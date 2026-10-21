package io.pagenote.android

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownThemePaletteTest {
  private val classic = MarkdownContentPalette(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF1B1B1B),
    secondary = Color(0xFF666666),
    heading = Color(0xFF245E59),
    rule = Color(0xFFDDDDDD),
    link = Color(0xFF245E59),
    codeBackground = Color(0xFFF2F2F2),
    codeText = Color(0xFF1B1B1B),
  )

  @Test
  fun classicRetainsTheApplicationPaletteExactly() {
    assertEquals(classic, markdownContentPalette("classic", isDark = false, classic))
    assertEquals(classic, markdownContentPalette("unknown", isDark = true, classic))
  }

  @Test
  fun paperUsesDistinctWarmPaletteWithReadableLightAndDarkContrast() {
    val light = markdownContentPalette("paper", isDark = false, classic)
    val dark = markdownContentPalette("paper", isDark = true, classic)

    assertFalse(light.background == classic.background)
    assertFalse(light.text == classic.text)
    assertTrue(light.background.red > light.text.red)
    assertTrue(dark.background.red < dark.text.red)
    assertFalse(light.background == dark.background)
    assertFalse(light.codeBackground == light.background)
    assertFalse(dark.codeBackground == dark.background)
  }
}
