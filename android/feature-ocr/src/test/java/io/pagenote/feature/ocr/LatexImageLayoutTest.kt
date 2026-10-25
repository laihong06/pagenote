package io.pagenote.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexImageLayoutTest {
  @Test
  fun tallImageScalesToTargetHeightWithProportionalWidth() {
    val layout = LatexImageLayout.layout(sourceWidth = 96, sourceHeight = 384)
    assertEquals(192, layout.canvasHeight)
    assertEquals(672, layout.canvasWidth)
    assertEquals(192, layout.contentHeight)
    assertEquals(48, layout.contentWidth)
  }

  @Test
  fun wideImageCapsAtMaxWidthWithShrunkHeight() {
    val layout = LatexImageLayout.layout(sourceWidth = 1344, sourceHeight = 192)
    assertEquals(672, layout.contentWidth)
    assertEquals(96, layout.contentHeight)
    assertEquals(192, layout.canvasHeight)
  }

  @Test
  fun veryWideImageKeepsPositiveContentHeight() {
    val layout = LatexImageLayout.layout(sourceWidth = 20000, sourceHeight = 10)
    assertTrue(layout.contentHeight >= 1)
    assertTrue(layout.contentWidth >= 1)
  }

  @Test
  fun normalizeFollowsPix2texMeanStdWithoutInversion() {
    assertEquals((1.0 - 0.7931) / 0.1738, LatexImageLayout.normalize(255).toDouble(), 1e-4)
    assertEquals((0.0 - 0.7931) / 0.1738, LatexImageLayout.normalize(0).toDouble(), 1e-4)
    // 白纸（255）映射为正值、墨迹（0）映射为负值，与 pix2tex 约定一致。
    assertTrue(LatexImageLayout.normalize(255) > 0f)
    assertTrue(LatexImageLayout.normalize(0) < 0f)
  }

  @Test
  fun layoutRejectsNonPositiveSources() {
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      LatexImageLayout.layout(0, 100)
    }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      LatexImageLayout.layout(100, -1)
    }
  }
}
