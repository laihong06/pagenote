package io.pagenote.feature.audio

import java.io.File

/**
 * 离线转写编排（移植批次 8）：PCM → 语音段 → 文本。路由规则：
 *
 * 1. **短音频回退**：时长 ≤ [shortClipMaxMs]（缺省 30s，与离线 Paraformer
 *    类模型的最优区间一致）直接走 [ShortClipRecognizer] 一次性识别，
 *    跳过 VAD 与流式会话；
 * 2. **长音频**：[VadSegmenter] 按静音分段 → 每段一个流式会话
 *    （喂入→inputFinished→收文），段级时间戳（绝对毫秒）来自 VAD 跨度；
 * 3. **降级**：捆绑不含流式组件 → 长音频分段后逐段走离线识别；VAD 未检出
 *    语音 → 整体按短音频回退路径再试一次（防 VAD 漏检）；
 * 4. **失败折叠**：引擎缺失/异常折叠为 [AsrTranscriptResult.status=FAILED]
 *    的结果（不抛出，对齐批次 6 OCR 引擎姿态），失败原因进 [warnings]。
 *
 * 语言/模型解析在构造时完成：语言按 [AsrLanguage.resolve]（system 跟随
 * locale），模型按 [AsrModelCatalog.resolve]；所选捆绑不支持目标语言时
 * 回落 [AsrModelCatalog.resolveForLanguage] 的承载条目。
 *
 * 输入 PCM 契约：16kHz 单声道 float（app 层解码 + 重采样后保证，见
 * [PcmAudio.resampleToTarget]）。
 */
