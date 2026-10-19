package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownCodeBlockInsertionTest {
  @Test
  fun wrapsSelectedSourceWithDesktopPythonFenceAndSelectsTheInnerCode() {
    val markdown = "before selected after"
    val edit = MarkdownCodeBlockInsertion.createEdit(
      markdown = markdown,
      selectionStart = 7,
      selectionEnd = 15,
      placeholder = "Code",
    )
    val opening = "\n```python\n"
    val code = "selected"
    val closing = "\n```\n"

    assertEquals(7, edit.replaceStart)
    assertEquals(15, edit.replaceEnd)
    assertEquals(opening + code + closing, edit.replacement)
    assertEquals(7 + opening.length, edit.selectionStart)
    assertEquals(7 + opening.length + code.length, edit.selectionEnd)
    assertEquals(
      "before " + opening + code + closing + " after",
      markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement),
    )
  }

  @Test
  fun insertsAndSelectsTheLocalizedPlaceholderForCollapsedSelection() {
    listOf("Code", "代码").forEach { placeholder ->
      val edit = MarkdownCodeBlockInsertion.createEdit(
        markdown = "before after",
        selectionStart = 7,
        selectionEnd = 7,
        placeholder = placeholder,
      )

      assertEquals(7, edit.replaceStart)
      assertEquals(7, edit.replaceEnd)
      assertEquals("\n```python\n$placeholder\n```\n", edit.replacement)
      assertEquals(18, edit.selectionStart)
      assertEquals(18 + placeholder.length, edit.selectionEnd)
      assertEquals("before \n```python\n$placeholder\n```\nafter", "before after".replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
    }
  }

  @Test
  fun preservesMultilineAndSupplementaryCharactersWithUtf16Selection() {
    val code = "println(\"😀\")\nnextLine()"
    val markdown = "intro\n$code\nsummary"
    val start = markdown.indexOf(code)
    val end = start + code.length
    val edit = MarkdownCodeBlockInsertion.createEdit(
      markdown = markdown,
      selectionStart = start,
      selectionEnd = end,
      placeholder = "Code",
    )
    val opening = "\n```python\n"
    val closing = "\n```\n"

    assertEquals(start, edit.replaceStart)
    assertEquals(end, edit.replaceEnd)
    assertEquals(opening + code + closing, edit.replacement)
    assertEquals(start + opening.length, edit.selectionStart)
    assertEquals(start + opening.length + code.length, edit.selectionEnd)
    assertEquals("intro\n$opening$code$closing\nsummary", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun normalizesReversedAndClampsSelectionToTheMarkdownBounds() {
    val markdown = "first\nsecond"
    val edit = MarkdownCodeBlockInsertion.createEdit(
      markdown = markdown,
      selectionStart = 100,
      selectionEnd = -5,
      placeholder = "Code",
    )
    val opening = "\n```python\n"
    val closing = "\n```\n"

    assertEquals(0, edit.replaceStart)
    assertEquals(markdown.length, edit.replaceEnd)
    assertEquals(opening + markdown + closing, edit.replacement)
    assertEquals(opening.length, edit.selectionStart)
    assertEquals(opening.length + markdown.length, edit.selectionEnd)
  }

  @Test
  fun insertsPlaceholderIntoAnEmptyDocument() {
    val edit = MarkdownCodeBlockInsertion.createEdit(
      markdown = "",
      selectionStart = 0,
      selectionEnd = 0,
      placeholder = "Code",
    )

    assertEquals(0, edit.replaceStart)
    assertEquals(0, edit.replaceEnd)
    assertEquals("\n```python\nCode\n```\n", edit.replacement)
    assertEquals(11, edit.selectionStart)
    assertEquals(15, edit.selectionEnd)
  }
}
