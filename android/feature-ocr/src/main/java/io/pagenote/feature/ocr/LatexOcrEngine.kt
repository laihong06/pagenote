package io.pagenote.feature.ocr

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 归一化置信度 ∈ [0,1]；score 为束搜归一化对数分。 */
data class LatexOcrCandidate(
  val latex: String,
  val confidence: Double,
  val score: Double,
) {
  init {
    require(confidence in 0.0..1.0) { "bad latex candidate confidence: $confidence" }
    require(latex.length <= MAX_LATEX_LENGTH) { "latex candidate too long" }
  }

  companion object {
    const val MAX_LATEX_LENGTH = 2000
  }
}

/**
 * 公式识别结果。`status`：`ok`（有候选）/`failed`（不可用，详见 warnings）。
 * 与文本 [OcrResult] 不同，公式结果不落块坐标（束搜输出的是整段 LaTeX，
 * 无行块结构），候选列表本身就是可修正输入。
 */
data class LatexOcrResult(
  val engine: String,
  val status: String,
  val candidates: List<LatexOcrCandidate> = emptyList(),
  val warnings: List<String> = emptyList(),
) {
  fun bestLatex(): String? = candidates.firstOrNull()?.latex?.takeIf(String::isNotBlank)

  companion object {
    fun failed(engine: String, warning: String) = LatexOcrResult(
      engine = engine,
      status = "failed",
      warnings = listOf(warning),
    )
  }
}

/**
 * pix2tex 类公式识别引擎（移植批次 6）：ONNX encoder/decoder 两段式自回归
 * 推理 + 束搜索多候选。结构与 [OfflineOcrEngine] 对齐：
 *
 * - [create] 在 IO 线程装载词典与 ONNX 会话；资产缺失/装载失败抛
 *   [OfflineOcrException]（调用方把"模型未捆绑"作为可恢复状态处理）；
 * - [recognize] 永不因推理异常抛出——失败折叠为 `failed` 结果 + warning；
 * - 输入位图不被修改、不被回收（内部临时位图在预处理里回收）；
 * - [close] 幂等释放会话。
 *
 * 全量注入构造器（[withInference]）供 JVM 测试用确定性假推理覆盖
 * "位图→候选"整条路径。
 */
