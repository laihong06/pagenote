package io.pagenote.android

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 护眼/夜览滤镜策略与设置集成（移植批次 12 / 差距清单 #24，设置项 ⚙️）。 */
class NightFilterPolicyTest {

  @Test
  fun strengthValidateAcceptsBoundsAndRejectsOutside() {
    NightFilterPolicy.validateStrength(NightFilterPolicy.MIN_STRENGTH)
    NightFilterPolicy.validateStrength(NightFilterPolicy.MAX_STRENGTH)
    NightFilterPolicy.validateStrength(NightFilterPolicy.DEFAULT_STRENGTH)
    assertThrows(IllegalArgumentException::class.java) {
      NightFilterPolicy.validateStrength(NightFilterPolicy.MAX_STRENGTH + 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      NightFilterPolicy.validateStrength(-1)
    }
  }

  @Test
  fun sanitizeFallsBackToDefaultForOutOfRange() {
    assertEquals(NightFilterPolicy.DEFAULT_STRENGTH, NightFilterPolicy.sanitizeStrength(-1))
    assertEquals(NightFilterPolicy.DEFAULT_STRENGTH, NightFilterPolicy.sanitizeStrength(101))
    assertEquals(80, NightFilterPolicy.sanitizeStrength(80))
  }

  @Test
  fun overlayAlphaGrowsLinearlyAndClamps() {
    // 强度 0 → 全透明（UI 层直接跳过绘制）。
    assertEquals(0, Color.alpha(NightFilterPolicy.overlayArgb(0)))
    // 强度 100 → 最大不透明度 0.35。
    assertEquals(Math.round(NightFilterPolicy.MAX_ALPHA * 255), Color.alpha(NightFilterPolicy.overlayArgb(100)))
    // 线性中点：45 ≈ 0.35*45% *255 ≈ 40。
    assertEquals(
      Math.round(NightFilterPolicy.MAX_ALPHA * 45 / 100 * 255).toInt(),
      Color.alpha(NightFilterPolicy.overlayArgb(45)),
    )
  }

  @Test
  fun overlayKeepsWarmAmberBase() {
    val argb = NightFilterPolicy.overlayArgb(60)
    val rgb = argb and 0xFFFFFF
    assertEquals(NightFilterPolicy.BASE_RGB, rgb)
    // 红 > 绿 > 蓝：暖琥珀滤蓝光观感。
    assertTrueOrdering(Color.red(rgb), Color.green(rgb), Color.blue(rgb))
  }

  private fun assertTrueOrdering(red: Int, green: Int, blue: Int) {
    assert(red > green && green > blue) { "expected warm amber ordering, got r=$red g=$green b=$blue" }
  }

  // --- ReaderSettings 集成 ----------------------------------------------------

  @Test
  fun readerSettingsDefaultsHaveFilterDisabled() {
    val settings = ReaderSettings()
    assertEquals(false, settings.nightFilterEnabled)
    assertEquals(NightFilterPolicy.DEFAULT_STRENGTH, settings.nightFilterStrength)
    ReaderSettingsPolicy.validate(settings)
  }

  @Test
  fun readerSettingsValidateRejectsBadStrength() {
    assertThrows(IllegalArgumentException::class.java) {
      ReaderSettingsPolicy.validate(ReaderSettings(nightFilterStrength = 900))
    }
  }

  @Test
  fun readerSettingsSanitizeClampsStrengthButKeepsSwitch() {
    val sanitized = ReaderSettingsPolicy.sanitize(
      ReaderSettings(nightFilterEnabled = true, nightFilterStrength = 999),
    )
    assertEquals(true, sanitized.nightFilterEnabled)
    assertEquals(NightFilterPolicy.DEFAULT_STRENGTH, sanitized.nightFilterStrength)
  }
}
