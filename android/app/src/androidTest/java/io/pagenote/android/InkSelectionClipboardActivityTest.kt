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
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class InkSelectionClipboardActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()
  private var previousReaderSettings: ReaderSettings? = null

  @Before
  fun useSinglePageViewportForAnnotationGestures() = runBlocking {
    val store = readerSettingsStore(composeRule.activity)
    previousReaderSettings = store.settings.first()
    store.save(requireNotNull(previousReaderSettings).copy(continuousPdfScrolling = false))
  }

  @After
  fun restoreReaderSettings() = runBlocking {
    previousReaderSettings?.let { readerSettingsStore(composeRule.activity).save(it) }
    Unit
  }

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
      reopenCompactMenu()
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performScrollTo().assertIsEnabled()
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performClick()

      val clipboard = context.getSystemService(ClipboardManager::class.java)
      val payload = clipboard.primaryClip!!.getItemAt(0).text.toString()
      assertTrue(payload.startsWith("PAGE_NOTES_ANNOTATION:\n"))

      reopenCompactMenu()
      composeRule.onNodeWithTag("reader_menu_command_paste_ink").performScrollTo().assertIsEnabled().performClick()
      composeRule.waitUntil(10_000) { session.pendingPenSaves.intValue == 0 }
      assertTrue("Ordinary ink saves should not leave a saved banner", session.penStatus.value != context.getString(R.string.pen_saved))
      assertEquals(2, pageStore.getItems(PenSide.DOCUMENT, 1).size)
      assertEquals(PenPoint(80f, 90f, 0.4f), pageStore.getItems(PenSide.DOCUMENT, 1).last().points.first())

      reopenCompactMenu()
      composeRule.onNodeWithTag("reader_menu_command_delete_ink").performScrollTo().assertIsEnabled().performClick()
      assertEquals(listOf(original), pageStore.getItems(PenSide.DOCUMENT, 1))
      reopenCompactMenu()
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().assertIsEnabled().performClick()
      assertEquals(2, pageStore.getItems(PenSide.DOCUMENT, 1).size)
      reopenCompactMenu()
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().assertIsEnabled().performClick()
      assertEquals(listOf(original), pageStore.getItems(PenSide.DOCUMENT, 1))
      reopenCompactMenu()
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

  @Test
  fun areaEraserSavesSplitStrokesAndUndoRestoresOriginal() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val original = PenStroke("pen", "#185abd", 5.0,
      listOf(PenPoint(150f, 500f, 0.4f), PenPoint(850f, 500f, 0.9f)))
    val pageStore = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 1, original) }
    val penStore = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project; session.replaceRenderer(renderer); session.markdownSource.value = null
      session.pageIndex.intValue = 0; session.penStore.value = penStore; session.penPageStore.value = pageStore
      session.penStrokes.value = listOf(original); session.penWritable.value = true; session.penTool.value = PenCanvasTool.ERASER
    }
    try {
      composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(checkNotNull(findPenCanvas(composeRule.activity.window.decorView))) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.width > 0 && canvas.selectedTool == PenCanvasTool.ERASER }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      val (x, y) = geometry.toViewPoint(PenPoint(500f, 500f, 1f))
      if (composeRule.onAllNodesWithTag("reader_menu_collapse").fetchSemanticsNodes().isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_collapse").performClick()
      }
      composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { click(Offset(x, y)) }
      composeRule.waitUntil(10_000) { pageStore.getItems(PenSide.DOCUMENT, 1).size == 2 && session.pendingPenSaves.intValue == 0 }
      val cut = penStore.readPage(PenSide.DOCUMENT, 1)
      assertEquals(2, cut.size)
      assertEquals(original.points.first(), cut.first().points.first())
      assertEquals(original.points.last(), cut.last().points.last())
      showHandwritingCommands()
      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      composeRule.waitUntil(10_000) { pageStore.getItems(PenSide.DOCUMENT, 1) == listOf(original) && session.pendingPenSaves.intValue == 0 }
      assertEquals(listOf(original), penStore.readPage(PenSide.DOCUMENT, 1))
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  private fun reopenCompactMenu() {
    if (ReaderMenuLayout.forWidth(composeRule.activity.resources.configuration.screenWidthDp) == ReaderMenuLayout.COMPACT_DRAWER) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
  }

  private fun selectInkTool() {
    showHandwritingCommands()
    composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
  }

  private fun showHandwritingCommands() {
    revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
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
