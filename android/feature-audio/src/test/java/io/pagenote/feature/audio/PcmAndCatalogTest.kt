package io.pagenote.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmAndCatalogTest {
  // ---- PcmAudio ----

  @Test
  fun durationDerivedFromSamplesAndRate() {
    assertEquals(1_000L, PcmAudio(FloatArray(16_000), 16_000).durationMs)
    assertEquals(500L, PcmAudio(FloatArray(8_000), 16_000).durationMs)
    assertEquals(1_000L, PcmAudio(FloatArray(48_000), 48_000).durationMs)
  }

  @Test
  fun windowsSplitWithAbsoluteStartOffsets() {
    val pcm = PcmAudio(FloatArray(16_000 * 2), 16_000)
    val windows = pcm.windows(100)
    assertEquals(20, windows.size)
    assertEquals(0L, windows.first().startMs)
    assertEquals(1_900L, windows.last().startMs)
    assertEquals(1_600, windows.first().samples.size)
    assertEquals(1_600, windows.last().samples.size)
  }

  @Test
  fun sliceClampsToRangeAndAllowsEmpty() {
    val pcm = PcmAudio(FloatArray(16_000) { 0.5f }, 16_000)
    assertEquals(4_000, pcm.slice(0, 250).samples.size)
    assertEquals(0, pcm.slice(2_000, 3_000).samples.size)
    assertEquals(16_000, pcm.slice(-100, 99_999).samples.size)
  }

  @Test
  fun resampleKeepsRateAndConverts() {
    val same = PcmAudio.resampleToTarget(FloatArray(1_600), 16_000)
    assertEquals(1_600, same.samples.size)
    assertEquals(16_000, same.sampleRate)

    val down = PcmAudio.resampleToTarget(FloatArray(48_000), 48_000)
    assertEquals(16_000, down.samples.size)
    assertEquals(16_000, down.sampleRate)

    val up = PcmAudio.resampleToTarget(FloatArray(8_000), 8_000)
    assertEquals(16_000, up.samples.size)

    assertTrue(PcmAudio.resampleToTarget(FloatArray(0), 16_000).samples.isEmpty())
  }

  @Test
  fun resampleLinearInterpolatesMidpoints() {
    // 2 samples @ 8000Hz → 4 samples @ 16000Hz，线性插值中点取均值。
    val output = PcmAudio.resampleToTarget(floatArrayOf(0f, 1f), 8_000).samples
    assertEquals(4, output.size)
    assertEquals(0f, output[0])
    assertEquals(0.5f, output[1])
    assertEquals(1f, output[2])
    assertEquals(1f, output[3])
  }

  @Test(expected = IllegalArgumentException::class)
  fun pcmRejectsBadRate() {
    PcmAudio(FloatArray(10), 0)
  }

  // ---- AsrLanguage ----

  @Test
  fun languageResolutionFollowsLocaleForSystem() {
    assertEquals("zh", AsrLanguage.resolve("system", "zh-CN"))
    assertEquals("en", AsrLanguage.resolve("system", "en-US"))
    assertEquals("en", AsrLanguage.resolve("system", "fr-FR"))
    assertEquals("zh", AsrLanguage.resolve("zh", "en-US"))
    assertEquals("en", AsrLanguage.resolve("en", "zh-CN"))
    // 未知设置值按 system 处理（回落不失败）。
    assertEquals("zh", AsrLanguage.resolve("ko", "zh-TW"))
  }

  // ---- AsrModelCatalog ----

  @Test
  fun catalogEntriesAreUniqueAndValid() {
    assertTrue(AsrModelCatalog.ENTRIES.size >= 3)
    assertEquals(AsrModelCatalog.ENTRIES.size, AsrModelCatalog.ENTRIES.map { it.id }.toSet().size)
    AsrModelCatalog.ENTRIES.forEach { entry ->
      assertTrue(entry.languages.contains(AsrLanguage.CHINESE) || entry.languages.contains(AsrLanguage.ENGLISH))
      // VAD 组件是每个捆绑的必需件。
      assertTrue(entry.vad.files.isNotEmpty())
    }
    assertTrue(AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.streaming != null)
    assertTrue(AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.offline != null)
    assertTrue(AsrModelCatalog.ZIPFORMER_STREAMING_ZH_EN.offline == null)
    assertTrue(AsrModelCatalog.PARAFORMER_ZH.streaming == null)
  }

  @Test
  fun catalogResolveFallsBackToDefault() {
    assertEquals(AsrModelCatalog.DEFAULT_ID, AsrModelCatalog.resolve("no-such-model").id)
    assertTrue(AsrModelCatalog.isValidId(AsrModelCatalog.DEFAULT_ID))
    assertEquals(false, AsrModelCatalog.isValidId("no-such-model"))
  }

  @Test
  fun catalogResolveForLanguagePrefersLanguageCapableEntry() {
    // zh/en 都有默认捆绑承载；未知语言回落默认。
    assertEquals(
      AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.id,
      AsrModelCatalog.resolveForLanguage("zh").id,
    )
    assertEquals(
      AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.id,
      AsrModelCatalog.resolveForLanguage("en").id,
    )
    assertEquals(
      AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.id,
      AsrModelCatalog.resolveForLanguage("system").id,
    )
    assertEquals(
      AsrModelCatalog.ZIPFORMER_PARAFORMER_ZH_EN.id,
      AsrModelCatalog.resolveForLanguage("ko").id,
    )
  }
}

class AudioTranscriptFormatterTest {
  private val segments = listOf(
    TranscriptSegment(0, 3_200, "今天我们讲"),
    TranscriptSegment(3_200, 7_800, "  函数的积分。 "),
    TranscriptSegment(61_500, 63_000, "下一节预告"),
  )

  @Test
  fun transcriptTextJoinsTrimmedSegments() {
    assertEquals("今天我们讲\n函数的积分。\n下一节预告", AudioTranscriptFormatter.transcriptText(segments))
  }

  @Test
  fun timedLinesUseClockLabels() {
    assertEquals(
      listOf("[00:00] 今天我们讲", "[00:03] 函数的积分。", "[01:01] 下一节预告"),
      AudioTranscriptFormatter.timedLines(segments),
    )
    assertEquals(
      "[00:00] 今天我们讲\n[00:03] 函数的积分。",
      AudioTranscriptFormatter.timedTranscript(segments.take(2)),
    )
  }

  @Test
  fun previewTruncatesByCodePoints() {
    assertEquals("", AudioTranscriptFormatter.preview("   "))
    assertEquals("短句", AudioTranscriptFormatter.preview(" 短句 "))
    val long = "字".repeat(100)
    val preview = AudioTranscriptFormatter.preview(long, maxCodePoints = 10)
    assertEquals("字".repeat(10) + "…", preview)
  }

  @Test
  fun metaLabelCombinesLanguageAndModel() {
    assertEquals("zh · m1", AudioTranscriptFormatter.metaLabel("zh", "m1"))
    assertEquals(null, AudioTranscriptFormatter.metaLabel(null, "m1"))
    assertEquals(null, AudioTranscriptFormatter.metaLabel("ko", "m1"))
  }
}
