package io.pagenote.feature.audio

/**
 * VAD 分段驱动（移植批次 8）：把整段 PCM 按固定窗口喂给 [VoiceActivityDetector]，
 * 收割内部累积的语音段并换算到毫秒时间轴。检测器本身的静音参数（前/后
 * 静音窗）在 sherpa 配置里由绑定实现设置；本类只负责驱动与换算。
 */
class VadSegmenter(
  private val detector: VoiceActivityDetector,
  private val sampleRate: Int,
  private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
  /**
   * 分段结果。`spans` 为毫秒时间轴语音段；`fedSamples` 为总样本数（供
   * 空结果回退判定）。
   */
  data class Result(val spans: List<SpeechSpan>, val fedSamples: Long)

  /**
   * 全量喂入并收割。检测器异常折叠为空段结果（调用方回退整段识别），
   * 不向转写管线抛出。
   */
  fun segment(samples: FloatArray): Result {
    val fed = samples.size.toLong()
    return try {
      val spans = mutableListOf<SpeechSpan>()
      PcmAudio(samples, sampleRate).windows(windowMs).forEach { window ->
        detector.acceptWaveform(window.samples)
        if (!detector.isSpeechDetected) {
          while (!detector.empty) {
            detector.front()?.let { segment -> spans.add(toSpan(segment)) }
            detector.pop()
          }
        }
      }
      // 尾部冲刷：最后一段语音后无静音收尾时，显式闭合再收割。
      runCatching { detector.flush() }
      while (!detector.empty) {
        detector.front()?.let { segment -> spans.add(toSpan(segment)) }
        detector.pop()
      }
      Result(spans.sortedBy(SpeechSpan::startMs), fed)
    } catch (error: Exception) {
      Result(emptyList(), fed)
    } finally {
      runCatching { detector.clear() }
    }
  }

  private fun toSpan(segment: SpeechSegment): SpeechSpan {
    val startMs = segment.startSample * 1000L / sampleRate
    val endMs = (segment.startSample + segment.samples.size) * 1000L / sampleRate
    return SpeechSpan(startMs, endMs)
  }

  companion object {
    const val DEFAULT_WINDOW_MS = 100L
  }
}
