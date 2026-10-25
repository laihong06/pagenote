package io.pagenote.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetBuilderTest {
  @Test
  fun buildsWindowAroundFirstTermHit() {
    val content = (1..50).joinToString(" ") { "word$it" } + " needle " + (51..100).joinToString(" ") { "word$it" }
    val snippet = SnippetBuilder.build(content, "needle", maxLength = 40)
    assertTrue(snippet.contains("needle"))
    assertTrue(snippet.startsWith("…"))
    assertTrue(snippet.endsWith("…"))
    assertTrue(snippet.length <= SnippetBuilder.ELLIPSIS.length * 2 + 40)
  }

  @Test
  fun fallsBackToHeadWhenNoHit() {
    val snippet = SnippetBuilder.build("abcdef", "zzz", maxLength = 4)
    assertEquals("abcd…", snippet)
  }

  @Test
  fun hitAtHeadHasNoLeadingEllipsis() {
    val snippet = SnippetBuilder.build("needle at head", "needle", maxLength = 10)
    assertTrue(snippet.startsWith("needle"))
    assertTrue(snippet.endsWith("…"))
  }

  @Test
  fun whitespaceIsNormalized() {
    val snippet = SnippetBuilder.build("a\n\n  b\t c   target  d", "target", maxLength = 20)
    // 命中窗口从 "target" 前方 1/4 长度处展开，首字符 a 落在窗口外属预期。
    assertEquals("b c target d", snippet.trim(SnippetBuilder.ELLIPSIS.single()).trim())
  }

  @Test
  fun emptyQueryReturnsHeadTruncation() {
    assertEquals("abc…", SnippetBuilder.build("abcdef", "", maxLength = 3))
  }

  @Test
  fun blankContentReturnsEmptySnippet() {
    assertEquals("", SnippetBuilder.build("  \n ", "query"))
  }

  @Test
  fun matchIsCaseInsensitive() {
    val snippet = SnippetBuilder.build("Find The Needle here", "needle", maxLength = 20)
    assertTrue(snippet.contains("Needle"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun nonPositiveLengthRejected() {
    SnippetBuilder.build("content", "query", maxLength = 0)
  }
}