class LatexOcrEngine internal constructor(
  private val modelId: String,
  private val tokenizer: LatexTokenizer,
  private val encode: (Bitmap) -> LatexPreprocessor.ModelInput,
  private val runEncoder: suspend (LatexPreprocessor.ModelInput) -> EncoderMemory,
  private val runDecoder: suspend (Array<IntArray>, EncoderMemory) -> Array<FloatArray>,
  private val release: suspend () -> Unit,
  private val beamWidth: Int,
  private val maxTokens: Int,
) {
  private val mutex = Mutex()
  private var released = false

  suspend fun recognize(bitmap: Bitmap): LatexOcrResult = mutex.withLock {
    if (released) return@withLock LatexOcrResult.failed(modelId, "LaTeX OCR engine has been closed.")
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
      return@withLock LatexOcrResult.failed(modelId, "LaTeX OCR input image is empty or has been recycled.")
    }
    try {
      val input = encode(bitmap)
      val memory = runEncoder(input)
      val beams = LatexBeamDecoder.decode(
        tokenizer = tokenizer,
        stepLogits = { prefix -> runDecoder(arrayOf(prefix), memory).first() },
        beamWidth = beamWidth,
        maxTokens = maxTokens,
      )
      val candidates = beams.mapNotNull { beam ->
        val latex = tokenizer.decode(beam.tokenIds)
        if (latex.isBlank()) return@mapNotNull null
        LatexOcrCandidate(
          latex = latex.take(LatexOcrCandidate.MAX_LATEX_LENGTH),
          confidence = beam.confidence,
          score = beam.score,
        )
      }
      if (candidates.isEmpty()) {
        LatexOcrResult.failed(modelId, "No LaTeX candidate was produced.")
      } else {
        LatexOcrResult(engine = modelId, status = "ok", candidates = candidates)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      LatexOcrResult.failed(
        modelId,
        "LaTeX OCR inference failed: ${error.message ?: error.javaClass.simpleName}",
      )
    }
  }

  suspend fun close() = mutex.withLock {
    if (!released) {
      release()
      released = true
    }
  }

  /** 编码器输出（memory 张量的值与形状）。 */
  data class EncoderMemory(val values: FloatArray, val shape: LongArray) {
    override fun equals(other: Any?): Boolean =
      other is EncoderMemory && other.shape.contentEquals(shape) && other.values.contentEquals(values)

    override fun hashCode(): Int = shape.contentHashCode() * 31 + values.contentHashCode()
  }

  companion object {
    /** JVM 测试构造器：注入确定性编码/解码行为。 */
    internal fun withInference(
      modelId: String = "fixture",
      tokenizer: LatexTokenizer,
      encode: (Bitmap) -> LatexPreprocessor.ModelInput = { error("encode fixture not provided") },
      runEncoder: suspend (LatexPreprocessor.ModelInput) -> EncoderMemory,
      runDecoder: suspend (Array<IntArray>, EncoderMemory) -> Array<FloatArray>,
      beamWidth: Int = 4,
      maxTokens: Int = 64,
    ): LatexOcrEngine = LatexOcrEngine(
      modelId = modelId,
      tokenizer = tokenizer,
      encode = encode,
      runEncoder = runEncoder,
      runDecoder = runDecoder,
      release = {},
      beamWidth = beamWidth,
      maxTokens = maxTokens,
    )

    /**
     * 装载随包模型资产（IO 线程）；词典或任一 ONNX 缺失抛 [OfflineOcrException]
     * （消息带资产路径，调用方据此提示"公式模型未捆绑"）。
     */
    suspend fun create(
      context: Context,
      model: LatexModelEntry = LatexModelCatalog.DEFAULT,
    ): LatexOcrEngine = withContext(Dispatchers.IO) {
      val appContext = context.applicationContext
      val tokens = try {
        appContext.assets.open(model.tokensPath).use { stream ->
          LatexTokenizer.fromJsonText(stream.readBytes().toString(Charsets.UTF_8))
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        throw OfflineOcrException(
          "The LaTeX OCR model assets are missing (${model.tokensPath}); " +
            "bundle the pix2tex ONNX export to enable formula recognition.",
          error,
        )
      }

      val environment = OrtEnvironment.getEnvironment()
      val options = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
      }
      val encoderSession = try {
        openSession(appContext, environment, options, model.encoderPath)
      } catch (error: Throwable) {
        options.close()
        throw error
      }
      val decoderSession = try {
        openSession(appContext, environment, options, model.decoderPath)
      } catch (error: Throwable) {
        encoderSession.close()
        options.close()
        throw error
      }
      val decoderTokenInput = decoderSession.inputNames.firstOrNull()
        ?: throw OfflineOcrException("The LaTeX decoder model has no input: ${model.decoderPath}")
      val decoderMemoryInput = decoderSession.inputNames.toList().getOrNull(1)
        ?: throw OfflineOcrException("The LaTeX decoder model has no memory input: ${model.decoderPath}")
      val sessionMutex = Mutex()

      LatexOcrEngine(
        modelId = model.id,
        tokenizer = tokens,
        encode = LatexPreprocessor::toModelInput,
        runEncoder = { input ->
          sessionMutex.withLock {
            val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.values), input.shape)
            try {
              val output = encoderSession.run(mapOf(encoderSession.inputNames.first() to tensor))
              try {
                val value = output[encoderSession.outputNames.first()]
                  .orElseThrow { IllegalStateException("encoder produced no output") } as OnnxTensor
                EncoderMemory(copyOf(value.floatBuffer), value.info.shape)
              } finally {
                output.close()
              }
            } finally {
              tensor.close()
            }
          }
        },
        runDecoder = { prefixes, memory ->
          sessionMutex.withLock {
            val batchSize = prefixes.size.toLong()
            val length = prefixes.maxOf { it.size }.toLong()
            val flat = LongArray((batchSize * length).toInt()) { LatexTokenizer.PAD_ID.toLong() }
            prefixes.forEachIndexed { row, prefix ->
              prefix.forEachIndexed { column, id -> flat[row * length.toInt() + column] = id.toLong() }
            }
            val tokensTensor = OnnxTensor.createTensor(
              environment,
              LongBuffer.wrap(flat),
              longArrayOf(batchSize, length),
            )
            val memoryTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(memory.values), memory.shape)
            try {
              val output = decoderSession.run(
                mapOf(
                  decoderTokenInput to tokensTensor,
                  decoderMemoryInput to memoryTensor,
                ),
              )
              try {
                val value = output[decoderSession.outputNames.first()]
                  .orElseThrow { IllegalStateException("decoder produced no output") } as OnnxTensor
                copyLastPositionLogits(
                  buffer = value.floatBuffer,
                  shape = value.info.shape,
                  batchSize = batchSize.toInt(),
                  length = length.toInt(),
                  vocabulary = tokens.vocabularySize,
                )
              } finally {
                output.close()
              }
            } finally {
              tokensTensor.close()
              memoryTensor.close()
            }
          }
        },
        release = {
          withContext(Dispatchers.IO) {
            encoderSession.close()
            decoderSession.close()
            options.close()
          }
        },
        beamWidth = 4,
        maxTokens = 256,
      )
    }

    private fun openSession(
      context: Context,
      environment: OrtEnvironment,
      options: OrtSession.SessionOptions,
      assetPath: String,
    ): OrtSession = try {
      val bytes = context.assets.open(assetPath).use { it.readBytes() }
      environment.createSession(bytes, options)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      throw OfflineOcrException("Could not load the LaTeX OCR model ($assetPath): ${error.message}", error)
    }

    private fun copyOf(buffer: FloatBuffer): FloatArray {
      val duplicate = buffer.duplicate()
      duplicate.rewind()
      val out = FloatArray(duplicate.remaining())
      duplicate.get(out)
      return out
    }

    /** logits 形状 [B, L, V] → 每行取最后有效位置的 V 维向量。 */
    internal fun copyLastPositionLogits(
      buffer: FloatBuffer,
      shape: LongArray,
      batchSize: Int,
      length: Int,
      vocabulary: Int,
    ): Array<FloatArray> {
      val duplicate = buffer.duplicate()
      duplicate.rewind()
      val total = duplicate.remaining()
      val out = Array(batchSize) { FloatArray(vocabulary) }
      for (row in 0 until batchSize) {
        val offset = (row.toLong() * length + (length - 1)) * vocabulary
        if (offset >= total) continue
        duplicate.position(offset.toInt())
        duplicate.get(out[row], 0, vocabulary)
      }
      return out
    }
  }
}
