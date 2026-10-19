package io.pagenote.core.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PageNotesTest {
  @Test
  fun splitKeepsEmptyPages() {
    val pages = PageNotes.split("# p1\n\n<!-- pagebreak -->\n\n# p2\n\n<!-- pagebreak -->\n\n")
    assertEquals(3, pages.size)
    assertEquals("# p1", pages[0])
    assertEquals("# p2", pages[1])
    assertEquals("", pages[2])
  }

  @Test
  fun joinSplitRoundTrip() {
    val pages = listOf("# p1", "", "# p3")
    assertEquals(pages, PageNotes.split(PageNotes.join(pages)))
  }

  @Test
  fun revisionChangesWithContent() {
    assertEquals(64, PageNotes.revision("hello").length)
    assertNotEquals(PageNotes.revision("a"), PageNotes.revision("b"))
  }
}
