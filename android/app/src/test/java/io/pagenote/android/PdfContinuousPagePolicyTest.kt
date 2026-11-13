package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfContinuousPagePolicyTest {
  @Test
  fun activePageUsesThePageCenterNearestViewportCenter() {
    val visiblePages = listOf(
      PdfVisiblePage(pageIndex = 0, topPx = 0, heightPx = 600),
      PdfVisiblePage(pageIndex = 1, topPx = 620, heightPx = 300),
      PdfVisiblePage(pageIndex = 2, topPx = 930, heightPx = 900),
    )

    assertEquals(1, PdfContinuousPagePolicy.activePage(visiblePages, viewportCenterPx = 1_000))
  }

  @Test
  fun activePageReturnsNullWhenThereAreNoVisiblePages() {
    assertNull(PdfContinuousPagePolicy.activePage(emptyList(), viewportCenterPx = 300))
  }

  @Test
  fun activePageBreaksExactTiesTowardTheLowerPageIndex() {
    val visiblePages = listOf(
      PdfVisiblePage(pageIndex = 0, topPx = 0, heightPx = 200),
      PdfVisiblePage(pageIndex = 1, topPx = 200, heightPx = 200),
    )

    assertEquals(0, PdfContinuousPagePolicy.activePage(visiblePages, viewportCenterPx = 200))
  }
}
