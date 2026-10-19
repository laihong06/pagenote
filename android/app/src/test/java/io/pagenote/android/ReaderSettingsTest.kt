package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ReaderSettingsTest {
  @Test
  fun defaultsPass() {
    ReaderSettingsPolicy.validate(ReaderSettings())
    assertEquals(3, ReaderSettings().prepareAheadPages)
    assertEquals("classic", ReaderSettings().markdownTheme)
  }

  @Test
  fun markdownThemeAcceptsOnlyClassicAndPaper() {
    listOf("classic", "paper").forEach { value ->
      ReaderSettingsPolicy.validate(ReaderSettings(markdownTheme = value))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ReaderSettingsPolicy.validate(ReaderSettings(markdownTheme = "sepia"))
    }
  }

  @Test
  fun preparationWindowAcceptsZeroThroughTenPages() {
    (0..10).forEach { count ->
      ReaderSettingsPolicy.validate(ReaderSettings(prepareAheadPages = count))
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun preparationWindowRejectsMoreThanTenPages() {
    ReaderSettingsPolicy.validate(ReaderSettings(prepareAheadPages = 11))
  }

  @Test(expected = IllegalArgumentException::class)
  fun badThemeRejected() {
    ReaderSettingsPolicy.validate(ReaderSettings(theme = "neon"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun badSplitRejected() {
    ReaderSettingsPolicy.validate(ReaderSettings(leftPercent = 5))
  }

  @Test
  fun noteFontSizeUsesDesktopOptionsAndDefaultsToFifteen() {
    assertEquals(listOf(12, 15, 18, 20, 24, 28, 32), ReaderSettingsPolicy.NOTE_FONT_SIZES)
    assertEquals(15, ReaderSettings().noteFontSize)
  }

  @Test(expected = IllegalArgumentException::class)
  fun unsupportedNoteFontSizeRejected() {
    ReaderSettingsPolicy.validate(ReaderSettings(noteFontSize = 17))
  }

  @Test
  fun everyDesktopNoteFontSizePassesValidation() {
    ReaderSettingsPolicy.NOTE_FONT_SIZES.forEach { size ->
      ReaderSettingsPolicy.validate(ReaderSettings(noteFontSize = size))
    }
  }

  @Test
  fun phonePortraitUsesStackedMode() {
    assertFalse(ReaderSettingsPolicy.useSideBySide(412, 915, false))
  }

  @Test
  fun tabletLandscapeUsesSideBySide() {
    assertTrue(ReaderSettingsPolicy.useSideBySide(1280, 800, true))
  }

  @Test
  fun dividerDragAdjustsPaneSplitRelativeToAvailableWidth() {
    assertEquals(52f, ReaderPaneSplitPolicy.percentAfterDrag(42f, 100f, 1_000f), 0.001f)
  }

  @Test
  fun dividerDragClampsPaneSplitToReadableBounds() {
    assertEquals(20f, ReaderPaneSplitPolicy.percentAfterDrag(42f, -500f, 1_000f), 0.001f)
    assertEquals(80f, ReaderPaneSplitPolicy.percentAfterDrag(42f, 500f, 1_000f), 0.001f)
    assertEquals(53, ReaderPaneSplitPolicy.persistedPercent(52.6f))
  }

  @Test(expected = IllegalArgumentException::class)
  fun dividerDragRejectsInvalidAvailableWidth() {
    ReaderPaneSplitPolicy.percentAfterDrag(42f, 10f, 0f)
  }
}
