package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSourcePagesTest {
  @Test
  fun splitsSourceByPagebreak() {
    val pages = MarkdownSourcePages.splitSource("# p1\n\n<!-- pagebreak -->\n\n# p2\n")

    assertEquals(2, pages.size)
    assertEquals("# p1", pages[0])
  }

  @Test
  fun pageCountTakesMaxOfSourceAndNotes() {
    val source = "# p1\n\n<!-- pagebreak -->\n\n# p2\n\n<!-- pagebreak -->\n\n# p3"

    assertEquals(3, MarkdownSourcePages.pageCount(source, "# n1"))
    assertEquals(2, MarkdownSourcePages.pageCount("# a", "# n1\n\n<!-- pagebreak -->\n\n# n2"))
  }

  @Test
  fun missingSourcePageReturnsEmptyPlaceholder() {
    assertEquals("", MarkdownSourcePages.pageAt("# only", 3))
  }

  @Test
  fun overlongSourcePagePreviewTruncates() {
    val (preview, truncated) = MarkdownSourcePages.previewPage("x".repeat(9000), 1, maxChars = 8000)

    assertTrue(truncated)
    assertEquals(8000, preview.length)
  }

  @Test
  fun recognizesMarkdownExtensions() {
    assertTrue(MarkdownSourcePages.isMarkdownExtension("md"))
    assertTrue(MarkdownSourcePages.isMarkdownExtension("markdown"))
    assertFalse(MarkdownSourcePages.isMarkdownExtension("pdf"))
  }
}
