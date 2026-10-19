package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableInsertionTest {
  @Test
  fun replacesSelectedTextWithTheExactDesktopTableAndPlacesCursorAfterIt() {
    val markdown = "before replace me after"
    val start = markdown.indexOf("replace me")
    val end = start + "replace me".length
    val edit = MarkdownTableInsertion.createEdit(markdown, start, end)
    val table = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"

    assertEquals(start, edit.replaceStart)
    assertEquals(end, edit.replaceEnd)
    assertEquals(table, edit.replacement)
    assertEquals(start + table.length, edit.cursor)
    assertEquals("before $table after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun insertsAtCollapsedSelectionAndReturnsAnUnselectedCursorAtTheEnd() {
    val markdown = "before after"
    val start = "before ".length
    val edit = MarkdownTableInsertion.createEdit(markdown, start, start)
    val table = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"

    assertEquals(start, edit.replaceStart)
    assertEquals(start, edit.replaceEnd)
    assertEquals(table, edit.replacement)
    assertEquals(start + table.length, edit.cursor)
    assertEquals("before $table" + "after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun normalizesReversedAndClampsSelectionToTheMarkdownBounds() {
    val markdown = "first 😀\nsecond"
    val edit = MarkdownTableInsertion.createEdit(markdown, 100, -5)
    val table = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"

    assertEquals(0, edit.replaceStart)
    assertEquals(markdown.length, edit.replaceEnd)
    assertEquals(table, edit.replacement)
    assertEquals(table.length, edit.cursor)
    assertEquals(table, markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun insertsIntoAnEmptyMarkdownDocument() {
    val edit = MarkdownTableInsertion.createEdit("", 0, 0)
    val table = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"

    assertEquals(0, edit.replaceStart)
    assertEquals(0, edit.replaceEnd)
    assertEquals(table, edit.replacement)
    assertEquals(table.length, edit.cursor)
  }
}
