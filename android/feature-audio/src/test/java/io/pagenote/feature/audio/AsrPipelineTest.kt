package io.pagenote.feature.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 引擎契约的注入式 fake：覆盖 VAD 分段、流式会话与短音频回退路由。 */
private class FakeVad(
  private val speechRanges: List<ClosedRange<Long>>,
  private val sampleRate: Int,
) : VoiceActivityDetector {
  private var fedSamples = 0L
  private var currentStartSample = -1L
  private var currentParts = mutableListOf<FloatArray>()
  private val queue = ArrayDeque<SpeechSegment>()
  var closed = false
    private set

  override fun acceptWaveform(samples: FloatArray) {
    val windowStartSample = fedSamples
    fedSamples += samples.size
    val startMs = windowStartSample * 1000L / sampleRate
    val endMs = fedSamples * 1000L / sampleRate
    val isSpeech = speechRanges.any { startMs < it.endInclusive && endMs > it.start }
    if (isSpeech) {
      if (currentStartSample < 0) currentStartSample = windowStartSample
      currentParts.add(samples.copyOf())
    } else if (currentStartSample >= 0) {
      flush()
    }
  }

  /** 当前窗口是否仍在语音中（未闭合的累积段存在）。 */
  override val isSpeechDetected: Boolean
    get() = currentStartSample >= 0

  override val empty: Boolean
    get() = queue.isEmpty()

  override fun front(): SpeechSegment? = queue.firstOrNull()

  override fun pop() {
    queue.removeFirstOrNull()
  }

  override fun flush() {
    if (currentStartSample >= 0) {
      val merged = currentParts.reduce { a, b -> a + b }
      queue.add(SpeechSegment(currentStartSample, merged))
      currentStartSample = -1L
      currentParts = mutableListOf()
    }
  }

  override fun clear() {
    queue.clear()
    currentStartSample = -1L
    currentParts = mutableListOf()
  }

  override fun close() {
    closed = true
  }
}

private class FakeStreamingSession(private val transcript: (FloatArray) -> String) : StreamingAsrSession {
  val accepted = mutableListOf<FloatArray>()
  private var text = ""
  private var finished = false
  var closed = false
    private set

  override val isReady: Boolean
    get() = accepted.isNotEmpty()

  override fun acceptWaveform(samples: FloatArray) {
    accepted.add(samples.copyOf())
  }

  override val isEndpoint: Boolean
    get() = false

  override fun decode() {
    if (finished) {
      text = transcript(accepted.reduce { a, b -> a + b })
    }
  }

  override fun reset() {}

  override fun inputFinished() {
    finished = true
  }

  override val currentText: String
    get() = text

  override fun close() {
    closed = true
  }
}

private class FakeStreamingEngine(
  private val transcript: (FloatArray) -> String,
  val onClosed: () -> Unit,
) : StreamingAsrEngine {
  override fun createSession(): StreamingAsrSession = FakeStreamingSession(transcript)

  override fun close() {
    onClosed()
  }
}

private class FakeShortClipRecognizer(
  private val result: (FloatArray) -> ShortClipResult,
) : ShortClipRecognizer {
  var closed = false
    private set

  override fun recognize(samples: FloatArray): ShortClipResult = result(samples)

  override fun close() {
    closed = true
  }
}

private class FakeBinding(
  private val availability: BindingAvailability = BindingAvailability(true),
  val vadFactory: () -> VoiceActivityDetector = { FakeVad(emptyList(), 16_000) },
  val streamingFactory: (() -> StreamingAsrEngine)? = null,
  val offlineFactory: (() -> ShortClipRecognizer)? = null,
) : AsrRuntimeBinding {
  override fun checkAvailable(model: AsrModelEntry, modelRoot: File): BindingAvailability = availability

  override fun createStreamingEngine(model: AsrModelEntry, modelRoot: File): StreamingAsrEngine =
    streamingFactory?.invoke() ?: throw AsrRuntimeException("no streaming engine")

  override fun createShortClipRecognizer(model: AsrModelEntry, modelRoot: File): ShortClipRecognizer =
    offlineFactory?.invoke() ?: throw AsrRuntimeException("no offline recognizer")

  override fun createVoiceActivityDetector(model: AsrModelEntry, modelRoot: File): VoiceActivityDetector =
    vadFactory()
}

