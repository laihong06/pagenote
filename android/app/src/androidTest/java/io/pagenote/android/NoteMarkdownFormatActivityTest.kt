package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Spanned
import android.util.TypedValue
import android.text.style.ClickableSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.noties.markwon.ext.tables.TableSpan
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class NoteMarkdownFormatActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun menuFormattingCommandsWrapSelectionAndSaveOnlyCurrentPage() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    formatCommand("bold").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    formatCommand("bold").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    listOf("bold", "italic", "strikethrough").forEach { command ->
      selectCurrentWord()
      formatCommand(command).assertIsEnabled().performClick()
      val expected = when (command) {
        "bold" -> "Lead **Selected** tail"
        "italic" -> "Lead ***Selected*** tail"
        else -> "Lead ***~~Selected~~*** tail"
      }
      try {
        composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
      } catch (error: Throwable) {
        throw AssertionError(
          "Expected draft <$expected>, got draft <${session.noteDraft.value}> and editor <${findNoteEditor()?.text}>",
          error,
        )
      }
    }

    val expectedCurrentPage = "Lead ***~~Selected~~*** tail"
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expectedCurrentPage
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expectedCurrentPage, NotePage.pageAt(saved, 1))
    assertEquals(savedPages[1], NotePage.pageAt(saved, 2))
    assertEquals(expectedCurrentPage, session.noteDraft.value)

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview() != null }
    val rendered = findRenderedPreview()?.text as? Spanned
      ?: error("formatted Markdown preview must expose native spans")
    val spans = rendered.getSpans(0, rendered.length, Any::class.java).toList()
    assertTrue("preview must render bold", spans.any { it is StrongEmphasisSpan })
    assertTrue("preview must render italic", spans.any { it is EmphasisSpan })
    assertTrue("preview must render strikethrough", spans.any { it is StrikethroughSpan })
  }

  @Test
  fun underlineCommandUsesDesktopMarkupAndRendersUnderlineSpan() {
    val savedPages = listOf("Lead **Selected** tail <b>raw html</b>", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    formatCommand("underline").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    formatCommand("underline").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    selectCurrentWord()
    formatCommand("underline").assertIsEnabled().performClick()
    val selectedExpected = "Lead **<u>Selected</u>** tail <b>raw html</b>"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == selectedExpected }
    var editor = requireNotNull(findNoteEditor())
    val selectedStart = selectedExpected.indexOf("Selected")
    assertEquals(selectedStart, editor.selectionStart)
    assertEquals(selectedStart + "Selected".length, editor.selectionEnd)
    composeRule.waitUntil(timeoutMillis = 10_000) {
      val source = findNoteEditor()?.text
      if (source !is Spanned) {
        false
      } else {
        val sourceStart = source.indexOf("Selected")
        source.getSpans(0, source.length, UnderlineSpan::class.java).any { span ->
          source.getSpanStart(span) <= sourceStart &&
            source.getSpanEnd(span) >= sourceStart + "Selected".length
        }
      }
    }

    val placeholder = composeRule.activity.getString(R.string.reader_menu_format_placeholder)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setSelection(editor.text.length)
    }
    formatCommand("underline").assertIsEnabled().performClick()
    val expected = "$selectedExpected<u>$placeholder</u>"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    editor = requireNotNull(findNoteEditor())
    assertEquals(expected.indexOf(placeholder), editor.selectionStart)
    assertEquals(expected.indexOf(placeholder) + placeholder.length, editor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Selected") != null }
    val rendered = findRenderedPreview("Selected")?.text as? Spanned
      ?: error("underline preview must expose native spans")
    val renderedSelectionStart = rendered.indexOf("Selected")
    val boldSpans = rendered.getSpans(0, rendered.length, StrongEmphasisSpan::class.java)
    assertTrue(
      "existing bold markup must remain rendered around selected text",
      boldSpans.any { span ->
        rendered.getSpanStart(span) <= renderedSelectionStart &&
          rendered.getSpanEnd(span) >= renderedSelectionStart + "Selected".length
      },
    )
    val underlineSpans = rendered.getSpans(0, rendered.length, UnderlineSpan::class.java)
    assertTrue(
      "selected text must render with an UnderlineSpan",
      underlineSpans.any { span ->
        rendered.getSpanStart(span) <= renderedSelectionStart &&
          rendered.getSpanEnd(span) >= renderedSelectionStart + "Selected".length
      },
    )
    val rawHtmlStart = rendered.indexOf("raw html")
    assertTrue("raw HTML content must remain visible", rawHtmlStart >= 0)
    assertFalse(
      "other HTML tag handlers must remain disabled",
      rendered.getSpans(0, rendered.length, StrongEmphasisSpan::class.java).any { span ->
        rendered.getSpanStart(span) <= rawHtmlStart &&
          rendered.getSpanEnd(span) >= rawHtmlStart + "raw html".length
      },
    )
  }

  @Test
  fun paragraphCommandsPrefixSelectedLinesAndSaveOnlyCurrentPage() {
    val initial = "intro\nalpha\nbeta\ngamma\ndelta\nepsilon"
    val savedPages = listOf(initial, "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    paragraphCommand("heading").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    paragraphCommand("unordered_list").assertIsNotEnabled()
    paragraphCommand("task_list").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    paragraphCommand("font_size").assertIsNotEnabled()
    paragraphCommand("task_list").assertIsEnabled()
    selectCurrentLine("intro")
    paragraphCommand("heading").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("reader_heading_cancel").assertIsDisplayed().performClick()
    assertEquals(initial, session.noteDraft.value)

    paragraphCommand("heading").performClick()
    (1..3).forEach { level ->
      composeRule.onNodeWithTag("reader_heading_level_$level").assertIsDisplayed()
    }
    composeRule.onNodeWithTag("reader_heading_level_2").performClick()
    val afterHeading = "## intro\nalpha\nbeta\ngamma\ndelta\nepsilon"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterHeading }

    selectTextRange("alpha", "beta")
    paragraphCommand("unordered_list").assertIsEnabled().performClick()
    val afterBullets = "## intro\n- alpha\n- beta\ngamma\ndelta\nepsilon"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterBullets }

    selectCurrentLine("gamma")
    paragraphCommand("ordered_list").assertIsEnabled().performClick()
    val afterNumbered = "## intro\n- alpha\n- beta\n1. gamma\ndelta\nepsilon"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterNumbered }

    selectCurrentLine("delta")
    paragraphCommand("quote").assertIsEnabled().performClick()
    val afterQuote = "## intro\n- alpha\n- beta\n1. gamma\n> delta\nepsilon"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == afterQuote }

    selectCurrentLine("epsilon")
    paragraphCommand("task_list").assertIsEnabled().performClick()
    val expected = "## intro\n- alpha\n- beta\n1. gamma\n> delta\n- [ ] epsilon"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("intro") != null }
    val rendered = findRenderedPreview("intro")?.text?.toString()
      ?: error("formatted Markdown preview must render the current page")
    listOf("intro", "alpha", "beta", "gamma", "delta", "epsilon").forEach { text ->
      assertTrue("preview must include <$text>", rendered.contains(text))
    }
    val taskPreview = findRenderedPreview("epsilon")?.text as? Spanned
      ?: error("task-list preview must expose native spans")
    val taskSpans = taskPreview.getSpans(0, taskPreview.length, TaskListSpan::class.java)
    assertEquals(1, taskSpans.size)
    assertFalse("inserted task list starts unchecked", taskSpans.single().isDone)
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun noteFontSizeAppliesToEditorAndPreviewWithoutChangingNote() {
    val markdown = "A note stays intact"
    val notes = PageNotes.join(listOf(markdown, "Second page"))
    val project = installProject(notes)
    val settingsStore = readerSettingsStore(composeRule.activity)
    runBlocking {
      settingsStore.save(settingsStore.settings.first().copy(noteFontSize = 15))
    }

    val session = attachProjectToReader(project, notes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    paragraphCommand("font_size").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("reader_note_font_size_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_note_font_size_option_24").performClick()
    composeRule.onNodeWithTag("reader_note_font_size_cancel").performClick()
    composeRule.waitForIdle()
    assertEquals(15, runBlocking { settingsStore.settings.first().noteFontSize })
    assertEquals(markdown, session.noteDraft.value)

    paragraphCommand("font_size").performClick()
    composeRule.onNodeWithTag("reader_note_font_size_option_24").performClick()
    composeRule.onNodeWithTag("reader_note_font_size_apply").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      runBlocking { settingsStore.settings.first().noteFontSize == 24 }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      val editor = findNoteEditor() ?: return@waitUntil false
      matchesTextSizeSp(editor, 24)
    }
    assertEquals(markdown, session.noteDraft.value)

    composeRule.runOnUiThread { session.noteWritable.value = false }
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor()?.isEnabled == false }
    paragraphCommand("font_size").assertIsNotEnabled()
    composeRule.runOnUiThread { session.noteWritable.value = true }
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor()?.isEnabled == true }

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      val preview = findRenderedPreview("A note") ?: return@waitUntil false
      matchesTextSizeSp(preview, 24)
    }
    paragraphCommand("font_size").assertIsNotEnabled()
    assertEquals(markdown, session.noteDraft.value)

    composeRule.activityRule.scenario.recreate()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      val editor = findNoteEditor() ?: return@waitUntil false
      matchesTextSizeSp(editor, 24)
    }
    assertEquals(markdown, session.noteDraft.value)
  }

  @Test
  fun markdownUndoRedoCommandsRestoreDraftAndSaveOnlyCurrentPage() {
    val savedPages = listOf("Original page", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    editCommand("note_undo").assertIsNotEnabled()
    editCommand("note_redo").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    editCommand("note_undo").assertIsNotEnabled()
    editCommand("note_redo").assertIsNotEnabled()

    val editor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setText("Edited page")
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "Edited page" }
    editCommand("note_undo").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == savedPages[0] }
    assertEquals(savedPages[0].length, requireNotNull(findNoteEditor()).selectionStart)
    editCommand("note_undo").assertIsNotEnabled()
    editCommand("note_redo").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "Edited page" }

    editCommand("note_undo").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == savedPages[0] }
    val branchedEditor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      branchedEditor.setText("Branched page")
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "Branched page" }
    editCommand("note_redo").assertIsNotEnabled()
    editCommand("note_undo").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == savedPages[0] }
    editCommand("note_redo").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "Branched page" }

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == "Branched page"
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals("Branched page", NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.runOnUiThread { session.noteWritable.value = false }
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor()?.isEnabled == false }
    editCommand("note_undo").assertIsNotEnabled()
    editCommand("note_redo").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    editCommand("note_undo").assertIsNotEnabled()
    editCommand("note_redo").assertIsNotEnabled()
  }

  @Test
  fun codeBlockCommandWrapsSelectionAndSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("code").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("code").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    selectCurrentWord()
    insertCommand("code").assertIsEnabled().performClick()
    val expected = "Lead \n```python\nSelected\n```\n tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val editor = requireNotNull(findNoteEditor())
    val selectedStart = expected.indexOf("Selected")
    assertEquals(selectedStart, editor.selectionStart)
    assertEquals(selectedStart + "Selected".length, editor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Selected") != null }
    val rendered = findRenderedPreview("Selected")?.text?.toString()
      ?: error("code block preview must render the selected source")
    assertTrue("preview must retain code", rendered.contains("Selected"))
    assertTrue("preview must not expose Markdown fences", !rendered.contains("```"))
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun formulaCommandWrapsSelectionAndSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead x^2 tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("formula").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("formula").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val editor = requireNotNull(findNoteEditor())
    val formulaStart = editor.text.indexOf("x^2")
    check(formulaStart >= 0) { "formula source must remain in the Markdown editor" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(formulaStart, formulaStart + "x^2".length)
    }
    composeRule.waitForIdle()

    insertCommand("formula").assertIsEnabled().performClick()
    val expected = "Lead \n\n$$\nx^2\n$$\n\n tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val updatedEditor = requireNotNull(findNoteEditor())
    val selectedStart = expected.indexOf("x^2")
    assertEquals(selectedStart, updatedEditor.selectionStart)
    assertEquals(selectedStart + "x^2".length, updatedEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Lead") != null }
    val rendered = findRenderedPreview("Lead")?.text as? Spanned
      ?: error("formula preview must expose native spans")
    assertFalse("preview must not expose Markdown block delimiters", rendered.toString().contains("$$"))
    val formulaSpan = rendered.getSpans(0, rendered.length, JLatexAsyncDrawableSpan::class.java).single()
    val formulaDrawable = (formulaSpan as AsyncDrawableSpan).drawable
    composeRule.waitUntil(timeoutMillis = 10_000) { formulaDrawable.hasResult() }
    assertTrue(
      "valid formula must render instead of using the error fallback",
      formulaDrawable.result?.javaClass?.name?.startsWith("ru.noties.jlatexmath.") == true,
    )
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun tableCommandReplacesSelectionAndSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead replace me tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("table").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("table").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val editor = requireNotNull(findNoteEditor())
    val selectedText = "replace me"
    val selectionStart = editor.text.indexOf(selectedText)
    check(selectionStart >= 0) { "selected note text must remain in the Markdown editor" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(selectionStart, selectionStart + selectedText.length)
    }
    composeRule.waitForIdle()

    insertCommand("table").assertIsEnabled().performClick()
    val table = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"
    val expected = "Lead $table tail"
    val cursor = "Lead ".length + table.length
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val updatedEditor = requireNotNull(findNoteEditor())
    assertEquals(cursor, updatedEditor.selectionStart)
    assertEquals(cursor, updatedEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("Lead") != null }
    val rendered = findRenderedPreview("Lead")?.text as? Spanned
      ?: error("table preview must expose native spans")
    assertTrue("GFM table must render as a native table span", rendered.getSpans(0, rendered.length, TableSpan::class.java).isNotEmpty())
    assertEquals("header and content must both render as table rows", 2, rendered.getSpans(0, rendered.length, TableRowSpan::class.java).size)
    assertEquals(expected, session.noteDraft.value)
  }

  @Test
  fun linkCommandWrapsSelectionInsertsAndSelectsPlaceholderSavesOnlyCurrentPage() {
    val savedPages = listOf("Lead chosen tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    insertCommand("link").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    insertCommand("link").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val selectedText = "chosen"
    val selectionStart = savedPages.first().indexOf(selectedText)
    val selectionEditor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      selectionEditor.requestFocus()
      selectionEditor.setSelection(selectionStart + selectedText.length, selectionStart)
    }
    composeRule.waitForIdle()

    insertCommand("link").assertIsEnabled().performClick()
    val selectedLink = "[$selectedText](https://example.com)"
    val expectedAfterSelection = "Lead $selectedLink tail"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expectedAfterSelection }
    val selectedEditor = requireNotNull(findNoteEditor())
    assertEquals("Lead [".length, selectedEditor.selectionStart)
    assertEquals("Lead [".length + selectedText.length, selectedEditor.selectionEnd)

    val placeholder = composeRule.activity.getString(R.string.reader_menu_link_placeholder)
    val insertionEditor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      insertionEditor.setSelection(insertionEditor.text.length)
    }
    insertCommand("link").performClick()
    val expected = "$expectedAfterSelection[$placeholder](https://example.com)"
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
    val placeholderEditor = requireNotNull(findNoteEditor())
    assertEquals(expectedAfterSelection.length + 1, placeholderEditor.selectionStart)
    assertEquals(expectedAfterSelection.length + 1 + placeholder.length, placeholderEditor.selectionEnd)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expected
    }
    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expected, NotePage.pageAt(persisted, 1))
    assertEquals(savedPages[1], NotePage.pageAt(persisted, 2))

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview("chosen") != null }
    val rendered = findRenderedPreview("chosen")?.text as? Spanned
      ?: error("link preview must expose native spans")
    assertTrue(
      "Markdown link preview must expose a clickable span",
      rendered.getSpans(0, rendered.length, ClickableSpan::class.java).isNotEmpty(),
    )
  }

  private fun formatCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_format").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
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

  private fun paragraphCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_paragraph").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
  }

  private fun insertCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
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

  private fun selectCurrentWord() {
    val editor = requireNotNull(findNoteEditor())
    val text = editor.text.toString()
    val start = text.indexOf("Selected")
    check(start >= 0) { "formatted word must remain in the Markdown source" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, start + "Selected".length)
    }
    composeRule.waitForIdle()
  }

  private fun selectCurrentLine(text: String) {
    val editor = requireNotNull(findNoteEditor())
    val markdown = editor.text.toString()
    val start = markdown.indexOf(text)
    check(start >= 0) { "expected <$text> to remain in the Markdown source" }
    val end = start + text.length
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, end)
    }
    composeRule.waitForIdle()
  }

  private fun selectTextRange(firstText: String, lastText: String) {
    val editor = requireNotNull(findNoteEditor())
    val markdown = editor.text.toString()
    val start = markdown.indexOf(firstText)
    val lastStart = markdown.indexOf(lastText, start + firstText.length)
    check(start >= 0 && lastStart >= 0) { "expected selected Markdown text to remain in the editor" }
    val end = lastStart + lastText.length
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, end)
    }
    composeRule.waitForIdle()
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"c".repeat(64)}","name":"Markdown format fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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

  private fun findRenderedPreview(containsText: String = "Selected"): TextView? {
    val result = AtomicReference<TextView?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val hosts = mutableListOf<MermaidPreviewHost>()
      composeRule.activity.window.decorView.collectPreviewHosts(hosts)
      result.set(hosts.firstOrNull { it.markdownView.text.toString().contains(containsText) }?.markdownView)
    }
    return result.get()
  }

  private fun matchesTextSizeSp(view: TextView, size: Int): Boolean {
    val expectedPixels = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      size.toFloat(),
      composeRule.activity.resources.displayMetrics,
    )
    return kotlin.math.abs(view.textSize - expectedPixels) < 0.5f
  }

  private fun View.collectEditTexts(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }

  private fun View.collectPreviewHosts(destination: MutableList<MermaidPreviewHost>) {
    if (this is MermaidPreviewHost) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectPreviewHosts(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}
