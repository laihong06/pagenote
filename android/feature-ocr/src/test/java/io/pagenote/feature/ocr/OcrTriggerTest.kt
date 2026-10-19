package io.pagenote.feature.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTriggerTest {
  @Test
  fun unavailableNativeTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = false, nativeText = "", hasBitmap = false))
  }

  @Test
  fun blankNativeTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "  \n ", hasBitmap = false))
  }

  @Test
  fun bitmapSparseTextTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "图1", hasBitmap = true))
  }

  @Test
  fun richNativeSkips() {
    assertFalse(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "x".repeat(50), hasBitmap = true))
    assertFalse(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "hello", hasBitmap = false))
  }

  @Test
  fun replacementCharacterTriggersWithoutBitmap() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "第一页�文字", hasBitmap = false))
  }

  @Test
  fun literalUtf8ReplacementMojibakeTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "\u00EF\u00BF\u00BD text", hasBitmap = false))
  }

  @Test
  fun suspiciousNonWhitespaceCharactersTriggerAtThreshold() {
    assertTrue(
      OcrTrigger.shouldRun(
        nativeAvailable = true,
        nativeText = "\uE000\uE001${"A".repeat(18)}",
        hasBitmap = false,
      ),
    )
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "A\u0001B\u0002C\u0003", hasBitmap = false))
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "\uD800abc\uDC00", hasBitmap = false))
  }

  @Test
  fun normalMultilingualTextAndSinglePrivateGlyphDoNotTrigger() {
    assertFalse(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "模式识别 with English", hasBitmap = false))
    assertFalse(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "正文\uE000以及后续说明", hasBitmap = false))
    assertFalse(
      OcrTrigger.shouldRun(
        nativeAvailable = true,
        nativeText = "\uE000\uE001${"A".repeat(19)}",
        hasBitmap = false,
      ),
    )
  }
}