class AsrPipelineTest {
  private val modelRoot = File("/tmp/pagenote-test-models")
  private val defaultModel = AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN

  private fun pcm(seconds: Double, value: Float = 0.01f): PcmAudio {
    val size = (16_000 * seconds).toInt()
    return PcmAudio(FloatArray(size) { value }, 16_000)
  }

  private fun transcriber(
    binding: AsrRuntimeBinding,
    shortClipMaxMs: Long = AsrTranscriber.DEFAULT_SHORT_CLIP_MAX_MS,
  ): AsrTranscriber = AsrTranscriber(
    binding = binding,
    modelRoot = modelRoot,
    settingModelId = defaultModel.id,
    settingLanguage = "zh",
    systemLanguageTag = "zh-CN",
    shortClipMaxMs = shortClipMaxMs,
  )

  @Test
  fun shortClipGoesToOfflineRecognizer() {
    var offlineCalls = 0
    var streamingCalls = 0
    val binding = FakeBinding(
      offlineFactory = {
        offlineCalls++
        FakeShortClipRecognizer { ShortClipResult("今天天气很好", emptyList()) }
      },
      streamingFactory = {
        streamingCalls++
        FakeStreamingEngine({ "should-not-run" }, {})
      },
    )
    val result = transcriber(binding).transcribe(pcm(5.0), durationMs = 5_000)
    assertEquals(AudioTranscriptStatus.OK, result.status)
    assertEquals("今天天气很好", result.text)
    assertEquals(1, offlineCalls)
    assertEquals(0, streamingCalls)
    assertEquals(1, result.segments.size)
    assertEquals(0L, result.segments[0].startMs)
    assertEquals(5_000L, result.segments[0].endMs)
    assertEquals("zh", result.language)
    assertEquals(defaultModel.id, result.modelId)
  }

  @Test
  fun shortClipFallsBackToStreamingWhenBundleHasNoOfflineComponent() {
    // zipformer-streaming-zh-en 捆绑 offline=null → 短音频走流式整段路径。
    var streamingCalls = 0
    var offlineCalls = 0
    val binding = FakeBinding(
      streamingFactory = {
        streamingCalls++
        FakeStreamingEngine({ "流式结果" }, {})
      },
      offlineFactory = {
        offlineCalls++
        FakeShortClipRecognizer { ShortClipResult("should-not-run", emptyList()) }
      },
    )
    val transcriber = AsrTranscriber(
      binding = binding,
      modelRoot = modelRoot,
      settingModelId = AsrModelCatalog.ZIPFORMER_STREAMING_ZH_EN.id,
      settingLanguage = "zh",
      systemLanguageTag = "zh-CN",
    )
    val result = transcriber.transcribe(pcm(5.0), durationMs = 5_000)
    assertEquals(AudioTranscriptStatus.OK, result.status)
    assertEquals("流式结果", result.text)
    assertEquals(AsrModelCatalog.ZIPFORMER_STREAMING_ZH_EN.id, result.modelId)
    assertEquals(0, offlineCalls)
    assertEquals(1, streamingCalls)
  }

  @Test
  fun longAudioSegmentsThroughVadAndStreaming() {
    val closedEngines = mutableListOf<Int>()
    val binding = FakeBinding(
      vadFactory = { FakeVad(listOf(0L..2_000L, 4_000L..6_000L), 16_000) },
      streamingFactory = {
        FakeStreamingEngine(
          transcript = { samples -> "seg(${samples.size})" },
          onClosed = { closedEngines.add(1) },
        )
      },
    )
    val result = transcriber(binding).transcribe(pcm(40.0), durationMs = 40_000)
    assertEquals(AudioTranscriptStatus.OK, result.status)
    assertEquals(2, result.segments.size)
    assertEquals(0L, result.segments[0].startMs)
    assertEquals(2_000L, result.segments[0].endMs)
    assertTrue(result.segments[1].startMs in 3_900..4_100)
    assertEquals("seg(32000)", result.segments[0].text)
    assertEquals(2, closedEngines.size) // 每段一个临时流式引擎，用毕即关
  }

