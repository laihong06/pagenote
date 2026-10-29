package io.pagenote.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchJumpPolicyTest {
  @Test
  fun pageIndexMapsOneBasedHitToZeroBasedReaderIndex() {
    assertEquals(0, SearchJumpPolicy.pageIndex(1, 10))
    assertEquals(9, SearchJumpPolicy.pageIndex(10, 10))
  }

  @Test
  fun pageIndexRejectsOutOfRangeHits() {
    assertNull(SearchJumpPolicy.pageIndex(0, 10))
    assertNull(SearchJumpPolicy.pageIndex(11, 10))
    assertNull(SearchJumpPolicy.pageIndex(1, 0))
  }

  @Test
  fun focusCenterMapsRectMidpointToNormalizedViewportCenter() {
    val (x, y) = SearchJumpPolicy.focusCenter(SearchRect(200, 400, 400, 600))
    assertEquals(0.3f, x, 1e-6f)
    assertEquals(0.5f, y, 1e-6f)
  }

  @Test
  fun focusCenterClampsToUnitSquare() {
    val (x, y) = SearchJumpPolicy.focusCenter(SearchRect(0, 0, 1, 1))
    assertEquals(0.0005f, x, 1e-6f)
    assertEquals(0.0005f, y, 1e-6f)
    val (maxX, maxY) = SearchJumpPolicy.focusCenter(SearchRect(999, 999, 1000, 1000))
    assertEquals(0.9995f, maxX, 1e-6f)
    assertEquals(0.9995f, maxY, 1e-6f)
  }

  @Test(expected = IllegalArgumentException::class)
  fun emptyRectRejected() {
    SearchRect(100, 100, 100, 100)
  }

  @Test(expected = IllegalArgumentException::class)
  fun unnormalizedRectRejected() {
    SearchRect(-1, 0, 10, 10)
  }
}
