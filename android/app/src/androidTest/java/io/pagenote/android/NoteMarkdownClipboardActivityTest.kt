package io.pagenote.android

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteMarkdownClipboardActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun menuClipboardCommandsCopyCutSelectAllAndSaveOnlyCurrentPage() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    listOf("cut", "copy", "select_all").forEach { command ->
      editCommand(command).assertIsNotEnabled()
    }

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    val editor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(editor.text.length)
    }
    composeRule.waitForIdle()
    editCommand("cut").assertIsNotEnabled()
    editCommand("copy").assertIsNotEnabled()
    editCommand("select_all").assertIsEnabled()

    val selectedText = "Selected"
    val selectedStart = savedPages.first().indexOf(selectedText)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(selectedStart + selectedText.length, selectedStart)
    }
    composeRule.waitForIdle()
    val clipboard = requireNotNull(composeRule.activity.getSystemService(ClipboardManager::class.java))

    editCommand("copy").assertIsEnabled().performClick()
    assertEquals(selectedText, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    assertEquals(savedPages.first(), session.noteDraft.value)

    editCommand("cut").assertIsEnabled().performClick()
    val expectedDraft = "Lead  tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expectedDraft }
    assertEquals(selectedText, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    assertEquals(expectedDraft, findNoteEditor()?.text?.toString())

    editCommand("select_all").assertIsEnabled().performClick()
    val editedEditor = requireNotNull(findNoteEditor())
    assertEquals(0, editedEditor.selectionStart)
    assertEquals(editedEditor.text.length, editedEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expectedDraft
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expectedDraft, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    listOf("cut", "copy", "select_all").forEach { command ->
      editCommand(command).assertIsNotEnabled()
    }
  }

  @Test
  fun pasteMarkdownUsesClipboardReplacesSelectionAndInsertsAtCaret() {
    val savedPages = listOf("Lead replace me tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)
    pasteMarkdownCommand().assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    val clipboard = requireNotNull(composeRule.activity.getSystemService(ClipboardManager::class.java))
    val editor = requireNotNull(findNoteEditor())

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      clipboard.clearPrimaryClip()
    }
    pasteMarkdownCommand().assertIsEnabled().performClick()
    assertEquals(
      composeRule.activity.getString(R.string.reader_menu_paste_markdown_empty),
      session.noteStatus.value,
    )
    assertEquals(savedPages.first(), session.noteDraft.value)

    val selectedText = "replace me"
    val selectedStart = savedPages.first().indexOf(selectedText)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(selectedStart + selectedText.length, selectedStart)
      clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", "**fresh**"))
    }
    pasteMarkdownCommand().assertIsEnabled().performClick()
    val replacedDraft = "Lead **fresh** tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == replacedDraft }
    val replacedEditor = requireNotNull(findNoteEditor())
    val caretAfterReplacement = "Lead **fresh**".length
    assertEquals(caretAfterReplacement, replacedEditor.selectionStart)
    assertEquals(caretAfterReplacement, replacedEditor.selectionEnd)
    assertEquals(null, session.noteStatus.value)

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", "!"))
    }
    pasteMarkdownCommand().assertIsEnabled().performClick()
    val expectedDraft = "Lead **fresh**! tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expectedDraft }
    val finalEditor = requireNotNull(findNoteEditor())
    assertEquals("Lead **fresh**!".length, finalEditor.selectionStart)
    assertEquals(finalEditor.selectionStart, finalEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expectedDraft
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expectedDraft, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))
  }

  private fun editCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_edit").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
  }

  private fun pasteMarkdownCommand() = run {
    val commandTag = "reader_menu_command_paste_markdown"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_insert").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
  }

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = "Source document text"
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    return session
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"d".repeat(64)}","name":"Markdown clipboard fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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
    return ProjectDocumentOpener(testResolver).open(treeUri)
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

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}
