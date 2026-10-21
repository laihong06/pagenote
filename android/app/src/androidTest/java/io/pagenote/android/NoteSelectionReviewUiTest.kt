package io.pagenote.android

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.pagenote.core.project.PageNotes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteSelectionReviewUiTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun nativeEditTextSelectionReportsUtf16RangeAndSelectedQuote() {
    val markdown = "A😀B"
    val selection = AtomicReference<TextRange?>(null)
    composeRule.setContent {
      MaterialTheme {
        NotePanel(
          page = 1,
          markdown = markdown,
          dirty = false,
          writable = true,
          saving = false,
          status = null,
          onMarkdownChange = {},
          onSave = {},
          onReload = {},
          onSelectionChanged = { selection.set(it) },
          animationEnabled = false,
        )
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000) { findEditText(markdown) != null }
    val editor = requireNotNull(findEditText(markdown))
    InstrumentationRegistry.getInstrumentation().runOnMainSync { editor.setSelection(1, 3) }
    composeRule.waitUntil(timeoutMillis = 5_000) { selection.get() == TextRange(1, 3) }

    val reported = requireNotNull(selection.get())
    val draft = NoteSelectionReviewDraft.capture(1, markdown, reported.start, reported.end)
    assertEquals("😀", draft.quote)
  }

  @Test
  fun changingNoteContentPageOrPreviewClearsNativeSelection() {
    val page = mutableStateOf(1)
    val markdown = mutableStateOf("Original note")
    val selection = AtomicReference<TextRange?>(null)
    composeRule.setContent {
      MaterialTheme {
        NotePanel(
          page = page.value,
          markdown = markdown.value,
          dirty = false,
          writable = true,
          saving = false,
          status = null,
          onMarkdownChange = {},
          onSave = {},
          onReload = {},
          onSelectionChanged = { selection.set(it) },
          animationEnabled = false,
        )
      }
    }

    selectNativeRange("Original note", 0, 8, selection)
    composeRule.runOnIdle { markdown.value = "Updated note" }
    composeRule.waitUntil(timeoutMillis = 5_000) { selection.get() == null }

    selectNativeRange("Updated note", 0, 7, selection)
    val previewLabel = InstrumentationRegistry.getInstrumentation().targetContext
      .getString(R.string.note_preview)
    composeRule.onNodeWithText(previewLabel).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { selection.get() == null }

    composeRule.onNodeWithText(
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.note_edit),
    ).performClick()
    selectNativeRange("Updated note", 0, 7, selection)
    composeRule.runOnIdle { page.value = 2 }
    composeRule.waitUntil(timeoutMillis = 5_000) { selection.get() == null }
  }

  @Test
  fun selectionReviewCommandRequiresValidWritableSelectionAndIdleReader() {
    val selectedRange = mutableStateOf<TextRange?>(null)
    val writable = mutableStateOf(true)
    val readerIdle = mutableStateOf(true)
    val markdown = mutableStateOf("A😀B")
    composeRule.setContent {
      MaterialTheme {
        ReaderAdaptiveMenu(
          layout = ReaderMenuLayout.COMPACT_DRAWER,
          groups = listOf(
            ReaderMenuSection(
              group = ReaderMenuGroup.WORD_EDITING,
              label = "Word Editing",
              commands = listOf(
                ReaderMenuCommand(
                  id = "selection_to_annotation",
                  label = "Selected text to AI review",
                  enabled = selectionToAnnotationEnabled(
                    selectedRange.value,
                    markdown.value,
                    writable.value,
                    readerIdle.value,
                  ),
                  disabledReason = "Select writable note text while the reader is idle.",
                  onSelect = {},
                ),
              ),
            ),
          ),
          expanded = true,
          onExpandedChange = {},
          drawerOpen = true,
          onDrawerOpenChange = {},
        ) { Column {} }
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_selection_to_annotation")
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_menu_command_selection_to_annotation").assertIsNotEnabled()
    composeRule.runOnIdle { selectedRange.value = TextRange(1, 3) }
    composeRule.onNodeWithTag("reader_menu_command_selection_to_annotation").assertIsEnabled()
    composeRule.runOnIdle {
      markdown.value = "  \t"
      selectedRange.value = TextRange(0, 3)
    }
    composeRule.onNodeWithTag("reader_menu_command_selection_to_annotation").assertIsNotEnabled()
    composeRule.runOnIdle {
      markdown.value = "A😀B"
      selectedRange.value = TextRange(1, 3)
    }
    composeRule.runOnIdle { writable.value = false }
    composeRule.onNodeWithTag("reader_menu_command_selection_to_annotation").assertIsNotEnabled()
    composeRule.runOnIdle {
      writable.value = true
      readerIdle.value = false
    }
    composeRule.onNodeWithTag("reader_menu_command_selection_to_annotation").assertIsNotEnabled()
  }

  @Test
  fun oversizedSelectionReasonIsLocalizedAndShownByTheMenu() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val reasonId = appContext.resources.getIdentifier(
      "reader_menu_selection_too_large",
      "string",
      appContext.packageName,
    )
    assertTrue("selection size feedback resource should exist", reasonId != 0)

    assertEquals(
      SelectionReviewReadiness.AVAILABLE,
      selectionReviewReadiness(0, 100_000, "q".repeat(100_000)),
    )
    assertEquals(
      SelectionReviewReadiness.TOO_LARGE,
      selectionReviewReadiness(0, 100_001, "q".repeat(100_001)),
    )
    assertEquals(
      SelectionReviewReadiness.AVAILABLE,
      selectionReviewReadiness(0, 1, "c".repeat(1_000_000)),
    )
    val readiness = selectionReviewReadiness(0, 1, "c".repeat(1_000_001))
    assertEquals(SelectionReviewReadiness.TOO_LARGE, readiness)

    val disabledReason = when (readiness) {
      SelectionReviewReadiness.TOO_LARGE -> appContext.getString(reasonId)
      SelectionReviewReadiness.AVAILABLE -> error("oversized selection must be disabled")
      SelectionReviewReadiness.SELECTION_REQUIRED -> error("valid range must not be reported missing")
    }
    val english = appContext.getString(reasonId)
    assertEquals("The selection or page is too large to review.", english)
    assertEquals(english, disabledReason)
    val chineseConfig = Configuration(appContext.resources.configuration).apply {
      setLocale(Locale.SIMPLIFIED_CHINESE)
    }
    val chinese = appContext.createConfigurationContext(chineseConfig).getString(reasonId)
    assertEquals("所选文本或页面过大，无法进行审阅。", chinese)
    val chineseDisabledReason = when (readiness) {
      SelectionReviewReadiness.TOO_LARGE ->
        appContext.createConfigurationContext(chineseConfig).getString(reasonId)
      SelectionReviewReadiness.AVAILABLE -> error("oversized selection must be disabled")
      SelectionReviewReadiness.SELECTION_REQUIRED -> error("valid range must not be reported missing")
    }
    assertEquals(chinese, chineseDisabledReason)

    @androidx.compose.runtime.Composable
    fun showDisabledReason(reason: String) {
      MaterialTheme {
        ReaderAdaptiveMenu(
          layout = ReaderMenuLayout.COMPACT_DRAWER,
          groups = listOf(
            ReaderMenuSection(
              group = ReaderMenuGroup.WORD_EDITING,
              label = "Word Editing",
              commands = listOf(
                ReaderMenuCommand(
                  id = "selection_to_annotation",
                  label = "Selected text to AI review",
                  enabled = false,
                  disabledReason = reason,
                  onSelect = {},
                ),
              ),
            ),
          ),
          expanded = true,
          onExpandedChange = {},
          drawerOpen = true,
          onDrawerOpenChange = {},
        ) { Column {} }
      }
    }

    composeRule.setContent { showDisabledReason(disabledReason) }
    composeRule.onNodeWithText(disabledReason).assertIsDisplayed()
  }

  @Test
  fun oversizedSelectionReadinessDoesNotCreateSubstring() {
    val text = SubsequenceForbiddenText("q".repeat(100_001))

    assertEquals(
      SelectionReviewReadiness.TOO_LARGE,
      selectionReviewReadiness(0, text.length, text),
    )
  }

  @Test
  fun oversizedSelectionReadinessRejectsBeforeReadingCharacters() {
    val text = IndexForbiddenText(100_001)

    assertEquals(
      SelectionReviewReadiness.TOO_LARGE,
      selectionReviewReadiness(0, text.length, text),
    )
  }

  @Test
  fun selectionReviewDialogShowsQuoteAndCancelDoesNotWrite() {
    val project = installProject()
    val draft = selectionDraft()
    val dismissed = AtomicBoolean(false)
    val status = mutableStateOf("")
    showSelectionDialog(project, draft, status) { dismissed.set(true) }

    composeRule.onNodeWithTag("review_selection_quote")
      .assertIsDisplayed()
      .assertTextEquals("source")
    composeRule.onNodeWithTag("reviews_close").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { dismissed.get() }
    assertEquals(0, reviewWriteCount())
  }

  @Test
  fun selectionReviewDialogPersistsQuoteContextAndAnchor() {
    val project = installProject()
    val draft = selectionDraft()
    val status = mutableStateOf("")
    showSelectionDialog(project, draft, status) {}
    enterInstructionAndSave()

    composeRule.waitUntil(timeoutMillis = 5_000) { reviewWriteCount() == 1 }
    val record = requireNotNull(
      ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
        .list(1).singleOrNull() as? JsonObject,
    )
    assertEquals("note", record["side"]?.jsonPrimitive?.contentOrNull)
    assertEquals("selection", record["kind"]?.jsonPrimitive?.contentOrNull)
    assertEquals("page", record["scope"]?.jsonPrimitive?.contentOrNull)
    assertEquals("source", record["quote"]?.jsonPrimitive?.contentOrNull)
    assertEquals("The source has a useful sentence.", record["context"]?.jsonPrimitive?.contentOrNull)
    assertEquals(draft.revision, record["revision"]?.jsonPrimitive?.contentOrNull)
    assertEquals("markdown-source-utf16", (record["anchor"] as JsonObject)["coordinateSystem"]?.jsonPrimitive?.content)
    assertEquals(4, (record["anchor"] as JsonObject)["start"]?.jsonPrimitive?.content?.toInt())
    assertEquals(10, (record["anchor"] as JsonObject)["end"]?.jsonPrimitive?.content?.toInt())
  }

  @Test
  fun successfulReviewSaveShowsRetainedRecoveryCopyName() {
    val project = installProject()
    configureRecoveryDeleteFailure(1)
    val status = mutableStateOf("")
    showSelectionDialog(project, selectionDraft(), status) {}
    enterInstructionAndSave()

    composeRule.waitUntil(timeoutMillis = 5_000) {
      status.value.contains("pagenote-recovery-")
    }
    assertTrue(
      status.value.startsWith(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.review_saved),
      ),
    )
    assertEquals(1, reviewWriteCount())
  }

  @Test
  fun staleSelectionReviewIsRejectedBeforeReviewFileWrite() {
    val project = installProject()
    val draft = selectionDraft()
    replaceNotes("The source changed externally.")
    val status = mutableStateOf("")
    showSelectionDialog(project, draft, status) {}
    enterInstructionAndSave()

    val staleMessage = InstrumentationRegistry.getInstrumentation().targetContext
      .getString(R.string.review_selection_stale)
    composeRule.waitUntil(timeoutMillis = 5_000) { status.value == staleMessage }
    assertEquals(0, reviewWriteCount())
  }

  @Test
  fun selectionReviewDraftCannotBeSavedOnAnotherPageWithMatchingText() {
    val repeatedPage = "The source has a useful sentence."
    val project = installProject(PageNotes.join(listOf(repeatedPage, repeatedPage)))
    val draft = selectionDraft()
    val status = mutableStateOf("")
    showSelectionDialog(project, draft, status, page = 2) {}
    enterInstructionAndSave()

    val staleMessage = InstrumentationRegistry.getInstrumentation().targetContext
      .getString(R.string.review_selection_stale)
    composeRule.waitUntil(timeoutMillis = 5_000) { status.value == staleMessage }
    assertEquals(0, reviewWriteCount())
  }

  private fun selectNativeRange(
    expectedText: String,
    start: Int,
    end: Int,
    selection: AtomicReference<TextRange?>,
  ) {
    composeRule.waitUntil(timeoutMillis = 5_000) { findEditText(expectedText) != null }
    val editor = requireNotNull(findEditText(expectedText))
    InstrumentationRegistry.getInstrumentation().runOnMainSync { editor.setSelection(start, end) }
    composeRule.waitUntil(timeoutMillis = 5_000) { selection.get() == TextRange(start, end) }
  }

  private fun showSelectionDialog(
    project: OpenedProject,
    draft: NoteSelectionReviewDraft,
    status: MutableState<String>,
    page: Int = 1,
    onDismiss: () -> Unit,
  ) {
    composeRule.setContent {
      MaterialTheme {
        Column {
          Text(status.value, Modifier.testTag("selection_review_status"))
          ReviewListDialog(
            page = page,
            project = project,
            noteRevision = draft.revision,
            selectionDraft = draft,
            onDismiss = onDismiss,
            onChanged = { status.value = it },
          )
        }
      }
    }
  }

  private fun enterInstructionAndSave() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      findEditableNode(instrumentation.uiAutomation.rootInActiveWindow) != null
    }
    val editor = requireNotNull(findEditableNode(instrumentation.uiAutomation.rootInActiveWindow))
    assertTrue(
      editor.performAction(
        AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply {
          putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            "Review this selected source.",
          )
        },
      ),
    )
    composeRule.onNodeWithTag("review_create").assertIsEnabled().performClick()
  }

  private fun selectionDraft() = NoteSelectionReviewDraft.capture(
    page = 1,
    context = "The source has a useful sentence.",
    start = 4,
    end = 10,
  )

  private fun installProject(
    notes: String = "The source has a useful sentence.",
  ): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"Selection UI fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(
          ExistingProjectSafTestProvider.EXTRA_NOTES,
          notes.toByteArray(Charsets.UTF_8),
        )
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray(Charsets.UTF_8))
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

  private fun configureRecoveryDeleteFailure(count: Int) {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_RECOVERY_DELETE_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, count) },
    )
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}

private class SubsequenceForbiddenText(private val value: String) : CharSequence {
  override val length: Int get() = value.length

  override fun get(index: Int): Char = value[index]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    error("selection readiness must not allocate a text slice")
}

private class IndexForbiddenText(override val length: Int) : CharSequence {
  override fun get(index: Int): Char = error("oversized selection must be rejected before scanning")

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    error("oversized selection must be rejected before slicing")
}

private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
  if (node == null) return null
  if (node.className == EditText::class.java.name && node.isEditable) return node
  for (index in 0 until node.childCount) {
    findEditableNode(node.getChild(index))?.let { return it }
  }
  return null
}

private fun findEditText(expectedText: String): EditText? {
  val result = AtomicReference<EditText?>()
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
    val activity = ActivityLifecycleMonitorRegistry.getInstance()
      .getActivitiesInStage(Stage.RESUMED).firstOrNull() ?: return@runOnMainSync
    val matches = mutableListOf<EditText>()
    activity.window.decorView.collectEditTexts(matches)
    result.set(matches.firstOrNull { it.text.toString() == expectedText })
  }
  return result.get()
}

private fun View.collectEditTexts(destination: MutableList<EditText>) {
  if (this is EditText) destination += this
  if (this is ViewGroup) {
    for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }
}
