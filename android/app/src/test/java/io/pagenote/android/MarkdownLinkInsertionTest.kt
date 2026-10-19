package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLinkInsertionTest {
  @Test
  fun wrapsSelectedTextWithTheDesktopLinkAndSelectsOnlyTheLabel() {
    val markdown = "before link after"
    val start = markdown.indexOf("link")
    val end = start + "link".length
    val edit = MarkdownLinkInsertion.createEdit(markdown, start, end, "Link text")
    val link = "[link](https://example.com)"

    assertEquals(start, edit.replaceStart)
    assertEquals(end, edit.replaceEnd)
    assertEquals(link, edit.replacement)
    assertEquals(start + 1, edit.selectionStart)
    assertEquals(start + 1 + "link".length, edit.selectionEnd)
    assertEquals("before $link after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun insertsAndSelectsLocalizedPlaceholderAtCollapsedSelection() {
    val markdown = "before after"
    val start = "before ".length
    val placeholder = "链接文字"
    val edit = MarkdownLinkInsertion.createEdit(markdown, start, start, placeholder)

    assertEquals(start, edit.replaceStart)
    assertEquals(start, edit.replaceEnd)
    assertEquals("[$placeholder](https://example.com)", edit.replacement)
    assertEquals(start + 1, edit.selectionStart)
    assertEquals(start + 1 + placeholder.length, edit.selectionEnd)
    assertEquals("before [$placeholder](https://example.com)after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun normalizesReversedUtf16SelectionAndKeepsSurroundingText() {
    val markdown = "😀 before link after"
    val start = markdown.indexOf("link")
    val end = start + "link".length
    val edit = MarkdownLinkInsertion.createEdit(markdown, end, start, "Link text")

    assertEquals(start, edit.replaceStart)
    assertEquals(end, edit.replaceEnd)
    assertEquals("[link](https://example.com)", edit.replacement)
    assertEquals(start + 1, edit.selectionStart)
    assertEquals(start + 1 + "link".length, edit.selectionEnd)
    assertEquals("😀 before [link](https://example.com) after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun clampsOutOfBoundsSelectionToTheMarkdownBounds() {
    val markdown = "first 😀\nlast"
    val edit = MarkdownLinkInsertion.createEdit(markdown, markdown.length + 8, -3, "Link text")

    assertEquals(0, edit.replaceStart)
    assertEquals(markdown.length, edit.replaceEnd)
    assertEquals("[$markdown](https://example.com)", edit.replacement)
    assertEquals(1, edit.selectionStart)
    assertEquals(1 + markdown.length, edit.selectionEnd)
  }
}
