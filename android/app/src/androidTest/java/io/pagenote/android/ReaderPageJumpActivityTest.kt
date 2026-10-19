package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReaderPageJumpActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun pageStatusValidatesAndJumpsAfterSavingDirtyPage() {
    val savedPages = listOf("First page note", "Second page note", "Third page note")
    val savedNotes = PageNotes.join(savedPages)
    val editedFirstPage = "First page note with an unsaved edit"
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    val targetStroke = PenStroke(
      type = "pen",
      color = "#185abd",
      width = 2.0,
      points = listOf(PenPoint(x = 120f, y = 240f, pressure = 0.8f)),
    )
    val penPageStore = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 3, targetStroke) }
    val nonDefaultBookmark = PdfViewportBookmark(scale = 2.0f, centerX = 0.25f, centerY = 0.75f)
    composeRule.runOnUiThread {
      session.penPageStore.value = penPageStore
      session.viewportBookmark.value = nonDefaultBookmark
    }
    editNativeNote("First page note", editedFirstPage)

    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("1 / 3").performClick()
    composeRule.onNodeWithTag("page_jump_dialog").assertIsDisplayed()
    val pageInput = composeRule.onNodeWithTag("page_jump_input").assertIsDisplayed().fetchSemanticsNode()
    assertEquals(AnnotatedString("1"), pageInput.config[SemanticsProperties.EditableText])

    enterPageNumber("4")
    composeRule.onNodeWithTag("page_jump_confirm").assertIsNotEnabled()
    composeRule.onNodeWithTag("page_jump_error").assertIsDisplayed()

    enterPageNumber("3")
    composeRule.onNodeWithTag("page_jump_confirm").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 2 }

    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("3 / 3")
    val savedNotesAfterJump = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(editedFirstPage, NotePage.pageAt(savedNotesAfterJump, 1))
    assertEquals("Second page note", NotePage.pageAt(savedNotesAfterJump, 2))
    assertEquals("Third page note", session.noteDraft.value)
    assertEquals(PageNotes.revision("Third page note"), session.noteBaseRevision.value)
    assertEquals(listOf(targetStroke), session.penStrokes.value)
    assertEquals(PdfViewportBookmark.DEFAULT, session.viewportBookmark.value)
    assertTrue(composeRule.onAllNodesWithTag("page_jump_dialog").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun revisionConflictKeepsCurrentPageDraftAndJumpDialogOpen() {
    val savedNotes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val editedFirstPage = "First page note with a local edit"
    val externalNotes = PageNotes.join(listOf("First page note from another editor", "Second page note", "Third page note"))
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    editNativeNote("First page note", editedFirstPage)
    replaceNotes(externalNotes)
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())

    composeRule.onNodeWithTag("reader_page_status").assertIsEnabled().performClick()
    enterPageNumber("3")
    composeRule.onNodeWithTag("page_jump_confirm").assertIsEnabled().performClick()

    val expectedConflict = PageNoteConflictException().message
    try {
      composeRule.waitUntil(timeoutMillis = 10_000) { session.noteStatus.value == expectedConflict }
    } catch (timeout: Throwable) {
      throw AssertionError(
        "Expected page conflict status; got ${session.noteStatus.value}, page ${session.pageIndex.intValue}, draft ${session.noteDraft.value}",
        timeout,
      )
    }
    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("1 / 3")
    composeRule.onNodeWithTag("page_jump_dialog").assertIsDisplayed()
    assertEquals(0, session.pageIndex.intValue)
    assertEquals(editedFirstPage, session.noteDraft.value)
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())
  }

  @Test
  fun pendingDirtySaveDisablesRepeatJumpInputAndDismissal() {
    val savedNotes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    editNativeNote("First page note", "First page note with a pending edit")
    pauseNextNoteWrite()

    composeRule.onNodeWithTag("reader_page_status").performClick()
    enterPageNumber("3")
    composeRule.onNodeWithTag("page_jump_confirm").assertIsEnabled().performClick()
    try {
      assertTrue("the note save should reach the paused SAF write", waitForPausedNoteWrite())
      composeRule.onNodeWithTag("page_jump_input").assertIsNotEnabled()
      composeRule.onNodeWithTag("page_jump_confirm").assertIsNotEnabled()
      composeRule.onNodeWithTag("page_jump_cancel").assertIsNotEnabled()
      assertEquals(0, session.pageIndex.intValue)
    } finally {
      releaseNoteWrite()
    }

    composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 2 }
    assertEquals("Third page note", session.noteDraft.value)
  }

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = PageNotes.join(listOf("Source one", "Source two", "Source three"))
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditText("First page note") != null }
    return session
  }

  private fun editNativeNote(original: String, replacement: String) {
    val editor = requireNotNull(findEditText(original))
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      assertTrue(
        editor.performAccessibilityAction(
          AccessibilityNodeInfo.ACTION_SET_TEXT,
          Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replacement)
          },
        ),
      )
    }
    composeRule.waitForIdle()
  }

  private fun enterPageNumber(value: String) {
    val field = composeRule.onNodeWithTag("page_jump_input")
    field.performTextClearance()
    field.performTextInput(value)
  }

  private fun pauseNextNoteWrite() {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_NOTE_WRITE,
      null,
      null,
    )
  }

  private fun waitForPausedNoteWrite(): Boolean = testResolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_WAIT_NOTE_WRITE,
    null,
    null,
  )?.getBoolean("started") == true

  private fun releaseNoteWrite() {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_RELEASE_NOTE_WRITE,
      null,
      null,
    )
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"b".repeat(64)}","name":"Page jump fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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

  private fun replaceNotes(notes: String) {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_REPLACE_NOTES,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
      },
    )
  }

  private fun findEditText(expectedText: String): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val matches = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditTexts(matches)
      result.set(matches.firstOrNull { it.text.toString() == expectedText })
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
