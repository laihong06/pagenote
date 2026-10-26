package io.pagenote.android

import android.content.ClipboardManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class InkSelectionClipboardActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun selectCopyPasteDeleteAndUndoOperateOnCurrentPdfPage() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val original = PenStroke(
      type = "pen",
      color = "#185abd",
      width = 5.0,
      points = listOf(PenPoint(250f, 350f, 0.4f), PenPoint(400f, 450f, 0.9f)),
    )
    val pageStore = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 1, original) }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = "# Note"
      session.noteDraft.value = "# Note"
      session.noteWritable.value = true
      session.penStore.value = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
      session.penPageStore.value = pageStore
      session.penStrokes.value = listOf(original)
      session.penWritable.value = true
      session.penTool.value = io.pagenote.feature.pen.PenCanvasTool.PEN
    }

    try {
      composeRule.waitUntil(15_000) {
        composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty()
      }
      showHandwritingCommands()
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performScrollTo().assertIsNotEnabled()

      selectInkTool()
      val nativeCanvasRef = AtomicReference<PenCanvasView>()
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        nativeCanvasRef.set(findPenCanvas(composeRule.activity.window.decorView) ?: error("ink canvas missing"))
      }
      val nativeCanvas = nativeCanvasRef.get()
      composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.SELECT }
      val geometry = PenCanvasGeometry(nativeCanvas.width.toFloat(), nativeCanvas.height.toFloat(), nativeCanvas.pageTransform)
      val strokePoint = PenPoint(325f, 400f, 0.7f)
      val touchPoint = Offset(geometry.toViewX(strokePoint), geometry.toViewY(strokePoint))
      composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput {
        click(touchPoint)
      }
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performScrollTo().assertIsEnabled()
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performClick()

      val clipboard = context.getSystemService(ClipboardManager::class.java)
      val payload = clipboard.primaryClip!!.getItemAt(0).text.toString()
      assertTrue(payload.startsWith("PAGE_NOTES_ANNOTATION:\n"))

      composeRule.onNodeWithTag("reader_menu_command_paste_ink").performScrollTo().assertIsEnabled().performClick()
      composeRule.waitUntil(10_000) { session.pendingPenSaves.intValue == 0 }
      assertEquals(2, pageStore.getItems(PenSide.DOCUMENT, 1).size)
      assertEquals(PenPoint(80f, 90f, 0.4f), pageStore.getItems(PenSide.DOCUMENT, 1).last().points.first())

      composeRule.onNodeWithTag("reader_menu_command_delete_ink").performScrollTo().assertIsEnabled().performClick()
      assertEquals(listOf(original), pageStore.getItems(PenSide.DOCUMENT, 1))
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().assertIsEnabled().performClick()
      assertEquals(2, pageStore.getItems(PenSide.DOCUMENT, 1).size)
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().assertIsEnabled().performClick()
      assertEquals(listOf(original), pageStore.getItems(PenSide.DOCUMENT, 1))
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      assertEquals(2, pageStore.getItems(PenSide.DOCUMENT, 1).size)
      composeRule.waitUntil(10_000) { session.pendingPenSaves.intValue == 0 }
      assertEquals(2, ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
        .readPage(PenSide.DOCUMENT, 1).size)
    } finally {
      composeRule.runOnUiThread {
        session.replaceRenderer(null)
        session.project.value = null
      }
    }
  }

  private fun selectInkTool() {
    showHandwritingCommands()
    composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
  }

  private fun showHandwritingCommands() {
    if (composeRule.onAllNodesWithTag("reader_menu_command_select_tool").fetchSemanticsNodes().isNotEmpty()) return
    if (composeRule.onAllNodesWithTag("reader_menu_trigger").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
    composeRule.onNodeWithTag("reader_menu_group_handwriting").performClick()
  }

  private fun findPenCanvas(view: View): PenCanvasView? {
    if (view is PenCanvasView) return view
    if (view is ViewGroup) {
      for (index in 0 until view.childCount) {
        findPenCanvas(view.getChildAt(index))?.let { return it }
      }
    }
    return null
  }

  private fun installFixture(resolver: android.content.ContentResolver) {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"c".repeat(64)}","name":"Ink clipboard fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, pdfFixture())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, PageNotes.join(listOf("# Note")).toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
  }

  private fun pdfFixture(): ByteArray {
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
    val canvas: Canvas = page.canvas
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawText("Ink clipboard", 50f, 70f, Paint(Paint.ANTI_ALIAS_FLAG))
    document.finishPage(page)
    return ByteArrayOutputStream().use { output ->
      try {
        document.writeTo(output)
        output.toByteArray()
      } finally {
        document.close()
      }
    }
  }

  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}
