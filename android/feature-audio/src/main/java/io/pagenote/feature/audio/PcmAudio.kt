package io.pagenote.feature.audio

import kotlin.math.floor

/**
 * 解码后的 PCM 音频（移植批次 8）：单声道、float 归一化（-1..1）。app 层的
 * MediaExtractor/MediaCodec 解码产物装进本类交给 [AsrTranscriber]；窗口切分
 * 与重采样是纯 JVM 可测逻辑。
 */
class PcmAudio(
  val samples: FloatArray,
  val sampleRate: Int,
) {
  init {
    require(sampleRate > 0) { "bad sample rate: $sampleRate" }
    require(samples.size % CHANNELS_MONO == 0) { "samples must be mono-interleaved complete" }
  }

  val durationMs: Long
    get() = samples.size * 1000L / sampleRate

  fun isValid(): Boolean = samples.all { it.isFinite() }

  /**
   * 固定毫秒窗口切分（最后一段可短于窗口）。每个窗口携带相对录音开头的
   * 起始毫秒，供流式会话与 VAD 分段驱动使用。
   */
  fun windows(windowMs: Long): List<PcmWindow> {
    require(windowMs > 0) { "bad window ms: $windowMs" }
    val windowSize = (sampleRate * windowMs / 1000L).toInt().coerceAtLeast(1)
    val result = mutableListOf<PcmWindow>()
    var offset = 0
    while (offset < samples.size) {
      val end = minOf(offset + windowSize, samples.size)
      result.add(
        PcmWindow(
          samples = samples.copyOfRange(offset, end),
          startMs = offset * 1000L / sampleRate,
        ),
      )
      offset = end
    }
    return result
  }

  /** 毫秒区间切片（端点夹取到实际范围；空区间返回空样本）。 */
  fun slice(startMs: Long, endMs: Long): PcmAudio {
    val startIndex = (startMs.coerceAtLeast(0L) * sampleRate / 1000L).toInt()
      .coerceAtMost(samples.size)
    val endIndex = (endMs * sampleRate / 1000L).toInt().coerceAtMost(samples.size)
    if (endIndex <= startIndex) return PcmAudio(FloatArray(0), sampleRate)
    return PcmAudio(samples.copyOfRange(startIndex, endIndex), sampleRate)
  }

  data class PcmWindow(val samples: FloatArray, val startMs: Long)

  companion object {
    const val CHANNELS_MONO = 1
    const val TARGET_SAMPLE_RATE = 16_000

    /**
     * 线性插值重采样到目标采样率（单声道输入）。解码器产出的率与目标一致
     * 时原样返回（零拷贝）。引擎（sherpa-onnx 流式 Zipformer/Paraformer）
     * 期望 16kHz 单声道 float。
     */
    fun resampleToTarget(
      samples: FloatArray,
      inputSampleRate: Int,
      targetSampleRate: Int = TARGET_SAMPLE_RATE,
    ): PcmAudio {
      require(inputSampleRate > 0) { "bad input sample rate: $inputSampleRate" }
      require(targetSampleRate > 0) { "bad target sample rate: $targetSampleRate" }
      if (inputSampleRate == targetSampleRate) {
        return PcmAudio(samples, targetSampleRate)
      }
      if (samples.isEmpty()) return PcmAudio(FloatArray(0), targetSampleRate)
      val outputSize = floor(samples.size.toLong() * targetSampleRate.toDouble() / inputSampleRate).toInt()
      if (outputSize <= 0) return PcmAudio(FloatArray(0), targetSampleRate)
      val output = FloatArray(outputSize)
      val ratio = inputSampleRate.toDouble() / targetSampleRate.toDouble()
      for (index in 0 until outputSize) {
        val sourcePosition = index * ratio
        val left = floor(sourcePosition).toInt().coerceIn(0, samples.size - 1)
        val right = (left + 1).coerceAtMost(samples.size - 1)
        val fraction = sourcePosition - left
        output[index] = (samples[left] * (1.0 - fraction) + samples[right] * fraction).toFloat()
      }
      return PcmAudio(output, targetSampleRate)
    }
  }
}