  @Test
  fun longAudioWithEmptyVadFallsBackToShortClipPath() {
    var offlineCalls = 0
    val binding = FakeBinding(
      vadFactory = { FakeVad(emptyList(), 16_000) }, // VAD 全程非语音（漏检场景）
      offlineFactory = {
        offlineCalls++
        FakeShortClipRecognizer { ShortClipResult("整段兜底", emptyList()) }
      },
    )
    val result = transcriber(binding).transcribe(pcm(40.0), durationMs = 40_000)
    assertEquals(AudioTranscriptStatus.OK, result.status)
    assertEquals("整段兜底", result.text)
    assertEquals(1, offlineCalls)  }

  @Test
  fun missingRuntimeFoldsToFailedResult() {
    val result = transcriber(MissingAsrRuntimeBinding).transcribe(pcm(2.0))
    assertEquals(AudioTranscriptStatus.FAILED, result.status)
    assertTrue(result.text == null)
    assertTrue(result.warnings.single().contains("not bundled"))
  }

  @Test
  fun languageMismatchFallsBackToCatalogEntry() {
    // paraformer-zh 捆绑不支持 en → 回落默认 zh+en 捆绑。
    val transcriber = AsrTranscriber(
      binding = FakeBinding(offlineFactory = { FakeShortClipRecognizer { ShortClipResult("hi", emptyList()) } }),
      modelRoot = modelRoot,
      settingModelId = AsrModelCatalog.PARAFORMER_ZH.id,
      settingLanguage = "en",
      systemLanguageTag = "en-US",
    )
    val result = transcriber.transcribe(pcm(1.0))
    assertEquals(AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.id, result.modelId)
    assertEquals("en", result.language)
  }

  @Test
  fun engineExceptionFoldsToFailed() {
    val binding = FakeBinding(
      offlineFactory = {
        FakeShortClipRecognizer { throw IllegalStateException("model crashed") }
      },
    )
    val result = transcriber(binding).transcribe(pcm(1.0))
    assertEquals(AudioTranscriptStatus.FAILED, result.status)
    assertTrue(result.warnings.single().contains("model crashed"))
  }

  @Test
  fun emptySpeechYieldsOkWithEmptyTranscript() {
    val binding = FakeBinding(
      offlineFactory = { FakeShortClipRecognizer { ShortClipResult("  ", emptyList()) } },
    )
    val result = transcriber(binding).transcribe(pcm(1.0))
    assertEquals(AudioTranscriptStatus.OK, result.status)
    assertEquals("", result.text)
    assertTrue(result.segments.isEmpty())
  }

  @Test
  fun vadSegmenterConvertsSampleOffsetsToMillis() {
    val vad = FakeVad(listOf(0L..1_000L), 16_000)
    val segmenter = VadSegmenter(vad, sampleRate = 16_000, windowMs = 100)
    val result = segmenter.segment(FloatArray(16_000 * 2) { 0f })
    assertEquals(1, result.spans.size)
    assertEquals(0L, result.spans.first().startMs)
    assertEquals(1_000L, result.spans.first().endMs)
  }

  @Test
  fun vadSegmenterFlushesTrailingSpeech() {
    // 语音一直持续到结尾（无静音收尾）→ flush 兜底收割。
    val vad = FakeVad(listOf(500L..2_000L), 16_000)
    val result = VadSegmenter(vad, sampleRate = 16_000).segment(FloatArray(16_000 * 2) { 0f })
    assertEquals(1, result.spans.size)
    assertEquals(500L, result.spans.first().startMs)
    assertEquals(2_000L, result.spans.first().endMs)
  }

  @Test
  fun vadSegmenterFoldsDetectorCrashIntoEmptyResult() {
    val vad = object : VoiceActivityDetector {
      override fun acceptWaveform(samples: FloatArray) = throw IllegalStateException("vad broke")
      override val isSpeechDetected: Boolean = false
      override val empty: Boolean = true
      override fun front(): SpeechSegment? = null
      override fun pop() {}
      override fun flush() {}
      override fun clear() {}
      override fun close() {}
    }
    val result = VadSegmenter(vad, sampleRate = 16_000).segment(FloatArray(16_000) { 0f })
    assertTrue(result.spans.isEmpty())
  }
}
