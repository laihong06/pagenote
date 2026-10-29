package io.pagenote.android

import io.pagenote.feature.audio.AsrLanguage
import io.pagenote.feature.audio.AsrModelCatalog
import io.pagenote.feature.audio.AudioQuotaPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 批次 8 录音/转写设置（模型、语言、自动转写、配额）的值域与回落。 */
class ReaderAudioSettingsTest {
  @Test
  fun audioDefaultsPassValidation() {
    ReaderSettingsPolicy.validate(ReaderSettings())
    assertEquals(AsrModelCatalog.DEFAULT_ID, ReaderSettings().asrModel)
    assertEquals(AsrLanguage.SYSTEM, ReaderSettings().asrLanguage)
    assertEquals(true, ReaderSettings().asrAutoTranscribe)
    assertEquals(AudioQuotaPolicy.DEFAULT_MB, ReaderSettings().audioQuotaMb)
  }

  @Test
  fun asrModelMustComeFromTheCatalog() {
    AsrModelCatalog.ENTRIES.forEach { entry ->
      ReaderSettingsPolicy.validate(ReaderSettings(asrModel = entry.id))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ReaderSettingsPolicy.validate(ReaderSettings(asrModel = "closed-native"))
    }
    assertEquals(
      AsrModelCatalog.DEFAULT_ID,
      ReaderSettingsPolicy.sanitize(ReaderSettings(asrModel = "closed-native")).asrModel,
    )
  }

  @Test
  fun asrLanguageAcceptsKnownKeysAndRejectsOthers() {
    setOf("system", "zh", "en").forEach { key ->
      ReaderSettingsPolicy.validate(ReaderSettings(asrLanguage = key))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ReaderSettingsPolicy.validate(ReaderSettings(asrLanguage = "klingon"))
    }
    assertEquals(
      AsrLanguage.SYSTEM,
      ReaderSettingsPolicy.sanitize(ReaderSettings(asrLanguage = "klingon")).asrLanguage,
    )
  }

  @Test
  fun audioQuotaAcceptsRangeAndSanitizesOutliers() {
    ReaderSettingsPolicy.validate(ReaderSettings(audioQuotaMb = AudioQuotaPolicy.MIN_MB))
    ReaderSettingsPolicy.validate(ReaderSettings(audioQuotaMb = AudioQuotaPolicy.MAX_MB))
    assertThrows(IllegalArgumentException::class.java) {
      ReaderSettingsPolicy.validate(ReaderSettings(audioQuotaMb = 10))
    }
    assertEquals(
      AudioQuotaPolicy.DEFAULT_MB,
      ReaderSettingsPolicy.sanitize(ReaderSettings(audioQuotaMb = 10)).audioQuotaMb,
    )
    assertEquals(
      AudioQuotaPolicy.DEFAULT_MB,
      ReaderSettingsPolicy.sanitize(ReaderSettings(audioQuotaMb = 100_000)).audioQuotaMb,
    )
  }

  @Test
  fun autoTranscribeToggleIsFreeForm() {
    ReaderSettingsPolicy.validate(ReaderSettings(asrAutoTranscribe = false))
    ReaderSettingsPolicy.validate(ReaderSettings(asrAutoTranscribe = true))
  }

  @Test
  fun quotaUsageLabelFormatsBytes() {
    assertEquals("0 MB", AudioQuotaUsageLabel.formatMb(0))
    assertEquals("2 MB", AudioQuotaUsageLabel.formatMb(2L * 1024 * 1024))
    assertEquals("1.5 GB", AudioQuotaUsageLabel.formatMb((1024L * 1024 * 1024 * 1.5).toLong()))
  }
}
