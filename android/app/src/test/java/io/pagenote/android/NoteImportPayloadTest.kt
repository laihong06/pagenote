package io.pagenote.android

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class NoteImportPayloadTest {
  @Test
  fun parsesDesktopBackupAndFiltersPagesAgainstCurrentDocument() {
    val updates = NoteImportPayload.parseBackup(
      content = """{
        "version": 93,
        "document": "another project",
        "pages": 69,
        "notes": {
          "1": "第一页",
          "03": "第三页",
          "0": "zero",
          "4": "outside",
          "bad": "invalid",
          "2": 7,
          "5": null
        }
      }""",
      currentPageCount = 3,
    )

    assertEquals(mapOf(1 to "第一页", 3 to "第三页"), updates)
  }

  @Test
  fun mergesOnlyImportedPagesAndAllowsEmptyStringToClearOnePage() {
    val current = PageNotes.join(listOf("第一页旧稿", "邻页保留", "第三页旧稿"))

    val merged = NoteImportPayload.merge(
      currentNotes = current,
      currentPageCount = 3,
      importedPages = mapOf(1 to "第一页新稿", 3 to ""),
    )

    assertEquals(PageNotes.join(listOf("第一页新稿", "邻页保留", "")), merged)
  }

  @Test
  fun rejectsMalformedBackupOrMissingNotesObject() {
    assertThrows(NoteImportFormatException::class.java) {
      NoteImportPayload.parseBackup("{not-json", currentPageCount = 3)
    }
    assertThrows(NoteImportFormatException::class.java) {
      NoteImportPayload.parseBackup("{\"notes\": []}", currentPageCount = 3)
    }
  }

  @Test
  fun replacesSelectedUtf16RangeAndPlacesCursorAfterImportedText() {
    val edit = NoteImportPayload.insertText(
      markdown = "前缀内容尾部",
      selectionStart = 2,
      selectionEnd = 5,
      text = "**新**",
    )

    assertEquals(2, edit.replaceStart)
    assertEquals(5, edit.replaceEnd)
    assertEquals("**新**", edit.replacement)
    assertEquals(7, edit.cursor)
  }

  @Test
  fun fallsBackToPageEndForUnavailableSelectionAndDetectsJsonByNameOrMime() {
    val edit = NoteImportPayload.insertText(
      markdown = "现有内容",
      selectionStart = -1,
      selectionEnd = -1,
      text = "追加",
    )

    assertEquals(4, edit.replaceStart)
    assertEquals(4, edit.replaceEnd)
    assertEquals(6, edit.cursor)
    assertTrue(NoteImportPayload.isJson("backup.JSON", "text/plain"))
    assertTrue(NoteImportPayload.isJson("backup", "application/json"))
    assertFalse(NoteImportPayload.isJson("notes.md", "text/markdown"))
  }
}
