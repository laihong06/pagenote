package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SPEC window semantics from `android/docs/spec/features/page-prefetch.md`.
 *
 * Covers only the EARS window calculation via [PagePrefetchPolicy.window]:
 * the configured window includes the current page, zero disables preparation,
 * and the window clamps at document boundaries.
 */
class PagePrefetchWindowSemanticsTest {
  @Test
  fun settingThreeAtFirstPageRequestsFirstThreePagesInOrder() {
    assertEquals(listOf(0, 1, 2), PagePrefetchPolicy.window(0, 69, 3))
  }

  @Test
  fun movingToSecondPageReusesOverlapAndExtendsByOnePage() {
    val firstPageWindow = PagePrefetchPolicy.window(0, 69, 3)
    val secondPageWindow = PagePrefetchPolicy.window(1, 69, 3)

    assertEquals(listOf(0, 1, 2), firstPageWindow)
    assertEquals(listOf(1, 2, 3), secondPageWindow)
    assertEquals(listOf(1, 2), secondPageWindow.intersect(firstPageWindow.toSet()).sorted())
    assertEquals(listOf(3), secondPageWindow - firstPageWindow.toSet())
  }

  @Test
  fun settingZeroRequestsNoPages() {
    assertEquals(emptyList<Int>(), PagePrefetchPolicy.window(0, 69, 0))
    assertEquals(emptyList<Int>(), PagePrefetchPolicy.window(5, 69, 0))
  }

  @Test
  fun windowClampsAtDocumentEnd() {
    assertEquals(listOf(67, 68), PagePrefetchPolicy.window(67, 69, 3))
    assertEquals(listOf(68), PagePrefetchPolicy.window(68, 69, 3))
  }
}
