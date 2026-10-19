package io.pagenote.android

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteLongMarkdownEditorTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun longUnbrokenToken_laysOutAndRemainsEditableWithoutChangingSource() {
    val token = "A".repeat(40_000)
    val markdown = "# before\n\n$dataUriPrefix$token\n\n## after"
    val notes = PageNotes.join(listOf("short note", "neighbor page"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditor()?.text?.toString() == "short note" }
    val editor = requireNotNull(findEditor())

    val startedAt = SystemClock.elapsedRealtime()
    composeRule.runOnUiThread { session.noteDraft.value = markdown }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      editor.isLaidOut && !editor.isLayoutRequested && editor.markdownSourceText() == markdown
    }
    composeRule.waitForIdle()
    val layoutElapsedMs = SystemClock.elapsedRealtime() - startedAt
    assertTrue("long note update and layout took ${layoutElapsedMs}ms", layoutElapsedMs < 5_000)
    assertTrue("long tokens should have invisible layout boundaries", editor.text.toString().contains("\u200e"))
    assertEquals(markdown, editor.markdownSourceText())
    assertEquals(markdown.length, editor.markdownSourceOffset(editor.selectionStart))
    val tokenStart = markdown.indexOf("A".repeat(40_000))
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setMarkdownSourceSelection(tokenStart + 128, tokenStart + 384)
      assertEquals(tokenStart + 128, editor.markdownSelectionStart())
      assertEquals(tokenStart + 384, editor.markdownSelectionEnd())
      assertTrue(editor.onTextContextMenuItem(android.R.id.copy))
      assertTrue(editor.onTextContextMenuItem(android.R.id.cut))
      assertEquals(
        markdown.removeRange(tokenStart + 128, tokenStart + 384),
        editor.markdownSourceText(),
      )
      assertTrue(editor.onTextContextMenuItem(android.R.id.paste))
      assertEquals(markdown, editor.markdownSourceText())
    }
    val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
      .getSystemService(ClipboardManager::class.java)
    assertEquals("A".repeat(256), clipboard?.primaryClip?.getItemAt(0)?.text?.toString())

    val editStartedAt = SystemClock.elapsedRealtime()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setMarkdownSourceSelection(8)
      editor.replaceMarkdownSourceRange(8, 8, " edited")
    }
    val edited = markdown.replaceRange(8, 8, " edited")
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteDraft.value == edited && !editor.isLayoutRequested
    }
    composeRule.waitForIdle()
    val editElapsedMs = SystemClock.elapsedRealtime() - editStartedAt
    assertTrue("editing the long token took ${editElapsedMs}ms", editElapsedMs < 5_000)
    assertEquals(edited, editor.markdownSourceText())

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == edited
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(edited, NotePage.pageAt(saved, 1))
    assertEquals("neighbor page", NotePage.pageAt(saved, 2))
  }

  @Test
  fun pastingLongUnbrokenToken_keepsClipboardAndSavedMarkdownUnmodified() {
    val notes = PageNotes.join(listOf("short note", "neighbor page"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditor("short note") != null }
    val editor = requireNotNull(findEditor("short note"))
    val pasted = "B".repeat(8_000)
    val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
      .getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(null, pasted))

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setMarkdownSourceSelection(0, editor.markdownSourceText().length)
      assertTrue(editor.onTextContextMenuItem(android.R.id.paste))
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteDraft.value == pasted && editor.markdownSourceText() == pasted &&
        editor.text.toString().contains("\u200e")
    }
    assertEquals(pasted.length, editor.markdownSourceOffset(editor.selectionStart))
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == pasted
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(pasted, NotePage.pageAt(saved, 1))
    assertEquals("neighbor page", NotePage.pageAt(saved, 2))
  }

  @Test
  fun pageJumpToLongMarkdown_remainsResponsiveAndSavesOnlyTargetPage() {
    val longNote = "# before\n\n$dataUriPrefix${"A".repeat(40_000)}\n\n## after"
    val pages = (1..31).map { page -> if (page == 31) longNote else "Page $page note" }
    val notes = PageNotes.join(pages)
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditor("Page 1 note") != null }

    composeRule.onNodeWithTag("reader_page_status").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("page_jump_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("page_jump_input").performTextClearance()
    composeRule.onNodeWithTag("page_jump_input").performTextInput("31")

    val startedAt = SystemClock.elapsedRealtime()
    composeRule.onNodeWithTag("page_jump_confirm").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.pageIndex.intValue == 30 && findEditor(longNote)?.let {
        it.isLaidOut && !it.isLayoutRequested
      } == true
    }
    composeRule.waitForIdle()
    val jumpElapsedMs = SystemClock.elapsedRealtime() - startedAt
    assertTrue("jump to the long note took ${jumpElapsedMs}ms", jumpElapsedMs < 5_000)
    val editor = requireNotNull(findEditor(longNote))
    assertEquals(longNote, editor.markdownSourceText())

    val edited = longNote.replaceRange(8, 8, " edited")
    InstrumentationRegistry.getInstrumentation().runOnMainSync { editor.text.insert(8, " edited") }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == edited }
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 31) == edited
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(edited, NotePage.pageAt(saved, 31))
    assertEquals("Page 30 note", NotePage.pageAt(saved, 30))
  }

  @Test
  fun reopeningRecentProjectRestoresLongMarkdownPageWithoutBlocking() {
    val longNote = largeRecoveryNote()
    assertTrue("recovery fixture should match the incident-scale page size", longNote.length in 56_000..58_000)
    assertTrue("recovery fixture should contain a 40 KB token", longNote.contains("$dataUriPrefix${"A".repeat(40_000)}"))
    val pages = (1..69).map { page -> if (page == 31) longNote else "Page $page note" }
    val notes = PageNotes.join(pages)
    val project = installProject(notes, pageCount = 69)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val store = recentProjectsStore(appContext)
    val entry = RecentProjectEntry(
      accessTreeUri = treeUri.toString(),
      rootDocumentId = ExistingProjectSafTestProvider.ROOT_ID,
      projectId = project.manifest.id,
      name = project.manifest.name,
      lastOpenedAtMillis = System.currentTimeMillis(),
    )

    try {
      testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_GRANT_PERSISTABLE_READ,
        null,
        Bundle().apply {
          putParcelable(ExistingProjectSafTestProvider.EXTRA_URI, treeUri)
          putString(ExistingProjectSafTestProvider.EXTRA_PACKAGE_NAME, appContext.packageName)
        },
      )
      appContext.contentResolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
      runBlocking {
        store.recentProjects.first().forEach { store.remove(it) }
        store.record(entry)
      }
      composeRule.onNodeWithTag("reader_menu_command_recent_documents")
        .performScrollTo().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("recent_project_open_0").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("recent_project_open_0").performClick()
      composeRule.waitUntil(timeoutMillis = 20_000) {
        session.project.value?.manifest?.id == project.manifest.id &&
          session.pageIndex.intValue == 0 && findEditor("Page 1 note") != null
      }
      assertEquals(69, session.renderer.value?.pageCount)

      composeRule.onNodeWithTag("reader_page_status").performClick()
      composeRule.onNodeWithTag("page_jump_input").performTextClearance()
      composeRule.onNodeWithTag("page_jump_input").performTextInput("31")
      composeRule.onNodeWithTag("page_jump_confirm").performClick()
      composeRule.waitUntil(timeoutMillis = 15_000) {
        session.pageIndex.intValue == 30 && findEditor(longNote) != null
      }
      composeRule.waitForIdle()
      assertNull("replacing the rendered PDF page must not fail on the callback thread", session.errorMessage.value)
      assertEquals(longNote, session.noteDraft.value)
      assertEquals(longNote, NotePage.pageAt(session.notesMarkdown.value, 31))
      assertTrue("persisted fixture grant should keep the project writable", session.noteWritable.value)
      composeRule.runOnIdle { assertEquals(30, session.saveCheckpoint()?.pageIndex) }

      val previousRenderer = requireNotNull(session.renderer.value)
      composeRule.onNodeWithTag("reader_menu_command_recent_documents")
        .performScrollTo().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("recent_project_open_0").fetchSemanticsNodes().isNotEmpty()
      }

      val startedAt = SystemClock.elapsedRealtime()
      composeRule.onNodeWithTag("recent_project_open_0").performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("recent_projects_cancel").fetchSemanticsNodes().isEmpty()
      }
      try {
        composeRule.waitUntil(timeoutMillis = 15_000) {
          session.renderer.value !== previousRenderer
        }
      } catch (timeout: Throwable) {
        throw AssertionError(
          "recent reopen did not replace the PDF renderer; page=${session.pageIndex.intValue}, " +
            "opening=${session.isOpening.value}, writable=${session.noteWritable.value}, " +
            "draftMatchesSaved=${session.noteDraft.value == NotePage.pageAt(session.notesMarkdown.value, 31)}, " +
            "error=${session.errorMessage.value}, status=${session.noteStatus.value}",
          timeout,
        )
      }
      assertEquals("checkpoint should reopen page 31", 30, session.pageIndex.intValue)
      composeRule.waitUntil(timeoutMillis = 15_000) {
        findEditor(longNote)?.let { it.isLaidOut && !it.isLayoutRequested } == true
      }
      composeRule.waitForIdle()
      val restoreElapsedMs = SystemClock.elapsedRealtime() - startedAt
      assertTrue("reopening and restoring page 31 took ${restoreElapsedMs}ms", restoreElapsedMs < 5_000)
      val editor = requireNotNull(findEditor(longNote))
      assertEquals(longNote, editor.markdownSourceText())

      val edited = longNote.replaceRange(8, 8, " edited")
      InstrumentationRegistry.getInstrumentation().runOnMainSync { editor.text.insert(8, " edited") }
      composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == edited }
      composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 31) == edited
      }
      val saved = ProjectNoteStore(testResolver, project.notesUri).read()
      assertEquals(edited, NotePage.pageAt(saved, 31))
      assertEquals("Page 30 note", NotePage.pageAt(saved, 30))
      composeRule.runOnIdle { assertEquals(30, session.saveCheckpoint()?.pageIndex) }
    } finally {
      if (InstrumentationRegistry.getArguments().getString("retain_long_markdown_fixture") == "true") {
        val remaining = runBlocking { store.recentProjects.first() }
        assertTrue(remaining.any { it.projectId == project.manifest.id })
        assertTrue(
          appContext.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
          },
        )
      } else {
        runBlocking { store.recentProjects.first().forEach { store.remove(it) } }
        appContext.contentResolver.releasePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
    }
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

  private fun installProject(notes: String, pageCount: Int = 31): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"e".repeat(64)}","name":"Long Markdown fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalPdf(pageCount))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }

  private fun minimalPdf(pageCount: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val offsets = mutableListOf(0)
    output.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
    val pageIds = (0 until pageCount).map { 3 + it * 2 }
    val objects = buildList {
      add("<< /Type /Catalog /Pages 2 0 R >>")
      add("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count $pageCount >>")
      pageIds.forEach { pageId ->
        add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> /Contents ${pageId + 1} 0 R >>")
        add("<< /Length 0 >>\nstream\n\nendstream")
      }
    }
    objects.forEachIndexed { index, body ->
      offsets += output.size()
      output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    val xrefOffset = output.size()
    output.write("xref\n0 ${offsets.size}\n0000000000 65535 f \n".toByteArray(Charsets.US_ASCII))
    offsets.drop(1).forEach { offset ->
      output.write("%010d 00000 n \n".format(offset).toByteArray(Charsets.US_ASCII))
    }
    output.write(
      "trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        .toByteArray(Charsets.US_ASCII),
    )
    return output.toByteArray()
  }

  private fun largeRecoveryNote(): String = buildString {
    append("# before\n\n")
    repeat(332) { append("Context paragraph with model assumptions and notes. ") }
    append(dataUriPrefix)
    append("A".repeat(40_000))
    append("\n\n## after")
  }

  private fun findEditor(): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val editors = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditors(editors)
      result.set(editors.firstOrNull { it.tag == "note_markdown_editor" })
    }
    return result.get()
  }

  private fun findEditor(expectedText: String): EditText? =
    findEditor()?.takeIf { it.markdownSourceText() == expectedText }

  private fun View.collectEditors(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditors(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  private companion object {
    const val dataUriPrefix = "data:image/png;base64,"
  }
}
