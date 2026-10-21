package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// Reader interaction enhancement defaults: vertical swipe threshold is covered by
// PdfPageSwipePolicy, so this file only pins ink/preparation defaults and the
// 0/boundary/out-of-range edges of PagePrefetchPolicy.window.
class ReaderInteractionDefaultsTest {
  @Test
  fun inkDefaultsToInsidePageOnly() {
    assertFalse(ReaderSettings().allowInkOutsidePage)
  }

  @Test
  fun prepareAheadDefaultsToThreePages() {
    assertEquals(3, ReaderSettings().prepareAheadPages)
  }

  @Test
  fun sanitizeFallsBackToDefaultPreparationWindowWhenOutOfRange() {
    assertEquals(3, ReaderSettingsPolicy.sanitize(ReaderSettings(prepareAheadPages = -1)).prepareAheadPages)
    assertEquals(3, ReaderSettingsPolicy.sanitize(ReaderSettings(prepareAheadPages = 11)).prepareAheadPages)
  }

  @Test
  fun prefetchWindowIsEmptyWhenWindowIsZero() {
    assertEquals(emptyList<Int>(), PagePrefetchPolicy.window(0, 5, 0))
  }

  @Test
  fun prefetchWindowClampsAtDocumentEnd() {
    assertEquals(listOf(4), PagePrefetchPolicy.window(4, 5, 3))
  }

  @Test(expected = IllegalArgumentException::class)
  fun prefetchWindowRejectsOutOfRangeWindow() {
    PagePrefetchPolicy.window(0, 5, 11)
  }
}
