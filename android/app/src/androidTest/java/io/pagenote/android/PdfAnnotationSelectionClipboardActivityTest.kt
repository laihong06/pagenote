package io.pagenote.android

import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.content.ContentValues
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfAnnotationSelectionClipboardActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test fun referencePolylinePersistsAndUndoesAsOneStroke() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.PEN }
      val x = canvas.width * .2f
      val y = canvas.height * .2f
      val dx = canvas.width * .012f
      val dy = canvas.height * .012f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, x, y, .5f, time, time)
      for (i in 1..20) dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x + i * dx, y, .5f, time, time + i * 10)
      for (i in 1..20) dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x + 20 * dx, y + i * dy, .5f, time, time + (20 + i) * 10)
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, x + 20 * dx, y + 20 * dy, .5f, time, time + 1100)
      waitForAnnotations(annotations) { items -> items.size == 1 && (items[0] as JsonObject)[PenStroke.POLYLINE_FIELD]?.jsonPrimitive?.content == "true" }
      val saved = annotations.readPage("document", 1)[0] as JsonObject
      assertEquals("pen", saved["type"]?.jsonPrimitive?.content)
      assertTrue(saved["points"]!!.jsonArray.size >= 3)
      composeRule.waitForIdle()
      val target = InstrumentationRegistry.getInstrumentation().targetContext
      val screenshotUri = target.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "reference-polyline.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PageNoteEvidence")
      })
      checkNotNull(screenshotUri)
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
        target.contentResolver.openOutputStream(screenshotUri).use { stream ->
          checkNotNull(stream)
          bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
      }
      Thread.sleep(300) // Let MediaStore finish indexing the evidence image.
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceSampledLinePersistsAndUndoRemovesIt() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.PEN }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      val startX = canvas.width * .15f
      val increment = canvas.width * .10f
      val y = canvas.height * .35f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, startX, y, .5f, time, time)
      for (i in 1..6) dispatchStylus(canvas, MotionEvent.ACTION_MOVE, startX + i * increment, y, .5f, time, time + i * 10)
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, startX + 6 * increment, y, .5f, time, time + 700)
      waitForAnnotations(annotations) { it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line" }
      val saved = points(annotations.readPage("document", 1)[0] as JsonObject)
      val expected = geometry.toDocumentPoint(startX + increment, y, 1f)!!
      assertEquals(expected.x.toDouble(), saved[0], .1)
      assertEquals(expected.y.toDouble(), saved[1], .1)
      composeRule.waitForIdle()
      val screenshot = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "reference-sampled-line.png")
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
        screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
      }
      Thread.sleep(1500) // Allow the evidence collector to copy before test-package teardown.
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test
  @Ignore("Known defect: interleaved ink and annotation histories are not yet unified")
  fun interleavedShapesAndInkUndoInGestureOrder() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    val penFile = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = "# Note"
      session.noteDraft.value = "# Note"
      session.penStore.value = penFile
      session.penPageStore.value = PenPageStore()
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
      session.penTool.value = PenCanvasTool.PEN
    }
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      fun stroke(y: Float, long: Boolean) {
        val now = SystemClock.uptimeMillis()
        val x = canvas.width * .2f
        val end = canvas.width * if (long) .75f else .23f
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, x, y, .5f, now, now)
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, end, y, .5f, now, now + 10)
        if (long) Thread.sleep(650)
        dispatchStylus(canvas, MotionEvent.ACTION_UP, end, y, .5f, now, now + if (long) 700 else 20)
      }
      fun types(): List<String> = annotations.readPage("document", 1).map { (it as JsonObject)["type"]!!.jsonPrimitive.content }.sorted()
      fun expect(vararg expected: String) { composeRule.waitUntil(10_000) { runCatching { types() == expected.sorted() }.getOrDefault(false) } }
      fun command(id: String) {
        revealReaderMenuCommand(composeRule, "handwriting", id)
        composeRule.onNodeWithTag("reader_menu_command_$id").performScrollTo().performClick()
      }
      stroke(canvas.height * .2f, true); expect("line")
      stroke(canvas.height * .5f, false); expect("line", "pen")
      stroke(canvas.height * .7f, true); expect("line", "line", "pen")
      command("pen_undo"); expect("line", "pen")
      command("pen_undo"); expect("line")
      command("pen_undo"); expect()
      command("pen_redo"); expect("line")
      command("pen_redo"); expect("line", "pen")
      command("pen_redo"); expect("line", "line", "pen")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }


  @Test fun undoWhileInkSaveQueuedTargetsTheLatestInkGesture() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    var locked = false
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      fun stroke(y: Float, dwell: Boolean) {
        val time = SystemClock.uptimeMillis()
        val x = canvas.width * .2f
        val end = canvas.width * if (dwell) .75f else .26f
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, x, y, .5f, time, time)
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, end, y, .5f, time, time + 10)
        if (dwell) Thread.sleep(650)
        dispatchStylus(canvas, MotionEvent.ACTION_UP, end, y, .5f, time, time + if (dwell) 700 else 20)
      }
      stroke(canvas.height * .3f, true)
      waitForAnnotations(annotations) { it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line" }
      runBlocking { session.penSaveMutex.lock() }; locked = true
      stroke(canvas.height * .7f, false)
      composeRule.waitUntil(5_000) { session.penStrokes.value.size == 1 }
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      session.penSaveMutex.unlock(); locked = false
      waitForAnnotations(annotations) { it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line" && session.penStrokes.value.isEmpty() }
    } finally {
      if (locked) session.penSaveMutex.unlock()
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun lateShapeSaveDoesNotOverrideNewerInkGestureOrder() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    var locked = false
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      fun stroke(y: Float, dwell: Boolean) {
        val time = SystemClock.uptimeMillis()
        val x = canvas.width * .2f
        val end = canvas.width * if (dwell) .75f else .26f
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, x, y, .5f, time, time)
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, end, y, .5f, time, time + 10)
        if (dwell) Thread.sleep(650)
        dispatchStylus(canvas, MotionEvent.ACTION_UP, end, y, .5f, time, time + if (dwell) 700 else 20)
      }
      runBlocking { session.penSaveMutex.lock() }; locked = true
      stroke(canvas.height * .3f, true)
      stroke(canvas.height * .7f, false)
      composeRule.waitUntil(5_000) { session.penStrokes.value.size == 1 }
      session.penSaveMutex.unlock(); locked = false
      waitForAnnotations(annotations) { it.size == 2 }
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line" && session.penStrokes.value.isEmpty() }
    } finally {
      if (locked) session.penSaveMutex.unlock()
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun undoNewInkThenShapeDeletionKeepsInterleavedPage() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement("""[{"type":"rect","color":"#185abd","width":4,"points":[[100,100],[400,400]]},{"type":"pen","color":"#000000","width":2,"points":[[50,60],[70,80]],"pressures":[1,1]},{"type":"rect","color":"#dc3545","width":4,"points":[[600,600],[800,800]]}]""") as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null && session.penStrokes.value.size == 1 }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }
      composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { click(Offset(geometry.toViewX(PenPoint(250f, 100f, 1f)), geometry.toViewY(PenPoint(250f, 100f, 1f)))) }
      revealReaderMenuCommand(composeRule, "handwriting", "delete_ink")
      composeRule.onNodeWithTag("reader_menu_command_delete_ink").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it == JsonArray(initial.drop(1)) }
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.PEN }
      val start = PenPoint(700f, 900f, .5f)
      val end = PenPoint(750f, 900f, .5f)
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), .5f, time, time)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(end), geometry.toViewY(end), .5f, time, time + 10)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(end), geometry.toViewY(end), .5f, time, time + 20)
      waitForAnnotations(annotations) { it.size == 3 }
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it == JsonArray(initial.drop(1)) }
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it == initial }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun changedAnnotationRejectsStaleDelete() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement("""[{"type":"rect","color":"#185abd","width":4,"points":[[100,100],[400,400]]}]""") as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }
      val hit = PenPoint(250f, 100f, 1f)
      composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { click(Offset(geometry.toViewX(hit), geometry.toViewY(hit))) }
      val changed = JsonArray(listOf(JsonObject((initial[0] as JsonObject) + ("color" to JsonPrimitive("#00aa88")))))
      annotations.writePage("document", 1, changed)
      revealReaderMenuCommand(composeRule, "handwriting", "delete_ink")
      composeRule.onNodeWithTag("reader_menu_command_delete_ink").assertIsEnabled().performScrollTo().performClick()
      composeRule.waitUntil(10_000) { session.penStatus.value == context.getString(R.string.pen_annotation_edit_changed) }
      assertEquals(changed, annotations.readPage("document", 1))
      composeRule.waitForIdle()
      Thread.sleep(600)
      val screenshot = java.io.File(context.getExternalFilesDir(null), "annotation-edit-conflict.png")
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
        screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
      }
      Thread.sleep(1500)
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun movingAndDeletingShapeAfterWritingInkKeepsInkInReopenedProject() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, Json.parseToJsonElement("""[{"type":"pen","color":"#000000","width":2,"points":[[50,60],[70,80]],"pressures":[1,1]},{"type":"rect","color":"#185abd","width":4,"points":[[100,100],[400,400]]}]""") as JsonArray)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
    val ref = AtomicReference<PenCanvasView>()
    composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
    val nativeCanvas = ref.get()
    composeRule.waitUntil(10_000) { nativeCanvas.pageTransform != null && session.penStrokes.value.size == 1 }
    revealReaderMenuCommand(composeRule, "handwriting", "pen")
    composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
    composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.PEN }
    val geometry = PenCanvasGeometry(nativeCanvas.width.toFloat(), nativeCanvas.height.toFloat(), nativeCanvas.pageTransform)
    val inkStart = PenPoint(700f, 700f, .5f)
    val inkEnd = PenPoint(750f, 700f, .5f)
    val now = SystemClock.uptimeMillis()
    dispatchStylus(nativeCanvas, MotionEvent.ACTION_DOWN, geometry.toViewX(inkStart), geometry.toViewY(inkStart), .5f, now, now)
    dispatchStylus(nativeCanvas, MotionEvent.ACTION_MOVE, geometry.toViewX(inkEnd), geometry.toViewY(inkEnd), .5f, now, now + 10)
    dispatchStylus(nativeCanvas, MotionEvent.ACTION_UP, geometry.toViewX(inkEnd), geometry.toViewY(inkEnd), .5f, now, now + 20)
    waitForAnnotations(annotations) { it.size == 3 }
    revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
    composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
    composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.SELECT }
    val hit = PenPoint(250f, 100f, 1f)
    composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { click(Offset(geometry.toViewX(hit), geometry.toViewY(hit))) }
    revealReaderMenuCommand(composeRule, "handwriting", "ink_color")
    composeRule.onNodeWithTag("reader_menu_command_ink_color").performScrollTo().performClick()
    composeRule.onNodeWithTag("ink_color_swatch_red").performClick()
    composeRule.onNodeWithTag("ink_color_apply").performClick()
    waitForAnnotations(annotations) { page ->
      (page.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" } as? JsonObject)
        ?.get("color")?.jsonPrimitive?.content == "#dc3545"
    }
    val moved = PenPoint(280f, 130f, 1f)
    composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput {
      down(Offset(geometry.toViewX(hit), geometry.toViewY(hit)))
      moveTo(Offset(geometry.toViewX(moved), geometry.toViewY(moved)), delayMillis = 500)
      up()
    }
    waitForAnnotations(annotations) { page ->
      val shape = page.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" } as? JsonObject
      page.size == 3 && shape?.let(::points)?.firstOrNull()?.let { it > 110.0 } == true
    }
    revealReaderMenuCommand(composeRule, "handwriting", "pen")
    composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
    composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.PEN }
    val secondStart = PenPoint(700f, 800f, .5f)
    val secondEnd = PenPoint(750f, 800f, .5f)
    val secondTime = SystemClock.uptimeMillis()
    dispatchStylus(nativeCanvas, MotionEvent.ACTION_DOWN, geometry.toViewX(secondStart), geometry.toViewY(secondStart), .5f, secondTime, secondTime)
    dispatchStylus(nativeCanvas, MotionEvent.ACTION_MOVE, geometry.toViewX(secondEnd), geometry.toViewY(secondEnd), .5f, secondTime, secondTime + 10)
    dispatchStylus(nativeCanvas, MotionEvent.ACTION_UP, geometry.toViewX(secondEnd), geometry.toViewY(secondEnd), .5f, secondTime, secondTime + 20)
    waitForAnnotations(annotations) { it.size == 4 }
    revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
    composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
    composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.SELECT }
    composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { click(Offset(geometry.toViewX(moved), geometry.toViewY(moved))) }
    revealReaderMenuCommand(composeRule, "handwriting", "delete_ink")
    composeRule.onNodeWithTag("reader_menu_command_delete_ink").assertIsEnabled().performScrollTo().performClick()
    waitForAnnotations(annotations) { it.size == 3 && it.all { item -> (item as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" } }
    val reopened = ProjectDocumentOpener(resolver).open(treeUri)
    val reopenedPen = ProjectPenStore(resolver, reopened.sourceDirectoryUri, reopened.annotationsUri)
    assertEquals(3, reopenedPen.readPage(PenSide.DOCUMENT, 1).size)
    composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
  }

  @Test
  fun endpointDwellPersistsNativeShapeAndUsesAnnotationUndoHistory() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val otherPageBefore = annotations.readPage("document", 2)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    val projectPenStore = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = "# Note"
      session.noteDraft.value = "# Note"
      session.noteWritable.value = true
      session.penStore.value = projectPenStore
      session.penPageStore.value = PenPageStore()
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
      session.penTool.value = io.pagenote.feature.pen.PenCanvasTool.PEN
    }

    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0
      }
      val canvasRef = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread {
        canvasRef.set(findPenCanvas(composeRule.activity.window.decorView) ?: error("ink canvas missing"))
      }
      val canvas = canvasRef.get()
      val freehandDownTime = SystemClock.uptimeMillis()
      val freehandStartX = canvas.width * 0.18f
      val freehandEndX = canvas.width * 0.27f
      val freehandY = canvas.height * 0.68f
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, freehandStartX, freehandY, 0.4f, freehandDownTime, freehandDownTime)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, freehandEndX, freehandY, 0.8f, freehandDownTime, freehandDownTime + 10)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, freehandEndX, freehandY, 0.8f, freehandDownTime, freehandDownTime + 20)
      composeRule.waitUntil(10_000) { runCatching { projectPenStore.readPage(PenSide.DOCUMENT, 1).size == 1 }.getOrDefault(false) }
      val savedFreehand = projectPenStore.readPage(PenSide.DOCUMENT, 1).single()

      val downTime = SystemClock.uptimeMillis()
      val startX = canvas.width * 0.15f
      val endX = canvas.width * 0.75f
      val y = canvas.height * 0.3f
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, startX, y, 0.5f, downTime, downTime)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, endX, y, 0.8f, downTime, downTime + 10)
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, endX, y, 0.6f, downTime, downTime + 700)

      waitForAnnotations(annotations) { it.any { item ->
        (item as? JsonObject)?.get("type")?.jsonPrimitive?.content == "line"
      }
      }
      composeRule.waitForIdle()
      assertTrue("Recognized ink should not leave a saved banner", session.penStatus.value != context.getString(R.string.pen_annotation_saved))
      val savedPage = annotations.readPage("document", 1)
      assertEquals(listOf("pen", "line"), savedPage.map { (it as JsonObject)["type"]?.jsonPrimitive?.content })
      val line = savedPage.last() as JsonObject
      assertEquals("#dc3545", line["color"]?.jsonPrimitive?.content)
      assertEquals(3.0, line["width"]?.jsonPrimitive?.content?.toDouble())
      assertTrue("native vector shapes must not serialize freehand pressure samples", "pressures" !in line)
      assertEquals(listOf(savedFreehand), projectPenStore.readPage(PenSide.DOCUMENT, 1))
      assertEquals(otherPageBefore, annotations.readPage("document", 2))

      // Simulate a queued ink save completing after the shape snapshot was taken.
      val laterFreehand = PenStroke("pen", "#123456", 2.0, listOf(
        PenPoint(31f, 42f, 0.6f), PenPoint(80f, 42f, 0.7f),
      ))
      projectPenStore.writePage(PenSide.DOCUMENT, 1, listOf(savedFreehand, laterFreehand))

      val beforeConflict = annotations.readPage("document", 1)
      val conflicted = JsonArray(beforeConflict.map { item ->
        val obj = item as? JsonObject
        if (obj?.get("type")?.jsonPrimitive?.content == "line") {
          JsonObject(obj + ("concurrent" to JsonPrimitive("other editor")))
        } else item
      })
      annotations.writePage("document", 1, conflicted)
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        session.penStatus.value == context.getString(R.string.pen_annotation_history_changed)
      }
      assertEquals(conflicted, annotations.readPage("document", 1))
      composeRule.waitForIdle()
      Thread.sleep(600) // Capture after the menu's close animation.
      val screenshotFile = java.io.File(context.getExternalFilesDir(null), "annotation-history-conflict.png")
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
        screenshotFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
      }
      Thread.sleep(1500) // Let the evidence collector copy the screenshot before test teardown.
      annotations.writePage("document", 1, beforeConflict)

      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      try {
        waitForAnnotations(annotations) { it.size == 2 &&
          it.all { item -> (item as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" }
        }
      } catch (error: Throwable) {
        val actualPage = annotations.readPage("document", 1)
        throw AssertionError("page=$actualPage status=${session.penStatus.value}", error)
      }
      revealReaderMenuCommand(composeRule, "pen", "pen_redo")
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 3 &&
        (it.lastOrNull() as? JsonObject)?.get("type")?.jsonPrimitive?.content == "line"
      }
      assertEquals(listOf(savedFreehand, laterFreehand), projectPenStore.readPage(PenSide.DOCUMENT, 1))
    } finally {
      composeRule.runOnUiThread {
        session.replaceRenderer(null)
        session.project.value = null
      }
    }
  }

  @Test
  fun recognizedShapeDoesNotBlockWritingWhileSaveIsQueued() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.penStore.value = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
      session.penPageStore.value = PenPageStore()
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
      session.penTool.value = PenCanvasTool.PEN
    }
    var locked = false
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val canvasRef = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { canvasRef.set(checkNotNull(findPenCanvas(composeRule.activity.window.decorView))) }
      val canvas = canvasRef.get()
      runBlocking { session.penSaveMutex.lock() }; locked = true
      val down = SystemClock.uptimeMillis()
      val y = canvas.height * 0.3f
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, canvas.width * 0.15f, y, 0.5f, down, down)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, canvas.width * 0.75f, y, 0.8f, down, down + 10)
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, canvas.width * 0.75f, y, 0.6f, down, down + 700)
      composeRule.waitForIdle()
      composeRule.runOnIdle {
        assertTrue("recognition save must not disable the canvas", canvas.inputEnabled)
        assertTrue("recognition save must not display a waiting banner", session.penStatus.value != context.getString(R.string.annotation_saving))
      }
      val next = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, canvas.width * 0.2f, canvas.height * 0.6f, 0.5f, next, next)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, canvas.width * 0.3f, canvas.height * 0.6f, 0.8f, next, next + 10)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, canvas.width * 0.3f, canvas.height * 0.6f, 0.8f, next, next + 20)
      composeRule.runOnIdle { assertEquals("a second stroke is accepted before saving finishes", 1, session.penStrokes.value.size) }
      composeRule.activityRule.scenario.recreate()
      session.penSaveMutex.unlock(); locked = false
      waitForAnnotations(annotations) { page ->
        page.any { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "line" } &&
          page.any { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" }
      }
    } finally {
      if (locked) session.penSaveMutex.unlock()
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test
  fun recognizedShapeWriteFailureFallsBackToOriginalFreehandStroke() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    val projectPenStore = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = "# Note"
      session.noteDraft.value = "# Note"
      session.noteWritable.value = true
      session.penStore.value = projectPenStore
      session.penPageStore.value = PenPageStore()
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
      session.penTool.value = PenCanvasTool.PEN
    }

    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0
      }
      val canvasRef = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread {
        canvasRef.set(findPenCanvas(composeRule.activity.window.decorView) ?: error("ink canvas missing"))
      }
      val canvas = canvasRef.get()
      val downTime = SystemClock.uptimeMillis()
      val startX = canvas.width * 0.15f
      val endX = canvas.width * 0.75f
      val y = canvas.height * 0.3f
      resolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_CONFIGURE_ANNOTATION_WRITE_FAILURE,
        null,
        Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, 1) },
      )
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, startX, y, 0.45f, downTime, downTime)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, endX, y, 0.85f, downTime, downTime + 10)
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, endX, y, 0.65f, downTime, downTime + 700)

      composeRule.waitUntil(10_000) {
        val page = runCatching { annotations.readPage("document", 1) }.getOrNull() ?: return@waitUntil false
        page.size == 1 && (page.singleOrNull() as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen"
      }
      val savedFreehand = projectPenStore.readPage(PenSide.DOCUMENT, 1).single()
      assertEquals("pen", savedFreehand.type)
      assertTrue("the fallback must keep sampled geometry", savedFreehand.points.size >= 2)
      assertTrue("the fallback must keep pressure samples", savedFreehand.points.any { it.pressure < 1f })
      assertEquals(
        "the failed recognition must not leave a native vector line",
        listOf("pen"),
        annotations.readPage("document", 1).map { (it as JsonObject)["type"]?.jsonPrimitive?.content },
      )
    } finally {
      composeRule.runOnUiThread {
        session.replaceRenderer(null)
        session.project.value = null
      }
    }
  }

  @Test
  fun nativeAnnotationSelectCopyPasteMoveDeleteUndoAndRedoPreserveFreehandItems() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver, projectId = UUID.randomUUID().toString().replace("-", "").repeat(2))
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, Json.parseToJsonElement("""
      [
        {"type":"rect","color":"#185abd","width":4,"points":[[100,100],[400,400]],"custom":"preserve"},
        {"type":"line","color":"#dc3545","width":4,"points":[[600,600],[800,600]]}
      ]
    """) as JsonArray)
    val untouchedOtherPage = Json.parseToJsonElement("""
      [{"type":"rect","color":"#00aa88","width":3,"points":[[20,30],[70,80]],"pageOnly":"keep"}]
    """) as JsonArray
    annotations.writePage("document", 2, untouchedOtherPage)
    val originalStroke = PenStroke(
      type = "pen",
      color = "#185abd",
      width = 5.0,
      points = listOf(PenPoint(750f, 850f, 0.7f), PenPoint(770f, 870f, 0.8f)),
    )
    val projectPenStore = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    projectPenStore.writePage(PenSide.DOCUMENT, 1, listOf(originalStroke))
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val appAnnotations = ProjectAnnotationStore(
      composeRule.activity.contentResolver,
      project.sourceDirectoryUri,
      project.annotationsUri,
    )
    composeRule.waitUntil(10_000) { appAnnotations.readPage("document", 1).size == 3 }
    val pageStore = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 1, originalStroke) }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = "# Note"
      session.noteDraft.value = "# Note"
      session.noteWritable.value = true
      session.penStore.value = projectPenStore
      session.penPageStore.value = pageStore
      session.penStrokes.value = listOf(originalStroke)
      session.penWritable.value = true
      session.penTool.value = io.pagenote.feature.pen.PenCanvasTool.PEN
    }

    fun readerCommand(id: String): androidx.compose.ui.test.SemanticsNodeInteraction {
      revealReaderMenuCommand(composeRule, "handwriting", id)
      return composeRule.onNodeWithTag("reader_menu_command_$id")
    }

    try {
      composeRule.waitUntil(15_000) {
        session.pageIndex.intValue == 0 &&
          composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          appAnnotations.readPage("document", 1).size == 3 &&
          runCatching { composeRule.onNodeWithTag("reader_next_page").assertIsEnabled() }.isSuccess
      }
      composeRule.waitForIdle()
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      readerCommand("select_tool").performScrollTo().performClick()
      val canvas = composeRule.onNodeWithTag("pdf_ink_canvas")
      val nativeCanvasRef = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread {
        nativeCanvasRef.set(findPenCanvas(composeRule.activity.window.decorView) ?: error("ink canvas missing"))
      }
      val nativeCanvas = nativeCanvasRef.get()
      composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.SELECT }
      composeRule.waitForIdle()
      assertEquals("Select command must reach the native ink view before input", PenCanvasTool.SELECT, nativeCanvas.selectedTool)
      val pageItems = annotations.readPage("document", 1)
      val geometry = PenCanvasGeometry(nativeCanvas.width.toFloat(), nativeCanvas.height.toFloat(), nativeCanvas.pageTransform)
      // Injected touch coordinates are quantized to screen pixels; allow at most two pixels in document space.
      val injectionTolerance = geometry.documentUnitsForPixels(2f).toDouble().coerceAtLeast(0.05)
      fun pageOffset(x: Float, y: Float): Offset {
        val point = PenPoint(x, y, 1f)
        return Offset(geometry.toViewX(point), geometry.toViewY(point))
      }
      val tapOffset = pageOffset(250f, 100f)
      val existingPoint = requireNotNull(
        geometry.toDocumentPoint(tapOffset.x, tapOffset.y, pressure = null, clampToPage = false),
      )
      val rectIndex = pageItems.indexOfFirst { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" }
      val expectedHit = PdfAnnotationClipboard.hitTest(
        pageItems,
        existingPoint.x.toDouble(),
        existingPoint.y.toDouble(),
        geometry.documentUnitsForPixels(18f * context.resources.displayMetrics.density).toDouble(),
      )
      assertEquals(
        "Hard-coded canvas tap mapped to $existingPoint, geometry=${nativeCanvas.pageTransform}, hit=$expectedHit",
        rectIndex,
        expectedHit,
      )
      canvas.performTouchInput { click(tapOffset) }
      readerCommand("copy_ink").assertIsEnabled()

      val latestPage = annotations.readPage("document", 1)
      val latestLine = JsonObject((latestPage[1] as JsonObject) + ("concurrent" to JsonPrimitive("preserve")))
      annotations.writePage("document", 1, JsonArray(listOf(latestPage[0], latestLine, latestPage[2])))

      readerCommand("ink_color").performScrollTo().performClick()
      composeRule.onNodeWithTag("ink_color_swatch_red").performClick()
      composeRule.onNodeWithTag("ink_color_apply").performClick()
      waitForAnnotations(annotations) {
        val rect = it[0] as? JsonObject
        rect?.get("color")?.jsonPrimitive?.content == "#dc3545" &&
          rect["width"]?.jsonPrimitive?.content?.toDoubleOrNull() == 4.0
      }
      readerCommand("ink_width").performScrollTo().performClick()
      composeRule.onNodeWithTag("ink_width_10").performClick()
      composeRule.onNodeWithTag("ink_width_apply").performClick()
      waitForAnnotations(annotations) {
        val rect = it[0] as? JsonObject
        rect?.get("color")?.jsonPrimitive?.content == "#dc3545" &&
          rect["width"]?.jsonPrimitive?.content?.toDoubleOrNull() == 10.0
      }
      assertEquals("preserve", (annotations.readPage("document", 1)[0] as JsonObject)["custom"]?.jsonPrimitive?.content)
      assertEquals("#dc3545", (annotations.readPage("document", 1)[1] as JsonObject)["color"]?.jsonPrimitive?.content)
      assertEquals("preserve", (annotations.readPage("document", 1)[1] as JsonObject)["concurrent"]?.jsonPrimitive?.content)
      waitForEnabledCommand("reader_menu_command_pen_undo")
      readerCommand("pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        val rect = it[0] as? JsonObject
        rect?.get("color")?.jsonPrimitive?.content == "#dc3545" &&
          rect["width"]?.jsonPrimitive?.content?.toDoubleOrNull() == 4.0
      }
      waitForEnabledCommand("reader_menu_command_pen_undo")
      readerCommand("pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        val rect = it[0] as? JsonObject
        rect?.get("color")?.jsonPrimitive?.content == "#185abd" &&
          rect["width"]?.jsonPrimitive?.content?.toDoubleOrNull() == 4.0
      }
      waitForEnabledCommand("reader_menu_command_pen_redo")
      readerCommand("pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[0] as? JsonObject)?.get("color")?.jsonPrimitive?.content == "#dc3545"
      }
      waitForEnabledCommand("reader_menu_command_pen_redo")
      readerCommand("pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[0] as? JsonObject)?.get("width")?.jsonPrimitive?.content?.toDoubleOrNull() == 10.0
      }

      canvas.performTouchInput { click(pageOffset(760f, 860f)) }
      composeRule.waitUntil(10_000) {
        runCatching {
          readerCommand("copy_ink").assertIsEnabled()
        }.isSuccess
      }
      readerCommand("ink_color").performScrollTo().performClick()
      composeRule.onNodeWithTag("ink_color_swatch_green").performClick()
      composeRule.onNodeWithTag("ink_color_apply").performClick()
      composeRule.waitUntil(10_000) {
        projectPenStore.readPage(PenSide.DOCUMENT, 1).singleOrNull()?.let {
          it.color == "#2e7d32" && it.width == originalStroke.width
        } == true
      }
      readerCommand("ink_width").performScrollTo().performClick()
      composeRule.onNodeWithTag("ink_width_6").performClick()
      composeRule.onNodeWithTag("ink_width_apply").performClick()
      composeRule.waitUntil(10_000) {
        projectPenStore.readPage(PenSide.DOCUMENT, 1).singleOrNull()?.width == 6.0
      }
      waitForEnabledCommand("reader_menu_command_pen_undo")
      readerCommand("pen_undo").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        projectPenStore.readPage(PenSide.DOCUMENT, 1).singleOrNull()?.let {
          it.color == "#2e7d32" && it.width == originalStroke.width
        } == true
      }
      waitForEnabledCommand("reader_menu_command_pen_undo")
      readerCommand("pen_undo").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        projectPenStore.readPage(PenSide.DOCUMENT, 1) == listOf(originalStroke)
      }
      waitForEnabledCommand("reader_menu_command_pen_redo")
      readerCommand("pen_redo").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        projectPenStore.readPage(PenSide.DOCUMENT, 1).singleOrNull()?.color == "#2e7d32"
      }
      waitForEnabledCommand("reader_menu_command_pen_redo")
      readerCommand("pen_redo").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        projectPenStore.readPage(PenSide.DOCUMENT, 1).singleOrNull()?.let {
          it.color == "#2e7d32" && it.width == 6.0
        } == true
      }
      val restyledStroke = originalStroke.copy(color = "#2e7d32", width = 6.0)
      canvas.performTouchInput { click(pageOffset(250f, 100f)) }
      composeRule.waitUntil(10_000) {
        runCatching {
          readerCommand("copy_ink").assertIsEnabled()
        }.isSuccess
      }

      readerCommand("copy_ink").performScrollTo().performClick()

      val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
      val payload = clipboard.primaryClip!!.getItemAt(0).text.toString()
      assertTrue(payload.startsWith("PAGE_NOTES_ANNOTATION:\n"))
      assertEquals("rect", PdfAnnotationClipboard.decode(payload)?.get("type")?.toString()?.trim('"'))

      readerCommand("paste_ink").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 4 }
      var page = annotations.readPage("document", 1)
      val copied = page[3] as JsonObject
      assertEquals(listOf(80.0, 90.0, 380.0, 390.0), points(copied))
      assertEquals("preserve", copied["custom"]?.toString()?.trim('"'))

      canvas.performTouchInput {
        down(pageOffset(100f, 100f))
        moveTo(pageOffset(140f, 140f), delayMillis = 500)
        up()
      }
      readerCommand("copy_ink").performScrollTo().performClick()
      val movedPayload = clipboard.primaryClip!!.getItemAt(0).text.toString()
      assertPoints(
        listOf(120.0, 130.0, 420.0, 430.0),
        points(PdfAnnotationClipboard.decode(movedPayload)!!),
        injectionTolerance,
      )
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(120.0, 130.0, 420.0, 430.0), injectionTolerance) } == true
      }
      page = annotations.readPage("document", 1)
      assertPoints(listOf(120.0, 130.0, 420.0, 430.0), points(page[3] as JsonObject), injectionTolerance)

      readerCommand("pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points) == listOf(80.0, 90.0, 380.0, 390.0)
      }
      readerCommand("pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(120.0, 130.0, 420.0, 430.0), injectionTolerance) } == true
      }

      canvas.performTouchInput {
        down(pageOffset(425f, 435f))
        moveTo(pageOffset(460f, 470f), delayMillis = 500)
        up()
      }
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> actual.getOrNull(2)?.let { it > 430.0 } } == true
      }
      val resizedPage = annotations.readPage("document", 1)
      val resizedPoints = points(resizedPage[3] as JsonObject)
      readerCommand("pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(120.0, 130.0, 420.0, 430.0), injectionTolerance) } == true
      }
      readerCommand("pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) { (it[3] as? JsonObject)?.let(::points) == resizedPoints }
      composeRule.waitForIdle()
      Thread.sleep(600)
      val selectionScreenshot = java.io.File(context.getExternalFilesDir(null), "annotation-selection-redo.png")
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
        selectionScreenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
      }
      Thread.sleep(1500)

      readerCommand("delete_ink").performScrollTo().performClick()
      try {
        waitForAnnotations(annotations) { it.size == 3 }
      } catch (error: Throwable) {
        val actualPage = annotations.readPage("document", 1)
        throw AssertionError("After delete page=$actualPage status=${session.penStatus.value}", error)
      }
      assertEquals(listOf(restyledStroke), projectPenStore.readPage(PenSide.DOCUMENT, 1))
      readerCommand("pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 4 }
      assertEquals("line", (annotations.readPage("document", 1)[1] as JsonObject)["type"]?.toString()?.trim('"'))
      assertEquals(listOf(restyledStroke), projectPenStore.readPage(PenSide.DOCUMENT, 1))

      val missingImage = """
        {"type":"image","color":"#123456","width":2,"points":[[80,90],[180,190]],"url":"/attachments/12345678-1234-1234-1234-123456789abc.png"}
      """.trimIndent()
      clipboard.setPrimaryClip(ClipData.newPlainText("image annotation", PdfAnnotationClipboard.MARKER + missingImage))
      composeRule.waitForIdle()
      readerCommand("paste_ink").performScrollTo().assertIsNotEnabled()
      assertEquals(4, annotations.readPage("document", 1).size)

      clipboard.setPrimaryClip(
        ClipData.newHtmlText(
          "rich HTML",
          "Rich note",
          "<h2 onclick=\"run()\">Rich <b>note</b></h2><script>owned()</script>" +
            "<img src=\"https://example.invalid/remote.png\">",
        ),
      )
      composeRule.waitForIdle()
      readerCommand("paste_ink").performScrollTo().assertIsEnabled().performClick()
      waitForAnnotations(annotations) { it.size == 5 }
      val htmlItem = annotations.readPage("document", 1)[4] as JsonObject
      val safeHtml = htmlItem["html"]?.jsonPrimitive?.content.orEmpty()
      assertEquals("html", htmlItem["type"]?.jsonPrimitive?.content)
      assertTrue(safeHtml.contains("<b>note</b>"))
      assertTrue(!safeHtml.contains("script", ignoreCase = true))
      assertTrue(!safeHtml.contains("onclick", ignoreCase = true))
      assertTrue(!safeHtml.contains("example.invalid", ignoreCase = true))

      val desktopItem = JsonObject(htmlItem + ("custom" to JsonPrimitive("preserve")))
      clipboard.setPrimaryClip(
        ClipData.newPlainText("desktop HTML annotation", PdfAnnotationClipboard.encode(desktopItem)),
      )
      composeRule.waitForIdle()
      readerCommand("paste_ink").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 6 }
      assertEquals("preserve", (annotations.readPage("document", 1)[5] as JsonObject)["custom"]?.jsonPrimitive?.content)

      canvas.performTouchInput { click(pageOffset(100f, 100f)) }
      canvas.performTouchInput {
        down(pageOffset(100f, 100f))
        moveTo(pageOffset(150f, 150f), delayMillis = 500)
        up()
      }
      waitForAnnotations(annotations) {
        (it[5] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(130.0, 140.0, 630.0, 140.0 + points(htmlItem).last() - points(htmlItem)[1]), injectionTolerance) } == true
      }
      composeRule.waitUntil(10_000) {
        runCatching {
          readerCommand("copy_ink").assertIsEnabled()
        }.isSuccess
      }
      readerCommand("copy_ink").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
          ?.let(PdfAnnotationClipboard::decode)
        copied?.let(::points)?.let { pointsNear(it, listOf(130.0, 140.0, 630.0, 140.0 + points(htmlItem).last() - points(htmlItem)[1]), injectionTolerance) } == true
      }
      val copiedHtml = PdfAnnotationClipboard.decode(clipboard.primaryClip!!.getItemAt(0).text.toString())!!
      val copiedHtmlText = copiedHtml["html"]!!.jsonPrimitive.content
      assertTrue(!copiedHtmlText.contains("script", ignoreCase = true))
      assertTrue(!copiedHtmlText.contains("example.invalid", ignoreCase = true))
      readerCommand("delete_ink").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 5 }
      readerCommand("pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 6 }
      assertEquals("html", (annotations.readPage("document", 1)[5] as JsonObject)["type"]?.jsonPrimitive?.content)
      assertEquals(untouchedOtherPage, annotations.readPage("document", 2))
      assertEquals(listOf(restyledStroke), projectPenStore.readPage(PenSide.DOCUMENT, 1))
    } finally {
      composeRule.runOnUiThread {
        session.replaceRenderer(null)
        session.project.value = null
      }
    }
  }

  private fun waitForAnnotations(
    store: ProjectAnnotationStore,
    predicate: (JsonArray) -> Boolean,
  ) {
    var lastReadError: IOException? = null
    try {
      composeRule.waitUntil(10_000) {
        val page = try {
          store.readPage("document", 1)
        } catch (error: IOException) {
          lastReadError = error
          null
        }
        page?.let(predicate) == true
      }
    } catch (timeout: AssertionError) {
      lastReadError?.let(timeout::addSuppressed)
      throw timeout
    }
  }

  private fun waitForEnabledCommand(tag: String) {
    revealReaderMenuCommand(composeRule, "handwriting", tag.removePrefix("reader_menu_command_"))
    composeRule.waitUntil(10_000) {
      runCatching { composeRule.onNodeWithTag(tag).assertIsEnabled() }.isSuccess
    }
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

  private fun dispatchStylus(
    canvas: PenCanvasView,
    action: Int,
    x: Float,
    y: Float,
    pressure: Float,
    downTime: Long,
    eventTime: Long,
  ) {
    val properties = MotionEvent.PointerProperties().apply {
      id = 0
      toolType = MotionEvent.TOOL_TYPE_STYLUS
    }
    val coordinates = MotionEvent.PointerCoords().apply {
      this.x = x
      this.y = y
      this.pressure = pressure
      size = 0.1f
    }
    val event = MotionEvent.obtain(
      downTime,
      eventTime,
      action,
      1,
      arrayOf(properties),
      arrayOf(coordinates),
      0,
      0,
      1f,
      1f,
      0,
      0,
      InputDevice.SOURCE_STYLUS,
      0,
    )
    try {
      composeRule.runOnUiThread { canvas.dispatchTouchEvent(event) }
    } finally {
      event.recycle()
    }
  }

  private fun points(item: JsonObject): List<Double> = item["points"]?.jsonArray?.flatMap { point ->
    point.jsonArray.map { it.jsonPrimitive.doubleOrNull ?: error("invalid annotation coordinate") }
  }.orEmpty()

  private fun assertPoints(expected: List<Double>, actual: List<Double>, tolerance: Double) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEachIndexed { index, (expectedValue, actualValue) ->
      org.junit.Assert.assertEquals("point coordinate $index", expectedValue, actualValue, tolerance)
    }
  }

  private fun pointsNear(actual: List<Double>, expected: List<Double>, tolerance: Double): Boolean =
    actual.size == expected.size && actual.zip(expected).all { (a, e) -> kotlin.math.abs(a - e) <= tolerance }

  private fun installFixture(
    resolver: android.content.ContentResolver,
    projectId: String = "d".repeat(64),
  ) {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"$projectId","name":"Annotation clipboard fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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
    canvas.drawText("Annotation clipboard", 50f, 70f, Paint(Paint.ANTI_ALIAS_FLAG))
    document.finishPage(page)
    val secondPage = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 2).create())
    secondPage.canvas.drawColor(android.graphics.Color.WHITE)
    secondPage.canvas.drawText("Second page", 50f, 70f, Paint(Paint.ANTI_ALIAS_FLAG))
    document.finishPage(secondPage)
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
