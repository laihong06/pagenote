package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfPageSwipePolicyTest {
  @Test
  fun mapsVerticalSwipeToAdjacentPageDirection() {
    assertEquals(PdfPageSwipeDirection.NEXT, PdfPageSwipePolicy.direction(4f, -100f, density = 1f))
    assertEquals(PdfPageSwipeDirection.PREVIOUS, PdfPageSwipePolicy.direction(-4f, 100f, density = 1f))
  }

  @Test
  fun rejectsShortOrDiagonalGestures() {
    assertNull(PdfPageSwipePolicy.direction(0f, -71f, density = 1f))
    assertNull(PdfPageSwipePolicy.direction(80f, -100f, density = 1f))
    assertNull(PdfPageSwipePolicy.direction(0f, Float.NaN, density = 1f))
  }

  @Test
  fun scalesDistanceThresholdWithDisplayDensity() {
    assertNull(PdfPageSwipePolicy.direction(0f, -143f, density = 2f))
    assertEquals(PdfPageSwipeDirection.NEXT, PdfPageSwipePolicy.direction(0f, -144f, density = 2f))
  }

  @Test
  fun returnsNoTargetAtDocumentBoundaries() {
    assertNull(PdfPageSwipePolicy.targetPage(0, 3, PdfPageSwipeDirection.PREVIOUS))
    assertNull(PdfPageSwipePolicy.targetPage(2, 3, PdfPageSwipeDirection.NEXT))
    assertEquals(1, PdfPageSwipePolicy.targetPage(0, 3, PdfPageSwipeDirection.NEXT))
    assertEquals(1, PdfPageSwipePolicy.targetPage(2, 3, PdfPageSwipeDirection.PREVIOUS))
  }
}
