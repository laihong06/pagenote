package io.pagenote.android

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.core.project.ProjectManifest
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderSessionRestorationTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun activityRecreationRetainsOpenReaderAndDirtyDraft() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val pdfFile = File(context.cacheDir, "reader-session-recreation-${System.nanoTime()}.pdf")
      .apply { writeBytes(minimalPdf(pageCount = 2)) }
    val notesFile = File(context.cacheDir, "reader-session-notes-${System.nanoTime()}.md")
      .apply { writeText("saved page one\n\n${PageNotes.SEPARATOR}\n\nsaved page two") }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    val renderer = runBlocking {
      PdfDocumentSession.open(context, Uri.fromFile(pdfFile), context.cacheDir)
    }
    val notes = PageNotes.join(listOf("saved page one", "saved page two"))
    val penPageStore = PenPageStore().apply {
      addStroke(
        PenSide.DOCUMENT,
        2,
        PenStroke(
          type = "pen",
          color = "#185abd",
          width = 2.0,
          points = listOf(PenPoint(x = 120f, y = 240f, pressure = 0.8f)),
        ),
      )
    }
    val project = OpenedProject(
      manifest = ProjectManifest(
        format = "pagenote-project",
        version = 1,
        id = "a".repeat(64),
        name = "Session fixture",
        left = "src/session.pdf",
        right = "src/notes.md",
      ),
      sourceUri = Uri.fromFile(pdfFile),
      notesUri = Uri.fromFile(notesFile),
      sourceDirectoryUri = Uri.fromFile(context.cacheDir),
      annotationsUri = null,
      reviewsUri = null,
      handoffsUri = null,
      sourceName = "session.pdf",
      sourceExtension = "pdf",
    )
    val dirtyDraft = "unsaved page-two draft"
    val bookmark = PdfViewportBookmark(scale = 2.25f, centerX = 0.34f, centerY = 0.72f)

    try {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 1
      session.notesMarkdown.value = notes
      session.noteDraft.value = dirtyDraft
      session.noteBaseRevision.value = PageNotes.revision("saved page two")
      session.noteWritable.value = true
      session.penPageStore.value = penPageStore
      session.penStrokes.value = penPageStore.getItems(PenSide.DOCUMENT, 2)
      session.penWritable.value = false
      session.penTool.value = PenCanvasTool.HIGHLIGHTER
      session.viewportBookmark.value = bookmark

      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("reader_page_status").assertTextContains("2", substring = true)
      composeRule.activityRule.scenario.recreate()
      composeRule.waitForIdle()

      composeRule.onNodeWithTag("reader_page_status").assertTextContains("2", substring = true)
      val restored = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
      assertSame("the Activity must reuse its retained PDF session", renderer, restored.renderer.value)
      assertEquals("Session fixture", restored.project.value?.name)
      assertEquals(1, restored.pageIndex.intValue)
      assertEquals(dirtyDraft, restored.noteDraft.value)
      assertEquals(PageNotes.revision("saved page two"), restored.noteBaseRevision.value)
      assertEquals(bookmark, restored.viewportBookmark.value)
      assertEquals(PenCanvasTool.HIGHLIGHTER, restored.penTool.value)
      assertEquals(penPageStore.getItems(PenSide.DOCUMENT, 2), restored.penStrokes.value)
      assertEquals(2, requireNotNull(restored.renderer.value).pageCount)

      val renderedPage = runBlocking { requireNotNull(restored.renderer.value).render(1) }
      renderedPage.recycle()
    } finally {
      session.replaceRenderer(null)
      pdfFile.delete()
      notesFile.delete()
    }
  }

  @Test
  fun freshViewModelReadsCheckpointAndDurableDraftJournal() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val projectId = "c".repeat(64)
    val target = ProjectOpenTarget(
      accessTreeUri = Uri.parse("content://recovery-test/tree/$projectId"),
      rootDocumentId = "root-$projectId",
      needsPersistablePermission = false,
    )
    val savedPage = "saved page two"
    val dirtyDraft = "recovered after process recreation"
    val writer = ReaderSessionViewModel(SavedStateHandle()).apply {
      initializeRecovery(context)
      project.value = OpenedProject(
        manifest = ProjectManifest(
          format = "pagenote-project",
          version = 1,
          id = projectId,
          name = "Recovery fixture",
          left = "src/source.pdf",
          right = "src/notes.md",
        ),
        sourceUri = target.accessTreeUri,
        notesUri = Uri.parse("content://recovery-test/notes/$projectId"),
        sourceDirectoryUri = target.accessTreeUri,
        annotationsUri = null,
        reviewsUri = null,
        handoffsUri = null,
        sourceName = "source.pdf",
        sourceExtension = "pdf",
      )
      notesMarkdown.value = PageNotes.join(listOf("saved page one", savedPage))
      pageIndex.intValue = 1
      noteBaseRevision.value = PageNotes.revision(savedPage)
      viewportBookmark.value = PdfViewportBookmark(1.8f, 0.3f, 0.65f)
      penTool.value = PenCanvasTool.HIGHLIGHTER
      rememberOpenedProject(target)
      setNoteDraft(dirtyDraft)
      flushDraftRecovery()
    }
    val journal = AtomicFileNoteDraftRecoveryStore(File(context.filesDir, "reader-draft-recovery"))
    val recoveryKey = NoteDraftRecoveryKey(
      accessTreeUri = target.accessTreeUri.toString(),
      rootDocumentId = target.rootDocumentId,
      projectId = projectId,
      page = 2,
    )
    val savedState = SavedStateHandle().also { handle ->
      requireNotNull(writer.saveCheckpoint()).saveTo(handle)
    }
    val copiedState = SavedStateHandle(savedState.keys().associateWith { key -> savedState.get<Any?>(key) })

    try {
      val restored = ReaderSessionViewModel(copiedState).apply { initializeRecovery(context) }
      val restoredCheckpoint = requireNotNull(restored.claimRestorationCheckpoint())
      val recovered = restored.readDraftRecovery(
        restoredCheckpoint.toProjectOpenTarget(),
        restoredCheckpoint.projectId,
        restoredCheckpoint.pageIndex + 1,
      )

      assertEquals(ReaderSessionCheckpoint.restoreFrom(savedState), restoredCheckpoint)
      assertEquals(dirtyDraft, recovered?.draft)
      assertEquals(PageNotes.revision(savedPage), recovered?.baseRevision)
      assertEquals(NoteDraftRecoveryDecision.RESTORE_DRAFT, requireNotNull(recovered).assess(savedPage))
    } finally {
      journal.clear(recoveryKey)
    }
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
}
