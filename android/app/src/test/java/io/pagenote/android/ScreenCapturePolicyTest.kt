package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCapturePolicyTest {

  @Test
  fun keepsSmallFramesUnscaled() {
    assertEquals(ScreenCapturePolicy.ScaledSize(1080, 800), ScreenCapturePolicy.scaleToFrameLimit(1080, 800))
    assertEquals(ScreenCapturePolicy.ScaledSize(2048, 2048), ScreenCapturePolicy.scaleToFrameLimit(2048, 2048))
  }

  @Test
  fun scalesLongEdgeDownPreservingAspect() {
    val landscape = ScreenCapturePolicy.scaleToFrameLimit(3840, 2160)
    assertEquals(2048, landscape.width)
    assertEquals(1152, landscape.height)
    val portrait = ScreenCapturePolicy.scaleToFrameLimit(2160, 3840)
    assertEquals(1152, portrait.width)
    assertEquals(2048, portrait.height)
    // 长边恰为上限；宽高比保持。
    assertTrue(landscape.width * 2160 == landscape.height * 3840)
  }

  @Test
  fun rejectsInvalidFrameSizes() {
    assertThrows(IllegalArgumentException::class.java) { ScreenCapturePolicy.scaleToFrameLimit(0, 100) }
    assertThrows(IllegalArgumentException::class.java) { ScreenCapturePolicy.scaleToFrameLimit(100, -1) }
  }
}

class ColorPickingTest {

  @Test
  fun hexFormatsArgb() {
    assertEquals("#FF0000FF", ColorPicking.hex(0xFF0000FF.toInt()))
    assertEquals("#FFFFFFFF", ColorPicking.hex(0xFFFFFFFF.toInt()))
  }

  @Test
  fun opaqueForcesFullAlpha() {
    assertEquals(0xFF336699L, ColorPicking.opaque(0x11336699).toLong() and 0xFFFFFFFFL)
  }

  @Test
  fun nearestNamedColorMatchesPrimaryHues() {
    assertEquals("red", ColorPicking.nearestNamedColor(0xFFEE1111.toInt()).nameEn)
    assertEquals("blue", ColorPicking.nearestNamedColor(0xFF1111EE.toInt()).nameEn)
    assertEquals("black", ColorPicking.nearestNamedColor(0xFF0A0A0A.toInt()).nameEn)
    assertEquals("white", ColorPicking.nearestNamedColor(0xFFF7F7F7.toInt()).nameEn)
  }

  @Test
  fun pickReturnsHexAndNearest() {
    val picked = ColorPicking.pick(0x80FF0000.toInt())
    assertEquals("#FFFF0000", picked.hex)
    assertEquals("red", picked.nearest.nameEn)
  }

  @Test
  fun pixelAtClampsNormalizedCoordinates() {
    assertEquals(0 to 0, ColorPicking.pixelAt(-0.5f, -0.5f, 100, 50))
    assertEquals(99 to 49, ColorPicking.pixelAt(1.5f, 2f, 100, 50))
    // roundToInt 半值上取：0.5*99=49.5→50，0.5*49=24.5→25。
    assertEquals(50 to 25, ColorPicking.pixelAt(0.5f, 0.5f, 100, 50))
    assertThrows(IllegalArgumentException::class.java) { ColorPicking.pixelAt(0f, 0f, 0, 10) }
  }

  @Test
  fun floatingEntryBaselineNeverLaunches() {
    val entry = NoFloatingCaptureEntry
    assertTrue(!entry.isAvailable())
    assertTrue(!entry.requestLaunch())
  }
}
