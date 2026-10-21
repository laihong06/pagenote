package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownInlineFormattingTest {
  @Test
  fun wrapsSelectedRangeAndLeavesSurroundingMarkdownUntouched() {
    val markdown = "before selected after"
    val edit = MarkdownInlineFormatting.wrapSelection(
      markdown = markdown,
      selectionStart = 7,
      selectionEnd = 15,
      style = MarkdownInlineStyle.BOLD,
      placeholder = "文字",
    )

    assertEquals(7, edit.replaceStart)
    assertEquals(15, edit.replaceEnd)
    assertEquals("**selected**", edit.replacement)
    assertEquals(9, edit.selectionStart)
    assertEquals(17, edit.selectionEnd)
    assertEquals(
      "before **selected** after",
      markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement),
    )
  }

  @Test
  fun usesDesktopDelimitersForEachInlineStyle() {
    val styles = listOf(
      MarkdownInlineStyle.BOLD to "**selected**",
      MarkdownInlineStyle.ITALIC to "*selected*",
      MarkdownInlineStyle.STRIKETHROUGH to "~~selected~~",
      MarkdownInlineStyle.UNDERLINE to "<u>selected</u>",
    )

    styles.forEach { (style, expected) ->
      val edit = MarkdownInlineFormatting.wrapSelection(
        markdown = "selected",
        selectionStart = 0,
        selectionEnd = 8,
        style = style,
        placeholder = "文字",
      )
      assertEquals(expected, edit.replacement)
    }
  }

  @Test
  fun insertsAndSelectsPlaceholderForCollapsedSelection() {
    val markdown = "before after"
    val edit = MarkdownInlineFormatting.wrapSelection(
      markdown = markdown,
      selectionStart = 7,
      selectionEnd = 7,
      style = MarkdownInlineStyle.ITALIC,
      placeholder = "文字",
    )

    assertEquals(7, edit.replaceStart)
    assertEquals(7, edit.replaceEnd)
    assertEquals("*文字*", edit.replacement)
    assertEquals(8, edit.selectionStart)
    assertEquals(10, edit.selectionEnd)
    assertEquals("before *文字*after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun insertsAndSelectsUnderlinePlaceholderForCollapsedSelection() {
    val markdown = "before after"
    val edit = MarkdownInlineFormatting.wrapSelection(
      markdown = markdown,
      selectionStart = 7,
      selectionEnd = 7,
      style = MarkdownInlineStyle.UNDERLINE,
      placeholder = "文字",
    )

    assertEquals("<u>文字</u>", edit.replacement)
    assertEquals(10, edit.selectionStart)
    assertEquals(12, edit.selectionEnd)
    assertEquals("before <u>文字</u>after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun usesUtf16OffsetsAfterSupplementaryCharacters() {
    val markdown = "a😀b"
    val edit = MarkdownInlineFormatting.wrapSelection(
      markdown = markdown,
      selectionStart = 3,
      selectionEnd = 4,
      style = MarkdownInlineStyle.STRIKETHROUGH,
      placeholder = "文字",
    )

    assertEquals(3, edit.replaceStart)
    assertEquals(4, edit.replaceEnd)
    assertEquals("~~b~~", edit.replacement)
    assertEquals("a😀~~b~~", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun normalizesReversedAndClampsOutOfRangeSelection() {
    val edit = MarkdownInlineFormatting.wrapSelection(
      markdown = "abc",
      selectionStart = 9,
      selectionEnd = -4,
      style = MarkdownInlineStyle.STRIKETHROUGH,
      placeholder = "文字",
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(3, edit.replaceEnd)
    assertEquals("~~abc~~", edit.replacement)
    assertEquals(2, edit.selectionStart)
    assertEquals(5, edit.selectionEnd)
  }
}
