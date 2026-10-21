package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePageTest {
  @Test
  fun missingPageReturnsEmpty() {
    assertEquals("", NotePage.pageAt("# p1", 5))
  }

  @Test
  fun longPageTruncatesWithFlag() {
    val (text, hasMore) = NotePage.preview("a".repeat(9000), 8000)
    assertEquals(8000, text.length)
    assertTrue(hasMore)
  }

  @Test
  fun shortPageHasNoMore() {
    val (_, hasMore) = NotePage.preview("# short")
    assertFalse(hasMore)
  }
}
