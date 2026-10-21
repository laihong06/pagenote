package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormulaInsertionTest {
  @Test
  fun wrapsSelectedLatexInTheDesktopBlockTemplateAndSelectsTheFormulaBody() {
    val markdown = "before x^2 after"
    val edit = MarkdownFormulaInsertion.createEdit(
      markdown = markdown,
      selectionStart = 7,
      selectionEnd = 10,
    )
    val opening = "\n\n$$\n"
    val formula = "x^2"
    val closing = "\n$$\n\n"

    assertEquals(7, edit.replaceStart)
    assertEquals(10, edit.replaceEnd)
    assertEquals(opening + formula + closing, edit.replacement)
    assertEquals(7 + opening.length, edit.selectionStart)
    assertEquals(7 + opening.length + formula.length, edit.selectionEnd)
    assertEquals(
      "before " + opening + formula + closing + " after",
      markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement),
    )
  }

  @Test
  fun insertsAndSelectsTheDesktopPlaceholderForCollapsedSelection() {
    val markdown = "before after"
    val edit = MarkdownFormulaInsertion.createEdit(
      markdown = markdown,
      selectionStart = 7,
      selectionEnd = 7,
    )
    val opening = "\n\n$$\n"
    val formula = "E=mc^2"
    val closing = "\n$$\n\n"

    assertEquals(7, edit.replaceStart)
    assertEquals(7, edit.replaceEnd)
    assertEquals(opening + formula + closing, edit.replacement)
    assertEquals(7 + opening.length, edit.selectionStart)
    assertEquals(7 + opening.length + formula.length, edit.selectionEnd)
    assertEquals("before " + opening + formula + closing + "after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun preservesMultilineAndSupplementaryCharactersUsingUtf16Offsets() {
    val formula = "\\sum_{i=1}^{n} 😀\n+ x_i"
    val markdown = "intro\n$formula\nsummary"
    val start = markdown.indexOf(formula)
    val end = start + formula.length
    val edit = MarkdownFormulaInsertion.createEdit(
      markdown = markdown,
      selectionStart = start,
      selectionEnd = end,
    )
    val opening = "\n\n$$\n"
    val closing = "\n$$\n\n"

    assertEquals(start, edit.replaceStart)
    assertEquals(end, edit.replaceEnd)
    assertEquals(opening + formula + closing, edit.replacement)
    assertEquals(start + opening.length, edit.selectionStart)
    assertEquals(start + opening.length + formula.length, edit.selectionEnd)
    assertEquals("intro\n$opening$formula$closing\nsummary", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun normalizesReversedAndClampsSelectionToMarkdownBounds() {
    val markdown = "first\nsecond"
    val edit = MarkdownFormulaInsertion.createEdit(
      markdown = markdown,
      selectionStart = 100,
      selectionEnd = -5,
    )
    val opening = "\n\n$$\n"
    val closing = "\n$$\n\n"

    assertEquals(0, edit.replaceStart)
    assertEquals(markdown.length, edit.replaceEnd)
    assertEquals(opening + markdown + closing, edit.replacement)
    assertEquals(opening.length, edit.selectionStart)
    assertEquals(opening.length + markdown.length, edit.selectionEnd)
  }

  @Test
  fun insertsPlaceholderIntoAnEmptyDocument() {
    val edit = MarkdownFormulaInsertion.createEdit(
      markdown = "",
      selectionStart = 0,
      selectionEnd = 0,
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(0, edit.replaceEnd)
    assertEquals("\n\n$$\nE=mc^2\n$$\n\n", edit.replacement)
    assertEquals(5, edit.selectionStart)
    assertEquals(11, edit.selectionEnd)
  }
}
