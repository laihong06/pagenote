package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParagraphFormattingTest {
  @Test
  fun usesDesktopMarkdownPrefixesForEachParagraphCommand() {
    val cases = listOf(
      MarkdownParagraphPrefix.HEADING_1 to "# selected",
      MarkdownParagraphPrefix.HEADING_2 to "## selected",
      MarkdownParagraphPrefix.HEADING_3 to "### selected",
      MarkdownParagraphPrefix.UNORDERED_LIST to "- selected",
      MarkdownParagraphPrefix.ORDERED_LIST to "1. selected",
      MarkdownParagraphPrefix.TASK_LIST to "- [ ] selected",
      MarkdownParagraphPrefix.QUOTE to "> selected",
    )

    cases.forEach { (prefix, expected) ->
      val edit = MarkdownParagraphFormatting.prefixLines(
        markdown = "selected",
        selectionStart = 0,
        selectionEnd = 8,
        prefix = prefix,
      )

      assertEquals(prefix.name, 0, edit.replaceStart)
      assertEquals(prefix.name, 8, edit.replaceEnd)
      assertEquals(prefix.name, expected, edit.replacement)
      assertEquals(prefix.name, expected.length, edit.cursor)
      assertEquals(
        prefix.name,
        expected,
        "selected".replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement),
      )
    }
  }

  @Test
  fun prefixesEverySelectedTaskLineIncludingBlankLines() {
    val markdown = "before\nalpha\n\nbeta\nafter"
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = markdown,
      selectionStart = markdown.indexOf("alpha"),
      selectionEnd = markdown.indexOf("beta") + "beta".length,
      prefix = MarkdownParagraphPrefix.TASK_LIST,
    )

    assertEquals(7, edit.replaceStart)
    assertEquals(markdown.indexOf("\nafter"), edit.replaceEnd)
    assertEquals("- [ ] alpha\n- [ ] \n- [ ] beta", edit.replacement)
    assertEquals("before\n- [ ] alpha\n- [ ] \n- [ ] beta\nafter", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun expandsSelectionAcrossLinesAndPrefixesBlankLinesWithoutTouchingNeighbors() {
    val markdown = "before\nalpha\n\nbeta\nafter"
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = markdown,
      selectionStart = 9,
      selectionEnd = 16,
      prefix = MarkdownParagraphPrefix.HEADING_2,
    )

    assertEquals(7, edit.replaceStart)
    assertEquals(18, edit.replaceEnd)
    assertEquals("## alpha\n## \n## beta", edit.replacement)
    assertEquals(7 + edit.replacement.length, edit.cursor)
    assertEquals("before\n## alpha\n## \n## beta\nafter", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun includesTheLineWhenSelectionEndsAtItsStart() {
    val markdown = "one\ntwo\nthree"
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = markdown,
      selectionStart = 0,
      selectionEnd = 4,
      prefix = MarkdownParagraphPrefix.UNORDERED_LIST,
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(7, edit.replaceEnd)
    assertEquals("- one\n- two", edit.replacement)
    assertEquals("- one\n- two\nthree", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun collapsedSelectionPrefixesTheContainingLineAndPlacesCaretAtEnd() {
    val markdown = "before\nselected\nafter"
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = markdown,
      selectionStart = 10,
      selectionEnd = 10,
      prefix = MarkdownParagraphPrefix.QUOTE,
    )

    assertEquals(7, edit.replaceStart)
    assertEquals(15, edit.replaceEnd)
    assertEquals("> selected", edit.replacement)
    assertEquals(17, edit.cursor)
    assertEquals("before\n> selected\nafter", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun prefixesTheEmptyLineInAnEmptyDocument() {
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = "",
      selectionStart = 0,
      selectionEnd = 0,
      prefix = MarkdownParagraphPrefix.ORDERED_LIST,
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(0, edit.replaceEnd)
    assertEquals("1. ", edit.replacement)
    assertEquals(3, edit.cursor)
  }

  @Test
  fun normalizesReversedAndClampsOutOfRangeSelection() {
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = "one\ntwo",
      selectionStart = 99,
      selectionEnd = -10,
      prefix = MarkdownParagraphPrefix.QUOTE,
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(7, edit.replaceEnd)
    assertEquals("> one\n> two", edit.replacement)
    assertEquals(11, edit.cursor)
  }

  @Test
  fun usesUtf16OffsetsAfterSupplementaryCharacters() {
    val markdown = "a😀b\nnext"
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = markdown,
      selectionStart = 3,
      selectionEnd = 4,
      prefix = MarkdownParagraphPrefix.UNORDERED_LIST,
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(4, edit.replaceEnd)
    assertEquals("- a😀b", edit.replacement)
    assertEquals(6, edit.cursor)
    assertEquals("- a😀b\nnext", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun prefixesTheTrailingEmptyLineAfterFinalNewline() {
    val markdown = "line\n"
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = markdown,
      selectionStart = markdown.length,
      selectionEnd = markdown.length,
      prefix = MarkdownParagraphPrefix.HEADING_1,
    )

    assertEquals(markdown.length, edit.replaceStart)
    assertEquals(markdown.length, edit.replaceEnd)
    assertEquals("# ", edit.replacement)
    assertEquals(markdown.length + 2, edit.cursor)
  }
}
