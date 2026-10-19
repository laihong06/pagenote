package io.pagenote.android

import io.pagenote.core.project.PageNotes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NoteExportPayloadTest {
  @Test
  fun fullBackupUsesDesktopV1JsonAndPreservesBlankPageAndDraftOverride() {
    val markdown = PageNotes.join(listOf("首页说明", "", "第三页旧稿"))

    val export = NoteExportPayload.fullBackup(
      projectName = "模式识别",
      sourcePageCount = 4,
      notesMarkdown = markdown,
      currentPage = 3,
      currentDraft = "第三页新稿：特征向量",
    )

    val root = Json.parseToJsonElement(export.content).jsonObject
    val notes = root.getValue("notes").jsonObject
    assertEquals(setOf("version", "document", "pages", "notes"), root.keys)
    assertEquals(1, root.getValue("version").jsonPrimitive.int)
    assertEquals("模式识别", root.getValue("document").jsonPrimitive.content)
    assertEquals(4, root.getValue("pages").jsonPrimitive.int)
    assertEquals(setOf("1", "2", "3"), notes.keys)
    assertEquals("首页说明", notes.getValue("1").jsonPrimitive.content)
    assertEquals("", notes.getValue("2").jsonPrimitive.content)
    assertEquals("第三页新稿：特征向量", notes.getValue("3").jsonPrimitive.content)
    assertEquals("application/json", export.mimeType)
    assertEquals("模式识别-逐页笔记.json", export.fileName)
  }

  @Test
  fun currentPageExportKeepsFullMarkdownAndUsesDesktopFilename() {
    val markdown = "段落\n" + "x".repeat(8_500)

    val export = NoteExportPayload.currentPage(
      projectName = "模式识别",
      page = 12,
      markdown = markdown,
    )

    assertEquals("text/markdown", export.mimeType)
    assertEquals("模式识别-第12页.md", export.fileName)
    assertEquals(markdown, export.content)
    assertFalse(export.content.isEmpty())
  }
}
