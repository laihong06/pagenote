package io.pagenote.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCommandChecklistTest {
  private val wiredCommands = setOf(
    "open_project",
    "open_document",
    "new_project",
    "recent_documents",
    "attachments",
    "import_notes",
    "export_notes",
    "note_history",
    "pdf_text_selection",
    "page_tutor",
    "agent_task",
    "annotations",
    "reviews",
    "handoff",
    "note_undo",
    "note_redo",
    "cut",
    "copy",
    "select_all",
    "font_size",
    "heading",
    "unordered_list",
    "ordered_list",
    "task_list",
    "quote",
    "bold",
    "italic",
    "strikethrough",
    "underline",
    "code",
    "formula",
    "table",
    "link",
    "image",
    "paste_image",
    "paste_markdown",
    "previous_page",
    "next_page",
    "ocr",
    "region_ocr",
    "zoom_out",
    "zoom_in",
    "fit_width",
    "fit_page",
    "select_tool",
    "pen",
    "highlighter",
    "eraser",
    "line",
    "arrow",
    "shape",
    "ink_text",
    "ink_color",
    "ink_width",
    "annotate_mode",
    "pen_undo",
    "pen_redo",
    "copy_ink",
    "paste_ink",
    "insert_ink_image",
    "clear_page",
    "delete_ink",
    "selection_to_annotation",
    "settings_action",
    "model_settings",
    "about",
    "shortcuts",
    "privacy_license",
    "diagnostics",
  )
  private val pendingCommands = setOf("office_edit", "save_return")

  @Test
  fun checklistMatchesWiredMenuCommands() {
    val main = File("src/main/java/io/pagenote/android/MainActivity.kt").readText()
    val wired = Regex("\"([a-z_0-9]+)\",\\s*R\\.string").findAll(main).map { it.groupValues[1] }.toSet()
    assertTrue("missing checklist commands: ${wiredCommands - wired}", wired.containsAll(wiredCommands))
    assertEquals(setOf("office_edit", "save_return"), pendingCommands.intersect(wired))
    val pendingCalls = Regex("pendingCommand\\(\\s*\"([a-z_0-9]+)\"").findAll(main).map { it.groupValues[1] }.toSet()
    assertEquals(setOf("office_edit", "save_return"), pendingCalls)
  }

  @Test
  fun checklistDocumentsAllTrackedCommands() {
    val checklist = File("../docs/spec/features/desktop-command-checklist.md").readText()
    val documented = Regex("`([a-z_0-9]+)`").findAll(checklist).map { it.groupValues[1] }.toSet()
    val missing = wiredCommands + pendingCommands - documented
    assertTrue("commands missing from SPEC: $missing", missing.isEmpty())
  }
}
