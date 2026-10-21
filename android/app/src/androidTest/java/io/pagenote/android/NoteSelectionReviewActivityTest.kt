package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.NotePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteSelectionReviewActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun dirtyNoteIsSavedBeforeActivityCreatesSelectionReview() {
    val savedNote = "The source has a useful sentence."
    val editedNote = "$savedNote Added from the reader."
    val project = installProject(savedNote)
    val session = attachProjectToReader(project, savedNote)
    showDirtySelection(editedNote, start = 4, end = 10)

    invokeSelectionReview()

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("review_selection_quote").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("review_selection_quote").assertIsDisplayed().assertTextEquals("source")
    assertEquals(editedNote, NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1))
    assertEquals(PageNotes.revision(editedNote), session.noteBaseRevision.value)

    enterReviewInstruction("Explain this passage.")
    composeRule.onNodeWithTag("review_create").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { reviewWriteCount() == 1 }

    val record = Json.parseToJsonElement(readReviews(project)).jsonArray.single() as JsonObject
    assertEquals("note", record["side"]?.jsonPrimitive?.content)
    assertEquals("selection", record["kind"]?.jsonPrimitive?.content)
    assertEquals("page", record["scope"]?.jsonPrimitive?.content)
    assertEquals(1, record["page"]?.jsonPrimitive?.content?.toInt())
    assertEquals("Explain this passage.", record["instruction"]?.jsonPrimitive?.content)
    assertEquals("source", record["quote"]?.jsonPrimitive?.content)
    assertEquals(editedNote, record["context"]?.jsonPrimitive?.content)
    assertEquals(PageNotes.revision(editedNote), record["revision"]?.jsonPrimitive?.content)
    val anchor = record["anchor"] as JsonObject
    assertEquals("markdown-source-utf16", anchor["coordinateSystem"]?.jsonPrimitive?.content)
    assertEquals(4, anchor["start"]?.jsonPrimitive?.content?.toInt())
    assertEquals(10, anchor["end"]?.jsonPrimitive?.content?.toInt())
  }

  @Test
  fun conflictingDirtyNoteDoesNotOpenReviewOrOverwriteExternalText() {
    val savedNote = "The source has a useful sentence."
    val editedNote = "$savedNote Local edit."
    val externalNote = "External writer replaced the page."
    val project = installProject(savedNote)
    val session = attachProjectToReader(project, savedNote)
    showDirtySelection(editedNote, start = 4, end = 10)
    replaceNotes(externalNote)

    invokeSelectionReview()

    val expectedConflict = PageNoteConflictException().message
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteStatus.value == expectedConflict }
    assertEquals(expectedConflict, session.noteStatus.value)
    assertEquals(editedNote, session.noteDraft.value)
    assertFalse(
      "selection review must stay closed after its required note save conflicts",
      composeRule.onAllNodesWithTag("review_selection_quote").fetchSemanticsNodes().isNotEmpty(),
    )
    assertTrue(
      "review editor must remain closed after its required note save conflicts",
      composeRule.onAllNodesWithTag("review_instruction_editor").fetchSemanticsNodes().isEmpty(),
    )
    assertEquals(0, reviewWriteCount())
    assertEquals(externalNote, ProjectNoteStore(testResolver, project.notesUri).read())
  }

  private fun attachProjectToReader(project: OpenedProject, savedNote: String): ReaderSessionViewModel {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = "# Source document\n\nOriginal content."
      session.notesMarkdown.value = savedNote
      session.noteDraft.value = savedNote
      session.noteBaseRevision.value = PageNotes.revision(savedNote)
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditText(savedNote) != null }
    return session
  }

  private fun showDirtySelection(text: String, start: Int, end: Int) {
    val editor = requireNotNull(findEditText("The source has a useful sentence."))
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val setText = editor.performAccessibilityAction(
        AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        },
      )
      check(setText) { "native note editor rejected test input" }
      editor.setSelection(start, end)
    }
    composeRule.waitForIdle()
  }

  private fun invokeSelectionReview() {
    val commandTag = "reader_menu_command_selection_to_annotation"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo().assertIsEnabled().performClick()
  }

  private fun enterReviewInstruction(instruction: String) {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      findEditableNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow) != null
    }
    val editor = requireNotNull(
      findEditableNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow),
    )
    assertTrue(
      editor.performAction(
        AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, instruction)
        },
      ),
    )
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"Activity review fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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

  private fun replaceNotes(text: String) {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_REPLACE_NOTES,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, text.toByteArray(Charsets.UTF_8))
      },
    )
  }

  private fun reviewWriteCount(): Int = testResolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_REVIEW_WRITE_COUNT,
    null,
    null,
  )?.getInt(ExistingProjectSafTestProvider.EXTRA_COUNT) ?: -1

  private fun readReviews(project: OpenedProject): String =
    testResolver.openInputStream(requireNotNull(project.reviewsUri))
      ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: error("review JSON should be readable")

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

private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
  if (node == null) return null
  if (node.isEditable) return node
  for (index in 0 until node.childCount) findEditableNode(node.getChild(index))?.let { return it }
  return null
}
