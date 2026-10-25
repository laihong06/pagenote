package io.pagenote.feature.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRError
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineOcrException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OfflineOcrEngine private constructor(
  private val inference: suspend (Bitmap) -> OcrResult,
  private val releaseInference: suspend () -> Unit,
) {
  internal constructor(inference: suspend (Bitmap) -> OcrResult) : this(inference, {})

  private val mutex = Mutex()
  private var released = false

  suspend fun recognize(bitmap: Bitmap): OcrResult = mutex.withLock {
    if (released) return@withLock failure("OCR engine has been closed.")
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
      return@withLock failure("OCR input image is empty or has been recycled.")
    }

    try {
      recognizeBitmap(bitmap)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      failure("Offline OCR inference failed: ${error.message ?: error.javaClass.simpleName}")
    }
  }

  suspend fun recognize(bitmap: Bitmap, region: NormalizedOcrRegion): OcrResult = mutex.withLock {
    if (released) return@withLock failure("OCR engine has been closed.")
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
      return@withLock failure("OCR input image is empty or has been recycled.")
    }

    val crop = try {
      region.toPixelRect(bitmap.width, bitmap.height)
    } catch (error: IllegalArgumentException) {
      return@withLock failure("Invalid OCR region: ${error.message ?: "region is outside the image"}")
    }

    var temporaryCrop: Bitmap? = null
    try {
      val recognitionBitmap = if (crop.left == 0 && crop.top == 0 &&
        crop.right == bitmap.width && crop.bottom == bitmap.height
      ) {
        bitmap
      } else {
        Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height).also { created ->
          if (created !== bitmap) temporaryCrop = created
        }
      }
      val cropResult = recognizeBitmap(recognitionBitmap)
      if (recognitionBitmap === bitmap) cropResult else OcrResultMapper.mapRegion(
        cropResult = cropResult,
        crop = crop,
        pageWidth = bitmap.width,
        pageHeight = bitmap.height,
      )
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      failure("Offline OCR inference failed: ${error.message ?: error.javaClass.simpleName}")
    } finally {
      temporaryCrop?.let { if (!it.isRecycled) it.recycle() }
    }
  }

  suspend fun close() = mutex.withLock {
    if (!released) {
      releaseInference()
      released = true
    }
  }

  private fun failure(message: String) = OcrResult(
    engine = OcrResultMapper.ENGINE,
    status = "failed",
    warnings = listOf(message),
  )

  private suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult = inference(bitmap)

  companion object {
    private val openCvLock = Any()
    private var openCvInitialized = false

    /**
     * 装载 OCR 模型（移植批次 6 起模型由 [OcrModelCatalog] 解析：识别语言
     * → 承载模型；缺省仍是随包 PP-OCR 轻量模型，既有调用点行为不变）。
     */
    suspend fun create(context: Context, model: OcrModelEntry = OcrModelCatalog.DEFAULT): OfflineOcrEngine =
      withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val isOpenCvReady = synchronized(openCvLock) {
          if (!openCvInitialized) {
            openCvInitialized = OpenCVUtils.init(appContext)
          }
          openCvInitialized
        }
        if (!isOpenCvReady) {
          throw OfflineOcrException("OpenCV could not be loaded; offline OCR is unavailable.")
        }

        try {
          val pipeline = PaddleOCR.create(
            context = appContext,
            config = PaddleOCRConfig(recBatchSize = 1),
            engineConfig = EngineConfig(numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)),
            detModelAssetPath = model.detectionModelPath,
            recModelAssetPath = model.recognitionModelPath,
            recConfigAssetPath = model.recognitionConfigPath,
          )
          OfflineOcrEngine(
            inference = { bitmap -> recognizeWithPipeline(pipeline, bitmap) },
            releaseInference = pipeline::release,
          )
        } catch (error: OCRError.ModelNotFound) {
          throw OfflineOcrException("A bundled OCR model is missing: ${error.message}", error)
        } catch (error: OCRError.ConfigParseFailed) {
          throw OfflineOcrException("The bundled OCR recognition dictionary is invalid: ${error.message}", error)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Throwable) {
          throw OfflineOcrException("Offline OCR models could not be initialized: ${error.message}", error)
        }
      }

    private suspend fun recognizeWithPipeline(pipeline: PaddleOCR, bitmap: Bitmap): OcrResult {
      val output = pipeline.recognize(bitmap)
      return OcrResultMapper.map(
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
        recognized = output.results.map { result ->
          RecognizedTextBlock(
            text = result.text,
            confidence = result.confidence.toDouble(),
            points = result.box.points.map { OcrPoint(it.x, it.y) },
          )
        },
      )
    }
  }
}