class AsrTranscriber(
  private val binding: AsrRuntimeBinding,
  private val modelRoot: File,
  private val settingModelId: String,
  private val settingLanguage: String,
  private val systemLanguageTag: String,
  private val shortClipMaxMs: Long = DEFAULT_SHORT_CLIP_MAX_MS,
  private val feedWindowMs: Long = VadSegmenter.DEFAULT_WINDOW_MS,
) {
  data class AsrTranscriptResult(
    val status: AudioTranscriptStatus,
    val text: String?,
    val segments: List<TranscriptSegment>,
    val language: String,
    val modelId: String,
    val warnings: List<String> = emptyList(),
  )

  private val language: String = AsrLanguage.resolve(settingLanguage, systemLanguageTag)

  private val model: AsrModelEntry = run {
    val chosen = AsrModelCatalog.resolve(settingModelId)
    if (language in chosen.languages) chosen else AsrModelCatalog.resolveForLanguage(settingLanguage)
  }

  /**
   * 转写整段录音。时长以调用方传入的 [durationMs] 为准（与录音清单一致），
   * 缺省用 PCM 时长兜底；短音频路由据此判定。
   */
  fun transcribe(pcm: PcmAudio, durationMs: Long = pcm.durationMs): AsrTranscriptResult {
    val effectiveDuration = if (durationMs > 0) durationMs else pcm.durationMs
    val availability = runCatching { binding.checkAvailable(model, modelRoot) }
      .getOrElse { BindingAvailability(false, it.message) }
    if (!availability.available) {
      return failed(availability.reason ?: "ASR runtime is unavailable")
    }

    val segments: List<TranscriptSegment> = try {
      if (effectiveDuration <= shortClipMaxMs) {
        transcribeShortClip(pcm)
      } else {
        transcribeLongAudio(pcm)
      }
    } catch (error: Exception) {
      return failed(error.message ?: error.javaClass.simpleName)
    }

    val text = AudioTranscriptFormatter.transcriptText(segments)
    return AsrTranscriptResult(
      status = AudioTranscriptStatus.OK,
      text = text,
      segments = segments,
      language = language,
      modelId = model.id,
      warnings = emptyList(),
    )
  }

  /** 短音频回退路径：优先离线组件；捆绑不含离线组件时走流式整段。 */
  private fun transcribeShortClip(pcm: PcmAudio): List<TranscriptSegment> {
    if (model.offline != null) {
      val recognizer = binding.createShortClipRecognizer(model, modelRoot)
      try {
        val result = recognizer.recognize(pcm.samples)
        val text = result.text.trim()
        if (text.isEmpty()) return emptyList()
        val timestamps = result.tokenTimestampsMs
        if (timestamps.isNotEmpty()) {
          return lineSegmentsFromTokens(text, timestamps, pcm.durationMs)
        }
        return listOf(TranscriptSegment(0, pcm.durationMs.coerceAtLeast(1), text))
      } finally {
        runCatching { recognizer.close() }
      }
    }
    val text = recognizeWithStreaming(pcm.samples)
    return if (text.isEmpty()) {
      emptyList()
    } else {
      listOf(TranscriptSegment(0, pcm.durationMs.coerceAtLeast(1), text))
    }
  }

  /** 长音频：VAD 分段 → 每段一次识别；VAD 空回落短音频路径（防漏检）。 */
  private fun transcribeLongAudio(pcm: PcmAudio): List<TranscriptSegment> {
    val spans = segmentWithVad(pcm)
    if (spans.isEmpty()) {
      return transcribeShortClip(pcm)
    }
    val segments = mutableListOf<TranscriptSegment>()
    val offlineFallback = model.streaming == null && model.offline != null
    spans.forEach { span ->
      val clipped = pcm.slice(span.startMs, span.endMs)
      if (clipped.samples.isEmpty()) return@forEach
      val text = if (offlineFallback) {
        recognizeWithOffline(clipped.samples)
      } else {
        recognizeWithStreaming(clipped.samples)
      }
      if (text.isNotEmpty()) {
        segments.add(
          TranscriptSegment(
            startMs = span.startMs,
            endMs = maxOf(span.endMs, span.startMs + 1),
            text = text,
          ),
        )
      }
    }
    return segments
  }

  private fun segmentWithVad(pcm: PcmAudio): List<SpeechSpan> {
    val detector = binding.createVoiceActivityDetector(model, modelRoot)
    try {
      val segmenter = VadSegmenter(detector, sampleRate = pcm.sampleRate, windowMs = feedWindowMs)
      return segmenter.segment(pcm.samples).spans
    } finally {
      runCatching { detector.close() }
    }
  }

  private fun recognizeWithStreaming(samples: FloatArray): String {
    val engine = binding.createStreamingEngine(model, modelRoot)
    val session = engine.createSession()
    try {
      PcmAudio(samples, PcmAudio.TARGET_SAMPLE_RATE).windows(feedWindowMs).forEach { window ->
        session.acceptWaveform(window.samples)
        if (session.isReady) session.decode()
      }
      session.inputFinished()
      session.decode()
      return session.currentText.trim()
    } finally {
      runCatching { session.close() }
      runCatching { engine.close() }
    }
  }

  private fun recognizeWithOffline(samples: FloatArray): String {
    val recognizer = binding.createShortClipRecognizer(model, modelRoot)
    try {
      return recognizer.recognize(samples).text.trim()
    } finally {
      runCatching { recognizer.close() }
    }
  }

  /**
   * 词级时间戳 → 行级片段：按 [TOKEN_BUCKET_MS] 间隔聚桶（行首=桶首词
   * 时间戳）。词数与时间戳数不匹配（分词近似失败）时退化为单段。
   */
  private fun lineSegmentsFromTokens(
    text: String,
    timestampsMs: List<Long>,
    durationMs: Long,
  ): List<TranscriptSegment> {
    val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty() || tokens.size != timestampsMs.size) {
      return listOf(TranscriptSegment(0, durationMs.coerceAtLeast(1), text.trim()))
    }
    val segments = mutableListOf<TranscriptSegment>()
    var bucketStartMs = timestampsMs.first()
    val bucketWords = mutableListOf<String>()
    fun flush(endMs: Long) {
      val line = bucketWords.joinToString(" ").trim()
      if (line.isNotEmpty()) {
        segments.add(
          TranscriptSegment(
            startMs = bucketStartMs,
            endMs = maxOf(endMs, bucketStartMs + 1),
            text = line,
          ),
        )
      }
    }
    tokens.zip(timestampsMs).forEach { (token, timestamp) ->
      if (bucketWords.isNotEmpty() && timestamp - bucketStartMs >= TOKEN_BUCKET_MS) {
        flush(timestamp)
        bucketStartMs = timestamp
        bucketWords.clear()
      }
      bucketWords.add(token)
    }
    flush(durationMs.coerceAtLeast(bucketStartMs + 1))
    return segments
  }

  private fun failed(reason: String): AsrTranscriptResult = AsrTranscriptResult(
    status = AudioTranscriptStatus.FAILED,
    text = null,
    segments = emptyList(),
    language = language,
    modelId = model.id,
    warnings = listOf(reason),
  )

  companion object {
    const val DEFAULT_SHORT_CLIP_MAX_MS = 30_000L
    const val TOKEN_BUCKET_MS = 2_000L
  }
}
