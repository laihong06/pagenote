package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteImportActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun importsJsonAndMarkdownWhileRejectingCancellationErrorsAndStaleContext() {
    val originalPages = listOf("首页旧稿", "邻页保留", "第三页旧稿")
    val originalNotes = PageNotes.join(originalPages)
    val project = installProject(originalNotes)
    val session = attachProjectToReader(project, originalNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_edit").performClick()

    val pending = AtomicReference<PickerRequest?>()
    val pickerTypes = AtomicReference<Array<String>?>()
    val pickerField = MainActivity::class.java.getDeclaredField("noteImportPickerForTesting")
      .apply { isAccessible = true }
    val pickerHook: (Array<String>, (Uri?) -> Unit) -> Unit = { types, callback ->
      pickerTypes.set(types)
      pending.set(PickerRequest(callback))
    }
    composeRule.runOnUiThread { pickerField.set(composeRule.activity, pickerHook) }

    importCommand().performClick()
    val cancelRequest = awaitPickerRequest(pending)
    assertEquals(setOf("application/json", "text/markdown", "text/plain"), pickerTypes.get()?.toSet())
    cancelRequest.callback(null)
    composeRule.waitForIdle()
    assertEquals(originalNotes, ProjectNoteStore(testResolver, project.notesUri).read())

    val editorBeforeImport = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editorBeforeImport.setText("导入前脏草稿")
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "导入前脏草稿" }
    importCommand().performClick()
    awaitPickerRequest(pending).callback(NoteImportSafTestProvider.uri("backup.json"))
    composeRule.onNodeWithText(
      composeRule.activity.getString(R.string.reader_menu_import_confirm_message),
    ).assertExists()
    composeRule.onNodeWithText(
      composeRule.activity.getString(R.string.reader_menu_import_confirm),
    ).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_import_success)
    }
    val noteStore = ProjectNoteStore(testResolver, project.notesUri)
    val imported = noteStore.read()
    assertEquals("导入前脏草稿", NotePage.pageAt(imported, 1))
    assertEquals("邻页保留", NotePage.pageAt(imported, 2))
    assertEquals("", NotePage.pageAt(imported, 3))

    val editor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setText("prefixTAIL")
      editor.setSelection(6, 10)
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "prefixTAIL" }
    importCommand().performClick()
    awaitPickerRequest(pending).callback(NoteImportSafTestProvider.uri("notes.md"))
    val selectedText = "prefix**导入文本**"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == selectedText }
    assertEquals(6 + "**导入文本**".length, requireNotNull(findNoteEditor()).selectionStart)

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    importCommand().performClick()
    awaitPickerRequest(pending).callback(NoteImportSafTestProvider.uri("notes.md"))
    val appendedText = selectedText + "**导入文本**"
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteDraft.value == appendedText && findNoteEditor()?.text?.toString() == appendedText
    }
    composeRule.onNodeWithTag("note_tab_edit").assertIsSelected()

    val beforeConfirmationCancel = noteStore.read()
    importCommand().performClick()
    awaitPickerRequest(pending).callback(NoteImportSafTestProvider.uri("backup.json"))
    composeRule.onNodeWithText(
      composeRule.activity.getString(R.string.reader_menu_import_confirm_message),
    ).assertExists()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.cancel)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithText(
        composeRule.activity.getString(R.string.reader_menu_import_confirm_message),
      ).fetchSemanticsNodes().isEmpty()
    }
    importCommand()
    assertEquals(beforeConfirmationCancel, noteStore.read())

    importCommand().performClick()
    awaitPickerRequest(pending).callback(NoteImportSafTestProvider.uri("invalid.json"))
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_import_invalid_backup)
    }
    assertEquals(imported, noteStore.read())

    importCommand().performClick()
    val staleRequest = awaitPickerRequest(pending)
    composeRule.runOnUiThread { session.pageIndex.value = 1 }
    staleRequest.callback(NoteImportSafTestProvider.uri("notes.md"))
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_import_stale)
    }
    assertEquals(imported, noteStore.read())
    assertTrue(session.project.value === project)
  }

  private fun importCommand() = run {
    val tag = "reader_menu_command_import_notes"
    if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_file").performClick()
      } else if (composeRule.onAllNodesWithTag("reader_menu_group_file").fetchSemanticsNodes().isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_file").performScrollTo()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(tag).assertIsEnabled().performScrollTo()
  }

  private fun awaitPickerRequest(pending: AtomicReference<PickerRequest?>): PickerRequest {
    composeRule.waitUntil(timeoutMillis = 10_000) { pending.get() != null }
    return requireNotNull(pending.getAndSet(null))
  }

  private fun findNoteEditor(): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val matches = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditTexts(matches)
      result.set(matches.firstOrNull { it.tag == "note_markdown_editor" })
    }
    return result.get()
  }

  private fun View.collectEditTexts(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = "原文第一页\n\n${PageNotes.SEPARATOR}\n\n原文第二页\n\n${PageNotes.SEPARATOR}\n\n原文第三页"
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.pageIndex.value = 0
      session.noteStatus.value = null
    }
    return session
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"d".repeat(64)}","name":"导入测试","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(
      DocumentsContract.buildTreeDocumentUri(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.ROOT_ID,
      ),
    )
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

  private data class PickerRequest(val callback: (Uri?) -> Unit)
}
