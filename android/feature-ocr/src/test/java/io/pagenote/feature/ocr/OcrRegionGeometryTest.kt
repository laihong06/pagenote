package io.pagenote.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRegionGeometryTest {
  @Test
  fun convertsNormalizedBoundsWithFloorAndCeilRounding() {
    val bounds = NormalizedOcrRegion(101, 51, 503, 377).toPixelRect(997, 613)

    assertEquals(OcrPixelRect(100, 31, 502, 232), bounds)
  }

  @Test
  fun regionAtBitmapEdgesClipsToTheAvailablePixels() {
    val bounds = NormalizedOcrRegion(750, 0, 1000, 1000).toPixelRect(2, 1)

    assertEquals(OcrPixelRect(1, 0, 2, 1), bounds)
  }

  @Test
  fun rejectsInvalidEmptyAndOutsideRegions() {
    listOf(
      NormalizedOcrRegion(-1, 0, 10, 10),
      NormalizedOcrRegion(0, 0, 1001, 10),
      NormalizedOcrRegion(10, 0, 10, 10),
      NormalizedOcrRegion(1000, 0, 1000, 10),
      NormalizedOcrRegion(0, 1000, 10, 1000),
    ).forEach { region ->
      try {
        region.toPixelRect(100, 100)
        throw AssertionError("Expected invalid region to be rejected: $region")
      } catch (_: IllegalArgumentException) {
      }
    }
  }
}
