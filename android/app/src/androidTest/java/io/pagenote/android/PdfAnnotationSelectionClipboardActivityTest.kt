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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import io.pagenote.feature.pen.PenClipboardCodec
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRecognizedShape
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfAnnotationSelectionClipboardActivityTest {
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

  @Test fun oneAreaEraseGestureAcrossFreehandAndNativeShapeIsOneUndoStep() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val original = Json.parseToJsonElement(
      """[{"type":"rect","color":"#185abd","width":5,"points":[[220,250],[780,750]]},""" +
        """{"type":"pen","color":"#185abd","width":5,"points":[[300,500],[700,500]]}]""",
    ) as JsonArray
    annotations.writePage("document", 1, original)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 1
      }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.runOnIdle { session.penTool.value = PenCanvasTool.ERASER }
      composeRule.waitUntil(5_000) { canvas.pageTransform != null && canvas.selectedTool == PenCanvasTool.ERASER }
      collapseReaderMenuAndWait()
      Thread.sleep(2_500) // Let any prior transient status clear before capturing the initial state.
      saveAnnotationEvidence("reference-area-erase-mixed-before.png")
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      val start = PenPoint(500f, 150f, 1f)
      val end = PenPoint(500f, 850f, 1f)
      val now = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), .5f, now, now)
      for (step in 1..14) {
        val y = start.y + (end.y - start.y) * (step / 14f)
        val point = PenPoint(500f, y, 1f)
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          now,
          now + step * 35,
        )
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(end), geometry.toViewY(end), .5f, now, now + 600)
      waitForAnnotations(annotations) { page ->
        page.none { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" } &&
          page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" } >= 3 &&
          session.penStrokes.value.size == page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" }
      }
      saveAnnotationEvidence("reference-area-erase-mixed-erased.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { page ->
        page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" } == 1 &&
          page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" } == 1 &&
          session.penStrokes.value.size == 1
      }
      val restored = annotations.readPage("document", 1).filterIsInstance<JsonObject>()
      assertEquals(original.first(), restored.single { it["type"]?.jsonPrimitive?.content == "rect" })
      assertPoints(listOf(300.0, 500.0, 700.0, 500.0), points(restored.single { it["type"]?.jsonPrimitive?.content == "pen" }), .01)
      assertEquals(2, session.penStrokes.value.single().points.size)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-area-erase-mixed-undo.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_redo")
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) { page ->
        page.none { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" } &&
          page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" } >= 3 &&
          session.penStrokes.value.size == page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" }
      }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-area-erase-mixed-redo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun areaEraserCutsNativeRectangleAnnotationAndUndoRestoresIt() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val original = Json.parseToJsonElement(
      """[{"type":"rect","color":"#185abd","width":5,"points":[[220,250],[780,750]]}]""",
    ) as JsonArray
    annotations.writePage("document", 1, original)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.isEmpty()
      }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.runOnIdle { session.penTool.value = PenCanvasTool.ERASER }
      composeRule.waitUntil(5_000) { canvas.pageTransform != null && canvas.selectedTool == PenCanvasTool.ERASER }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-area-erase-native-before.png")
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      val start = PenPoint(500f, 150f, 1f)
      val end = PenPoint(500f, 850f, 1f)
      val now = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), .5f, now, now)
      for (step in 1..14) {
        val y = start.y + (end.y - start.y) * (step / 14f)
        val point = PenPoint(500f, y, 1f)
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          now,
          now + step * 35,
        )
        if (step == 8) {
          Thread.sleep(250)
          composeRule.waitForIdle()
          saveAnnotationEvidence("reference-area-erase-native-erasing.png")
        }
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(end), geometry.toViewY(end), .5f, now, now + 600)
      waitForAnnotations(annotations) { page ->
        page.none { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" } &&
          page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" } >= 2 &&
          session.penStrokes.value.size == page.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen" }
      }
      composeRule.waitForIdle()
      Thread.sleep(500)
      val fragments = annotations.readPage("document", 1).filterIsInstance<JsonObject>()
        .filter { it["pagenotePolyline"]?.jsonPrimitive?.content == "true" }
      assertTrue(fragments.size >= 2)
      assertTrue(fragments.none { fragment ->
        points(fragment).chunked(2).zipWithNext().any { (a, b) ->
          a[1] == b[1] && (a[1] == 250.0 || a[1] == 750.0) &&
            a[0] < 500.0 && b[0] > 500.0
        }
      })
      saveAnnotationEvidence("reference-area-erase-native-after.png")
      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it == original }
      composeRule.waitForIdle()
      Thread.sleep(500)
      assertEquals(0, composeRule.onAllNodesWithText(context.getString(R.string.pen_annotation_saved)).fetchSemanticsNodes().size)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-area-erase-native-undo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

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
        put(MediaStore.Images.Media.DISPLAY_NAME, "${UUID.randomUUID()}-reference-polyline.png")
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

  @Test fun referenceCurvePersistsAndUndoesAsOneStroke() {
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
      val startX = canvas.width * .15f
      val endX = canvas.width * .8f
      val startY = canvas.height * .4f
      val controlY = startY - canvas.height * .45f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, startX, startY, .5f, time, time)
      for (step in 1..40) {
        val t = step / 40f
        val inverse = 1f - t
        val x = startX * inverse * inverse + ((startX + endX) / 2f) * 2f * inverse * t + endX * t * t
        val y = startY * inverse * inverse + controlY * 2f * inverse * t + startY * t * t
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x, y, .5f, time, time + step * 10)
      }
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, endX, startY, .5f, time, time + 1100)
      waitForAnnotations(annotations) { items -> items.size == 1 && (items[0] as JsonObject)[PenStroke.CURVE_FIELD]?.jsonPrimitive?.content == "true" }
      val saved = annotations.readPage("document", 1)[0] as JsonObject
      assertEquals("pen", saved["type"]?.jsonPrimitive?.content)
      assertEquals(33, saved["points"]!!.jsonArray.size)
      val target = InstrumentationRegistry.getInstrumentation().targetContext
      val screenshotUri = target.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${UUID.randomUUID()}-reference-curve.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PageNoteEvidence")
      })
      checkNotNull(screenshotUri)
      composeRule.waitForIdle()
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
        target.contentResolver.openOutputStream(screenshotUri).use { stream ->
          checkNotNull(stream)
          bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
      }
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceCurveLeastSquaresFallbackPersistsAndUndoesAsOneStroke() {
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
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-curve-fallback-before.png")

      // The vertical extent is slightly larger than the endpoint span. The
      // reference Bezier branch rejects this ratio, so GraphUtils falls back
      // to its degree-five least-squares fit.
      val startX = canvas.width * .3f
      val endX = canvas.width * .5f
      val startY = canvas.height * .8f
      val amplitude = canvas.width * .22f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, startX, startY, .5f, time, time)
      for (step in 1..40) {
        // TouchEventManager clears touchPoints on ACTION_DOWN and starts its
        // sampled path on the first qualifying MOVE. Keep that first sampled
        // point on the baseline so the chord equals the path's x extent.
        val x = startX + (endX - startX) * (step / 40f)
        val t = (step - 1) / 39f
        val y = startY - amplitude * 4f * t * (1f - t)
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x, y, .5f, time, time + step * 10)
      }
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, endX, startY, .5f, time, time + 1100)

      waitForAnnotations(annotations) { items ->
        items.size == 1 &&
          (items[0] as? JsonObject)?.get(PenStroke.CURVE_FIELD)?.jsonPrimitive?.content == "true"
      }
      val saved = annotations.readPage("document", 1).single() as JsonObject
      assertEquals("pen", saved["type"]?.jsonPrimitive?.content)
      assertTrue("least-squares fallback should retain the sampled path", saved["points"]!!.jsonArray.size > 33)
      saveAnnotationEvidence("reference-curve-fallback-saved.png")

      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-curve-fallback-undo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceClosedEllipsePersistsAsOneClosedPolylineAndUndoes() {
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
      saveAnnotationEvidence("reference-closed-ellipse-before.png")

      val centerX = canvas.width * .5f
      val centerY = canvas.height * .4f
      val radiusX = canvas.width * .16f
      val radiusY = canvas.width * .07f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, centerX + radiusX, centerY, .5f, time, time)
      for (step in 1..64) {
        val angle = step / 64.0 * 2.0 * Math.PI
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          centerX + kotlin.math.cos(angle).toFloat() * radiusX,
          centerY + kotlin.math.sin(angle).toFloat() * radiusY,
          .5f,
          time,
          time + step * 10,
        )
      }
      Thread.sleep(900)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, centerX + radiusX, centerY, .5f, time, time + 1000)
      waitForAnnotations(annotations) { items ->
        items.size == 1 && (items[0] as? JsonObject)?.get(PenStroke.POLYLINE_FIELD)?.jsonPrimitive?.content == "true"
      }
      val saved = annotations.readPage("document", 1).single() as JsonObject
      val points = points(saved)
      assertEquals("pen", saved["type"]?.jsonPrimitive?.content)
      assertTrue(points.size >= 20)
      assertEquals(points[0], points[points.lastIndex - 1], 1.0)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-closed-ellipse.png")

      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceClosedCirclePersistsAsCircleMaterialAndUndoes() {
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
      saveAnnotationEvidence("reference-closed-circle-before.png")

      val centerX = canvas.width * .5f
      val centerY = canvas.height * .4f
      val radius = minOf(canvas.width, canvas.height) * .10f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, centerX + radius, centerY, .5f, time, time)
      for (step in 1..64) {
        val angle = step / 64.0 * 2.0 * Math.PI
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          centerX + kotlin.math.cos(angle).toFloat() * radius,
          centerY + kotlin.math.sin(angle).toFloat() * radius,
          .5f,
          time,
          time + step * 10,
        )
      }
      Thread.sleep(900)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, centerX + radius, centerY, .5f, time, time + 1000)
      waitForAnnotations(annotations) { it.size == 1 }
      val saved = annotations.readPage("document", 1).single() as JsonObject
      assertEquals("ellipse", saved["type"]?.jsonPrimitive?.content)
      assertEquals("circle", saved["shape"]?.jsonPrimitive?.content)
      assertEquals(2, saved["points"]!!.jsonArray.size)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-closed-circle.png")

      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-closed-circle-undo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceClosedRectangleAndTrianglePersistAndUndoAsOneMaterialEach() {
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

      fun recognize(
        label: String,
        vertices: List<Pair<Float, Float>>,
        expectedType: String,
        expectedExtension: Pair<String, String>? = null,
      ) {
        saveAnnotationEvidence("reference-closed-$label-before.png")
        val path = vertices.zipWithNext().flatMapIndexed { edge, (start, end) ->
          (0 until 64).map { step ->
            val fraction = step / 64f
            (start.first + (end.first - start.first) * fraction) to
              (start.second + (end.second - start.second) * fraction)
          } + if (edge == vertices.lastIndex - 1) listOf(vertices.last()) else emptyList()
        }
        val downTime = SystemClock.uptimeMillis()
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, path.first().first, path.first().second, .5f, downTime, downTime)
        path.drop(1).forEachIndexed { index, point ->
          dispatchStylus(canvas, MotionEvent.ACTION_MOVE, point.first, point.second, .5f, downTime, downTime + (index + 1) * 10)
        }
        Thread.sleep(900)
        val last = path.last()
        dispatchStylus(canvas, MotionEvent.ACTION_UP, last.first, last.second, .5f, downTime, downTime + 1000)
        waitForAnnotations(annotations) { it.size == 1 }
        val saved = annotations.readPage("document", 1).single() as JsonObject
        assertEquals(expectedType, saved["type"]?.jsonPrimitive?.content)
        expectedExtension?.let { (field, value) -> assertEquals(value, saved[field]?.jsonPrimitive?.content) }
        if (expectedType == "pen") {
          val coordinates = points(saved)
          assertTrue(coordinates.size >= 8)
          assertEquals(coordinates.first(), coordinates[coordinates.lastIndex - 1], 1.0)
          assertEquals(coordinates[1], coordinates.last(), 1.0)
        } else {
          assertEquals(2, saved["points"]!!.jsonArray.size)
        }
        collapseReaderMenuAndWait()
        saveAnnotationEvidence("reference-closed-$label.png")
        revealReaderMenuCommand(composeRule, "pen", "pen_undo")
        composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
        waitForAnnotations(annotations) { it.isEmpty() }
        collapseReaderMenuAndWait()
        saveAnnotationEvidence("reference-closed-$label-undo.png")
        revealReaderMenuCommand(composeRule, "handwriting", "pen")
        composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.PEN }
      }

      recognize(
        label = "rectangle",
        vertices = listOf(
          canvas.width * .24f to canvas.height * .24f,
          canvas.width * .56f to canvas.height * .24f,
          canvas.width * .56f to canvas.height * .54f,
          canvas.width * .24f to canvas.height * .54f,
          canvas.width * .24f to canvas.height * .24f,
        ),
        expectedType = "rect",
      )
      recognize(
        label = "triangle",
        vertices = listOf(
          canvas.width * .22f to canvas.height * .64f,
          canvas.width * .50f to canvas.height * .27f,
          canvas.width * .78f to canvas.height * .64f,
          canvas.width * .22f to canvas.height * .64f,
        ),
        expectedType = "pen",
        expectedExtension = PenStroke.POLYLINE_FIELD to "true",
      )
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceRegionSelectionUsesFirstPenSampleAndUndoesMixedDeleteAsOneAction() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement(
      """[
        {"type":"pen","color":"#dc3545","width":3,"points":[[250,300],[350,300]],"pressures":[1,1]},
        {"type":"rect","color":"#008844","width":5,"points":[[430,320],[570,400]]},
        {"type":"pen","color":"#185abd","width":3,"points":[[100,350],[650,350],[750,350]],"pressures":[1,1,1]}
      ]""",
    ) as JsonArray
    val crossingStrokeStartsOutside = initial[2]
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 2
      }
      collapseReaderMenuAndWait()
      Thread.sleep(2_500) // Let any previous test's paste toast clear before capturing the initial state.
      saveAnnotationEvidence("reference-region-selection-before.png")
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }

      val lasso = listOf(
        PenPoint(180f, 280f, 1f),
        PenPoint(820f, 280f, 1f),
        PenPoint(820f, 420f, 1f),
        PenPoint(180f, 420f, 1f),
        PenPoint(180f, 280f, 1f),
      )
      val downTime = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(lasso.first()), geometry.toViewY(lasso.first()), .5f, downTime, downTime)
      lasso.drop(1).forEachIndexed { index, point ->
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          downTime,
          downTime + (index + 1) * 20,
        )
      }
      dispatchStylus(
        canvas,
        MotionEvent.ACTION_UP,
        geometry.toViewX(lasso.last()),
        geometry.toViewY(lasso.last()),
        .5f,
        downTime,
        downTime + 120,
      )
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-selection-selected.png")

      revealReaderMenuCommand(composeRule, "handwriting", "delete_ink")
      composeRule.onNodeWithTag("reader_menu_command_delete_ink").assertIsEnabled()
        .performScrollTo().performClick()
      waitForAnnotations(annotations) { it == JsonArray(listOf(crossingStrokeStartsOutside)) }
      composeRule.waitUntil(5_000) { session.penStrokes.value.size == 1 }
      assertEquals(1, session.penStrokes.value.size)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-selection-deleted.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled()
        .performScrollTo().performClick()
      waitForAnnotations(annotations) { it == initial }
      composeRule.waitUntil(5_000) { session.penStrokes.value.size == 2 }
      assertEquals(2, session.penStrokes.value.size)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-selection-undo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceRegionSelectionCopiesMixedMaterialsAndPastesAsOneUndoAction() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement(
      """[
        {"type":"pen","color":"#dc3545","width":3,"points":[[250,350],[350,350]],"pressures":[1,1]},
        {"type":"rect","color":"#008844","width":5,"points":[[430,320],[570,400]]},
        {"type":"highlight","color":"#185abd","width":3,"points":[[650,350],[750,350]],"pressures":[1,1]}
      ]""",
    ) as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 2
      }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }

      val lasso = listOf(
        PenPoint(180f, 280f, 1f),
        PenPoint(820f, 280f, 1f),
        PenPoint(820f, 420f, 1f),
        PenPoint(180f, 420f, 1f),
        PenPoint(180f, 280f, 1f),
      )
      val downTime = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(lasso.first()), geometry.toViewY(lasso.first()), .5f, downTime, downTime)
      lasso.drop(1).forEachIndexed { index, point ->
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          downTime,
          downTime + (index + 1) * 20,
        )
      }
      dispatchStylus(
        canvas,
        MotionEvent.ACTION_UP,
        geometry.toViewX(lasso.last()),
        geometry.toViewY(lasso.last()),
        .5f,
        downTime,
        downTime + 120,
      )
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-copy-selected.png")

      revealReaderMenuCommand(composeRule, "handwriting", "copy_ink")
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").assertIsEnabled().performScrollTo().performClick()
      val clipboard = composeRule.activity.getSystemService(android.content.ClipboardManager::class.java)
      val clip = checkNotNull(clipboard?.primaryClip)
      assertEquals(3, clip.itemCount)
      val copiedPayloads = (0 until clip.itemCount).map { clip.getItemAt(it).text.toString() }
      assertEquals(2, copiedPayloads.count { PenClipboardCodec.decode(it, PenPoint(0f, 0f, 1f)) != null })
      assertEquals(1, copiedPayloads.count { PdfAnnotationClipboard.decode(it)?.get("type")?.jsonPrimitive?.content == "rect" })
      assertEquals(
        listOf("pen", "rect", "highlight"),
        copiedPayloads.map { payload ->
          PenClipboardCodec.decode(payload, PenPoint(0f, 0f, 1f))?.type
            ?: PdfAnnotationClipboard.decode(payload)?.get("type")?.jsonPrimitive?.content
        },
      )

      revealReaderMenuCommand(composeRule, "handwriting", "paste_ink")
      composeRule.onNodeWithTag("reader_menu_command_paste_ink").assertIsEnabled().performScrollTo().performClick()
      try {
        waitForAnnotations(annotations) { it.size == 6 && session.penStrokes.value.size == 4 }
      } catch (error: Throwable) {
        throw AssertionError(
          "page=${annotations.readPage("document", 1)} strokes=${session.penStrokes.value.size} status=${session.penStatus.value}",
          error,
        )
      }
      val pastedSnapshot = annotations.readPage("document", 1)
      assertEquals(listOf(80.0, 120.0, 180.0, 120.0), points(pastedSnapshot[3] as JsonObject))
      assertEquals(listOf(260.0, 90.0, 400.0, 170.0), points(pastedSnapshot[4] as JsonObject))
      assertEquals(listOf(480.0, 120.0, 580.0, 120.0), points(pastedSnapshot[5] as JsonObject))
      collapseReaderMenuAndWait()
      Thread.sleep(4_000)
      saveAnnotationEvidence("reference-region-copy-pasted.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it == initial && session.penStrokes.value.size == 2 }
      revealReaderMenuCommand(composeRule, "handwriting", "pen_redo")
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 6 && session.penStrokes.value.size == 4 }
      assertEquals(pastedSnapshot, annotations.readPage("document", 1))
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceRegionSelectionMovesMixedMaterialsTogetherAndUndoesAsOneAction() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    InstrumentationRegistry.getInstrumentation().targetContext
      .getSystemService(android.content.ClipboardManager::class.java)
      ?.clearPrimaryClip()
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement(
      """[
        {"type":"pen","color":"#dc3545","width":3,"points":[[250,350],[350,350]],"pressures":[1,1]},
        {"type":"rect","color":"#008844","width":5,"points":[[430,320],[570,400]]},
        {"type":"highlight","color":"#185abd","width":3,"points":[[650,350],[750,350]],"pressures":[1,1]},
        {"type":"ellipse","color":"#777777","width":2,"points":[[1000,1000],[1100,1100]]}
      ]""",
    ) as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 2
      }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-move-before.png")
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }

      val lasso = listOf(
        PenPoint(180f, 280f, 1f),
        PenPoint(820f, 280f, 1f),
        PenPoint(820f, 420f, 1f),
        PenPoint(180f, 420f, 1f),
        PenPoint(180f, 280f, 1f),
      )
      var time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(lasso.first()), geometry.toViewY(lasso.first()), .5f, time, time)
      lasso.drop(1).forEachIndexed { index, point ->
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(point), geometry.toViewY(point), .5f, time, time + (index + 1) * 20)
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(lasso.last()), geometry.toViewY(lasso.last()), .5f, time, time + 120)
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-move-selected.png")

      val start = PenPoint(500f, 350f, 1f)
      val finish = PenPoint(600f, 430f, 1f)
      time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), .5f, time, time)
      for (step in 1..8) {
        val progress = step / 8f
        val point = PenPoint(
          start.x + (finish.x - start.x) * progress,
          start.y + (finish.y - start.y) * progress,
          1f,
        )
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(point), geometry.toViewY(point), .5f, time, time + step * 20)
      }
      composeRule.waitForIdle()
      assertEquals(
        "group drag should preview the selected freehand stroke before release; bounds=${canvas.selectedStrokeIndices} geometry=$geometry",
        350f,
        canvas.strokes.first().points.first().x,
        1f,
      )
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(finish), geometry.toViewY(finish), .5f, time, time + 200)
      val moved = Json.parseToJsonElement(
        """[
          {"type":"pen","color":"#dc3545","width":3,"points":[[350.0,430.0],[450.0,430.0]],"pressures":[1,1]},
          {"type":"rect","color":"#008844","width":5,"points":[[530.0,400.0],[670.0,480.0]]},
          {"type":"highlight","color":"#185abd","width":3,"points":[[750.0,430.0],[850.0,430.0]],"pressures":[1,1]},
          {"type":"ellipse","color":"#777777","width":2,"points":[[1000,1000],[1100,1100]]}
        ]""",
      ) as JsonArray
      val expectedMovedPoints = listOf(
        listOf(350.0, 430.0, 450.0, 430.0),
        listOf(530.0, 400.0, 670.0, 480.0),
        listOf(750.0, 430.0, 850.0, 430.0),
        listOf(1000.0, 1000.0, 1100.0, 1100.0),
      )
      fun movedPageMatches(page: JsonArray): Boolean {
        if (page.size != moved.size) return false
        val actualTypes = page.map { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content }
        val expectedTypes = moved.map { (it as JsonObject)["type"]!!.jsonPrimitive.content }
        if (actualTypes != expectedTypes) return false
        return page.indices.all { index ->
          val actual = points(page[index] as JsonObject)
          actual.size == expectedMovedPoints[index].size && actual.zip(expectedMovedPoints[index]).all { (value, expected) ->
            kotlin.math.abs(value - expected) <= 0.05
          }
        }
      }
      try {
        waitForAnnotations(annotations) {
          movedPageMatches(it) &&
            kotlin.math.abs(session.penStrokes.value.firstOrNull()?.points?.firstOrNull()?.x?.toDouble()?.minus(350.0) ?: 1_000.0) <= 0.05
        }
      } catch (error: Throwable) {
        throw AssertionError(
          "move expected=$moved actual=${annotations.readPage("document", 1)} liveStrokes=${session.penStrokes.value} status=${session.penStatus.value}",
          error,
        )
      }
      saveAnnotationEvidence("reference-region-move-committed.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) {
        it == initial && kotlin.math.abs(session.penStrokes.value.firstOrNull()?.points?.firstOrNull()?.x?.toDouble()?.minus(250.0) ?: 1_000.0) <= 0.05
      }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-move-undo.png")
      revealReaderMenuCommand(composeRule, "handwriting", "pen_redo")
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) {
        movedPageMatches(it) &&
          kotlin.math.abs(session.penStrokes.value.firstOrNull()?.points?.firstOrNull()?.x?.toDouble()?.minus(350.0) ?: 1_000.0) <= 0.05
      }
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-move-redo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceMixedSelectionChangesColorAndUndoesAsOneAction() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement(
      """[
        {"type":"pen","color":"#dc3545","width":3,"points":[[250,350],[350,350]],"pressures":[1,1]},
        {"type":"rect","color":"#008844","width":5,"points":[[430,320],[570,400]]},
        {"type":"highlight","color":"#185abd","width":3,"points":[[650,350],[750,350]],"pressures":[1,1]},
        {"type":"ellipse","color":"#777777","width":2,"points":[[1000,1000],[1100,1100]]}
      ]""",
    ) as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 2
      }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }

      val lasso = listOf(
        PenPoint(180f, 280f, 1f),
        PenPoint(820f, 280f, 1f),
        PenPoint(820f, 420f, 1f),
        PenPoint(180f, 420f, 1f),
        PenPoint(180f, 280f, 1f),
      )
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(lasso.first()), geometry.toViewY(lasso.first()), .5f, time, time)
      lasso.drop(1).forEachIndexed { index, point ->
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          time,
          time + (index + 1) * 20,
        )
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(lasso.last()), geometry.toViewY(lasso.last()), .5f, time, time + 120)
      composeRule.waitForIdle()
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-region-style-before.png")

      composeRule.onNodeWithTag("reader_pen_style").performClick()
      composeRule.onNodeWithTag("reader_pen_color_2e7d32").assertIsEnabled().performClick()
      val recolored = JsonArray(initial.mapIndexed { index, item ->
        if (index in 0..2) JsonObject((item as JsonObject) + ("color" to JsonPrimitive("#2e7d32"))) else item
      })
      waitForAnnotations(annotations) {
        it == recolored && session.penStrokes.value.map(PenStroke::color) == listOf("#2e7d32", "#2e7d32")
      }
      assertEquals(listOf("#2e7d32", "#2e7d32"), session.penStrokes.value.map(PenStroke::color))
      InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
      )
      composeRule.waitForIdle()
      saveAnnotationEvidence("reference-region-style-committed.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it == initial && session.penStrokes.value.map(PenStroke::color) == listOf("#dc3545", "#185abd") }
      collapseReaderMenuAndWait()
      composeRule.waitForIdle()
      saveAnnotationEvidence("reference-region-style-undo.png")

      revealReaderMenuCommand(composeRule, "handwriting", "pen_redo")
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it == recolored && session.penStrokes.value.all { stroke -> stroke.color == "#2e7d32" } }
      collapseReaderMenuAndWait()
      composeRule.waitForIdle()
      saveAnnotationEvidence("reference-region-style-redo.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test
  fun referenceRegionMovePreservesEarlierPenUndoHistory() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement(
      """[
        {"type":"pen","color":"#dc3545","width":3,"points":[[250,350],[350,350]],"pressures":[1,1]},
        {"type":"rect","color":"#008844","width":5,"points":[[430,320],[570,400]]},
        {"type":"highlight","color":"#185abd","width":3,"points":[[650,350],[750,350]],"pressures":[1,1]},
        {"type":"ellipse","color":"#777777","width":2,"points":[[1000,1000],[1100,1100]]}
      ]""",
    ) as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 2
      }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.PEN }

      val strayStart = PenPoint(900f, 600f, 1f)
      val strayEnd = PenPoint(960f, 650f, 1f)
      var time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(strayStart), geometry.toViewY(strayStart), .5f, time, time)
      for (step in 1..4) {
        val progress = step / 4f
        val point = PenPoint(
          strayStart.x + (strayEnd.x - strayStart.x) * progress,
          strayStart.y + (strayEnd.y - strayStart.y) * progress,
          1f,
        )
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          time,
          time + step * 20,
        )
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(strayEnd), geometry.toViewY(strayEnd), .5f, time, time + 100)
      waitForAnnotations(annotations) { it.size == initial.size + 1 }
      assertTrue("the newly drawn stroke should be independently undoable", session.penPageStore.value.canUndo(PenSide.DOCUMENT, 1))

      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }
      val lasso = listOf(
        PenPoint(180f, 280f, 1f), PenPoint(820f, 280f, 1f), PenPoint(820f, 420f, 1f),
        PenPoint(180f, 420f, 1f), PenPoint(180f, 280f, 1f),
      )
      time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(lasso.first()), geometry.toViewY(lasso.first()), .5f, time, time)
      lasso.drop(1).forEachIndexed { index, point ->
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(point), geometry.toViewY(point), .5f, time, time + (index + 1) * 20)
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(lasso.last()), geometry.toViewY(lasso.last()), .5f, time, time + 120)
      composeRule.waitForIdle()
      assertEquals("the lasso should exclude the newly appended stroke", setOf(0, 1), canvas.selectedStrokeIndices)

      val start = PenPoint(500f, 350f, 1f)
      val finish = PenPoint(600f, 430f, 1f)
      time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), .5f, time, time)
      for (step in 1..8) {
        val progress = step / 8f
        val point = PenPoint(
          start.x + (finish.x - start.x) * progress,
          start.y + (finish.y - start.y) * progress,
          1f,
        )
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_MOVE,
          geometry.toViewX(point),
          geometry.toViewY(point),
          .5f,
          time,
          time + step * 20,
        )
      }
      dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(finish), geometry.toViewY(finish), .5f, time, time + 200)
      try {
        waitForAnnotations(annotations) {
          val page = it.filterIsInstance<JsonObject>()
          page.size == initial.size + 1 &&
            kotlin.math.abs(points(page.first { item -> item["type"]?.jsonPrimitive?.content == "pen" })[0] - 350.0) <= .05
        }
      } catch (error: Throwable) {
        throw AssertionError(
          "page=${annotations.readPage("document", 1)} strokes=${session.penStrokes.value} " +
            "canUndo=${session.penPageStore.value.canUndo(PenSide.DOCUMENT, 1)} " +
            "selectedPens=${canvas.selectedStrokeIndices} status=${session.penStatus.value}",
          error,
        )
      }
      assertTrue("mixed movement must preserve the earlier ink undo entry", session.penPageStore.value.canUndo(PenSide.DOCUMENT, 1))

      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        it.size == initial.size + 1 && points(it.filterIsInstance<JsonObject>().first { item -> item["type"]?.jsonPrimitive?.content == "pen" })[0] == 250.0
      }
      composeRule.waitUntil(5_000) {
        session.penStrokes.value.size == 3 &&
          session.penStrokes.value.firstOrNull()?.points?.firstOrNull()?.x == 250f
      }
      assertTrue("undoing the mixed move must retain the previous pen undo entry", session.penPageStore.value.canUndo(PenSide.DOCUMENT, 1))
      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      try {
        waitForAnnotations(annotations) { it == initial && session.penStrokes.value.size == 2 }
      } catch (error: Throwable) {
        throw AssertionError(
          "expected=${initial} actual=${annotations.readPage("document", 1)} strokes=${session.penStrokes.value} " +
            "canUndo=${session.penPageStore.value.canUndo(PenSide.DOCUMENT, 1)} " +
            "canRedo=${session.penPageStore.value.canRedo(PenSide.DOCUMENT, 1)} status=${session.penStatus.value}",
          error,
        )
      }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test
  fun referenceMixedSelectionCommitsOnOwnerPointerUpAndRestoresOnCancel() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val initial = Json.parseToJsonElement(
      """[
        {"type":"pen","color":"#dc3545","width":3,"points":[[250,350],[350,350]],"pressures":[1,1]},
        {"type":"rect","color":"#008844","width":5,"points":[[430,320],[570,400]]},
        {"type":"highlight","color":"#185abd","width":3,"points":[[650,350],[750,350]],"pressures":[1,1]},
        {"type":"ellipse","color":"#777777","width":2,"points":[[1000,1000],[1100,1100]]}
      ]""",
    ) as JsonArray
    annotations.writePage("document", 1, initial)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          session.penStrokes.value.size == 2
      }
      val ref = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { ref.set(findPenCanvas(composeRule.activity.window.decorView)!!) }
      val canvas = ref.get()
      composeRule.waitUntil(10_000) { canvas.pageTransform != null }
      val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
      revealReaderMenuCommand(composeRule, "handwriting", "select_tool")
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { canvas.selectedTool == PenCanvasTool.SELECT }

      fun lasso(points: List<PenPoint>) {
        val downTime = SystemClock.uptimeMillis()
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(points.first()), geometry.toViewY(points.first()), .5f, downTime, downTime)
        points.drop(1).forEachIndexed { index, point ->
          dispatchStylus(
            canvas,
            MotionEvent.ACTION_MOVE,
            geometry.toViewX(point),
            geometry.toViewY(point),
            .5f,
            downTime,
            downTime + (index + 1) * 20,
          )
        }
        dispatchStylus(
          canvas,
          MotionEvent.ACTION_UP,
          geometry.toViewX(points.last()),
          geometry.toViewY(points.last()),
          .5f,
          downTime,
          downTime + points.size * 20L,
        )
        composeRule.waitForIdle()
      }

      lasso(listOf(
        PenPoint(180f, 280f, 1f), PenPoint(820f, 280f, 1f), PenPoint(820f, 420f, 1f),
        PenPoint(180f, 420f, 1f), PenPoint(180f, 280f, 1f),
      ))
      assertEquals(setOf(0, 1), canvas.selectedStrokeIndices)
      val start = PenPoint(500f, 350f, 1f)
      val finish = PenPoint(550f, 390f, 1f)
      val downTime = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), .5f, downTime, downTime)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(finish), geometry.toViewY(finish), .5f, downTime, downTime + 60)
      dispatchMultiplePointers(
        canvas,
        action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        pointers = listOf(
          TestPointer(0, MotionEvent.TOOL_TYPE_STYLUS, geometry.toViewX(finish), geometry.toViewY(finish), .5f),
          TestPointer(1, MotionEvent.TOOL_TYPE_FINGER, geometry.toViewX(PenPoint(900f, 900f, 1f)), geometry.toViewY(PenPoint(900f, 900f, 1f))),
        ),
        downTime = downTime,
        eventTime = downTime + 70,
      )
      dispatchMultiplePointers(
        canvas,
        action = MotionEvent.ACTION_POINTER_UP,
        pointers = listOf(
          TestPointer(0, MotionEvent.TOOL_TYPE_STYLUS, geometry.toViewX(finish), geometry.toViewY(finish), .5f),
          TestPointer(1, MotionEvent.TOOL_TYPE_FINGER, geometry.toViewX(PenPoint(900f, 900f, 1f)), geometry.toViewY(PenPoint(900f, 900f, 1f))),
        ),
        downTime = downTime,
        eventTime = downTime + 80,
      )
      try {
        waitForAnnotations(annotations) {
          val actual = points(it.filterIsInstance<JsonObject>().first { item -> item["type"]?.jsonPrimitive?.content == "pen" })
          val expected = listOf(300.0, 390.0, 400.0, 390.0)
          actual.size == expected.size && actual.zip(expected).all { (value, target) -> kotlin.math.abs(value - target) <= 0.05 }
        }
      } catch (error: Throwable) {
        throw AssertionError(
          "pointer-up move was not committed: page=${annotations.readPage("document", 1)} " +
            "liveStrokes=${session.penStrokes.value} selected=${canvas.selectedStrokeIndices} status=${session.penStatus.value}",
          error,
        )
      }

      lasso(listOf(
        PenPoint(230f, 320f, 1f), PenPoint(870f, 320f, 1f), PenPoint(870f, 460f, 1f),
        PenPoint(230f, 460f, 1f), PenPoint(230f, 320f, 1f),
      ))
      assertEquals(setOf(0, 1), canvas.selectedStrokeIndices)
      val cancelStart = PenPoint(550f, 400f, 1f)
      val cancelFinish = PenPoint(600f, 450f, 1f)
      val cancelDownTime = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(cancelStart), geometry.toViewY(cancelStart), .5f, cancelDownTime, cancelDownTime)
      dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(cancelFinish), geometry.toViewY(cancelFinish), .5f, cancelDownTime, cancelDownTime + 60)
      composeRule.waitForIdle()
      assertEquals(350f, canvas.strokes.first().points.first().x, .1f)
      composeRule.runOnUiThread { canvas.cancelActiveGesture() }
      composeRule.waitForIdle()
      assertEquals("cancel must restore the original live preview", 300f, canvas.strokes.first().points.first().x, .1f)
      assertEquals("cancel must not write the preview", 300.0, points(
        annotations.readPage("document", 1).filterIsInstance<JsonObject>().first { it["type"]?.jsonPrimitive?.content == "pen" },
      )[0], .05)
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
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-line.png")
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test fun referenceMinimumLengthLinePersistsAndUndoesAsNativeLine() {
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

      val startX = canvas.width * .15f
      val y = canvas.height * .35f
      val time = SystemClock.uptimeMillis()
      dispatchStylus(canvas, MotionEvent.ACTION_DOWN, startX, y, .5f, time, time)
      listOf(4f, 8f, 12f, 20f).forEachIndexed { index, offset ->
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, startX + offset, y, .5f, time, time + (index + 1) * 10)
      }
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, startX + 20f, y, .5f, time, time + 700)
      waitForAnnotations(annotations) {
        it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line"
      }
      val saved = annotations.readPage("document", 1).single() as JsonObject
      assertEquals("line", saved["type"]?.jsonPrimitive?.content)

      composeRule.waitForIdle()
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-short-line.png")
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      waitForAnnotations(annotations) { it.isEmpty() }
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test
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
        for (step in 1..12) {
          val progress = step / 12f
          dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x + (end - x) * progress, y, .5f, now, now + step * 10)
        }
        if (long) Thread.sleep(650)
        dispatchStylus(canvas, MotionEvent.ACTION_UP, end, y, .5f, now, now + if (long) 700 else 140)
      }
      fun types(): List<String> = annotations.readPage("document", 1).map { (it as JsonObject)["type"]!!.jsonPrimitive.content }.sorted()
      fun expect(vararg expected: String) {
        val desired = expected.sorted()
        try {
          composeRule.waitUntil(10_000) { runCatching { types() == desired }.getOrDefault(false) }
        } catch (error: Throwable) {
          throw AssertionError("expected=$desired actual=${runCatching { types() }.getOrNull()} status=${session.penStatus.value}", error)
        }
      }
      fun command(id: String) {
        revealReaderMenuCommand(composeRule, "handwriting", id)
        composeRule.onNodeWithTag("reader_menu_command_$id").performScrollTo().performClick()
      }
      stroke(canvas.height * .2f, true); expect("line")
      stroke(canvas.height * .5f, false); expect("line", "pen")
      stroke(canvas.height * .7f, true); expect("line", "line", "pen")
      composeRule.waitForIdle()
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-mixed-history-before.png")
      command("pen_undo"); expect("line", "pen")
      command("pen_undo"); expect("line")
      command("pen_undo"); expect()
      collapseReaderMenuAndWait()
      saveAnnotationEvidence("reference-mixed-history-undone.png")
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
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(10_000) { canvas.selectedTool == PenCanvasTool.PEN }
      fun stroke(y: Float, dwell: Boolean) {
        val time = SystemClock.uptimeMillis()
        val x = canvas.width * .2f
        val end = canvas.width * if (dwell) .75f else .26f
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, x, y, .5f, time, time)
        for (step in 1..12) {
          val progress = step / 12f
          dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x + (end - x) * progress, y, .5f, time, time + step * 10)
        }
        if (dwell) Thread.sleep(650)
        dispatchStylus(canvas, MotionEvent.ACTION_UP, end, y, .5f, time, time + if (dwell) 700 else 140)
      }
      stroke(canvas.height * .3f, true)
      waitForAnnotations(annotations, timeoutMillis = 20_000) {
        it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line"
      }
      runBlocking { session.penSaveMutex.lock() }; locked = true
      stroke(canvas.height * .7f, false)
      try {
        composeRule.waitUntil(15_000) { session.penStrokes.value.size == 1 }
      } catch (error: Throwable) {
        throw AssertionError(
          "queued-save stroke was not applied: strokes=${session.penStrokes.value} " +
            "page=${annotations.readPage("document", 1)} status=${session.penStatus.value}",
          error,
        )
      }
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
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(10_000) { canvas.selectedTool == PenCanvasTool.PEN }
      fun stroke(y: Float, dwell: Boolean) {
        val time = SystemClock.uptimeMillis()
        val x = canvas.width * .2f
        val end = canvas.width * if (dwell) .75f else .26f
        dispatchStylus(canvas, MotionEvent.ACTION_DOWN, x, y, .5f, time, time)
        for (step in 1..12) {
          val progress = step / 12f
          dispatchStylus(canvas, MotionEvent.ACTION_MOVE, x + (end - x) * progress, y, .5f, time, time + step * 10)
        }
        if (dwell) Thread.sleep(650)
        dispatchStylus(canvas, MotionEvent.ACTION_UP, end, y, .5f, time, time + if (dwell) 700 else 140)
      }
      runBlocking { session.penSaveMutex.lock() }; locked = true
      stroke(canvas.height * .3f, true)
      stroke(canvas.height * .7f, false)
      try {
        composeRule.waitUntil(15_000) { session.penStrokes.value.size == 1 }
      } catch (error: Throwable) {
        throw AssertionError(
          "queued-save stroke was not applied: strokes=${session.penStrokes.value} " +
            "page=${annotations.readPage("document", 1)} status=${session.penStatus.value}",
          error,
        )
      }
      session.penSaveMutex.unlock(); locked = false
      waitForAnnotations(annotations) { it.size == 2 }
      assertEquals(
        "expected native line followed by a newer freehand annotation",
        listOf("line", "pen"),
        annotations.readPage("document", 1).map { (it as JsonObject)["type"]?.jsonPrimitive?.content },
      )
      revealReaderMenuCommand(composeRule, "pen", "pen_undo")
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsEnabled().performScrollTo().performClick()
      try {
        waitForAnnotations(annotations) { it.size == 1 && (it[0] as JsonObject)["type"]?.jsonPrimitive?.content == "line" && session.penStrokes.value.isEmpty() }
      } catch (error: Exception) {
        throw AssertionError(
          "after latest-ink undo: page=${annotations.readPage("document", 1)} strokes=${session.penStrokes.value} status=${session.penStatus.value}",
          error,
        )
      }
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
      try {
        waitForAnnotations(annotations) { it == initial }
      } catch (error: Throwable) {
        throw AssertionError(
          "second undo expected=$initial actual=${annotations.readPage("document", 1)} strokes=${session.penStrokes.value} status=${session.penStatus.value}",
          error,
        )
      }
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
      for (step in 1..12) {
        val progress = step / 12f
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, startX + (endX - startX) * progress, y, 0.8f, downTime, downTime + step * 10)
      }
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
      revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
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
  fun triangleDwellShowsPreviewAndPersistsTheShape() {
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
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      if (composeRule.onAllNodesWithTag("reader_menu_collapse").fetchSemanticsNodes().isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_collapse").performClick()
      }
      val canvas = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { canvas.set(findPenCanvas(composeRule.activity.window.decorView)) }
      val view = checkNotNull(canvas.get())
      composeRule.waitUntil(10_000) { view.pageTransform != null }
      revealReaderMenuCommand(composeRule, "handwriting", "pen")
      composeRule.onNodeWithTag("reader_menu_command_pen").performScrollTo().performClick()
      composeRule.waitUntil(5_000) { view.selectedTool == PenCanvasTool.PEN }
      collapseReaderMenuAndWait()
      val vertices = listOf(
        view.width * .22f to view.height * .64f,
        view.width * .50f to view.height * .27f,
        view.width * .78f to view.height * .64f,
        view.width * .22f to view.height * .64f,
      )
      val path = vertices.zipWithNext().flatMapIndexed { edge, (first, second) ->
        (0 until 64).map { step ->
          val fraction = step / 64f
          (first.first + (second.first - first.first) * fraction) to
            (first.second + (second.second - first.second) * fraction)
        } + if (edge == vertices.lastIndex - 1) listOf(vertices.last()) else emptyList()
      }
      val down = SystemClock.uptimeMillis()
      dispatchStylus(view, MotionEvent.ACTION_DOWN, path.first().first, path.first().second, .5f, down, down)
      path.drop(1).forEachIndexed { index, point ->
        dispatchStylus(view, MotionEvent.ACTION_MOVE, point.first, point.second, .5f, down, down + (index + 1) * 10)
      }
      Thread.sleep(900)
      saveInkScreenshot("triangle-preview.png")
      val last = path.last()
      dispatchStylus(view, MotionEvent.ACTION_UP, last.first, last.second, .5f, down, down + path.size * 10 + 1000)
      waitForAnnotations(annotations) { items ->
        val item = items.singleOrNull() as? JsonObject ?: return@waitForAnnotations false
        item["type"]?.jsonPrimitive?.content == "pen" &&
          item[PenStroke.POLYLINE_FIELD]?.jsonPrimitive?.content == "true"
      }
      val item = annotations.readPage("document", 1).single() as JsonObject
      assertEquals("pen", item["type"]!!.jsonPrimitive.content)
      assertEquals("true", item[PenStroke.POLYLINE_FIELD]!!.jsonPrimitive.content)
      val coordinates = points(item)
      assertTrue("the stored triangle must retain its sampled polygon geometry", coordinates.size >= 8)
      assertEquals(coordinates.first(), coordinates[coordinates.lastIndex - 1], 1.0)
      assertEquals(coordinates[1], coordinates.last(), 1.0)
      val savedColor = android.graphics.Color.parseColor(item["color"]!!.jsonPrimitive.content)
      val expectedRed = android.graphics.Color.red(savedColor) / 255f
      val expectedGreen = android.graphics.Color.green(savedColor) / 255f
      val expectedBlue = android.graphics.Color.blue(savedColor) / 255f
      val geometry = PenCanvasGeometry(view.width.toFloat(), view.height.toFloat(), view.pageTransform)
      val polygon = coordinates.chunked(2).map { PenPoint(it[0].toFloat(), it[1].toFloat(), 1f) }
      composeRule.waitUntil(10_000) {
        runCatching {
          // Persisted polylines live in the native ink overlay inside the viewport.
          val pixels = composeRule.onNodeWithTag("pdf_page_viewport").captureToImage().toPixelMap()
          polygon.zipWithNext().any { (first, second) ->
            val midpoint = PenPoint((first.x + second.x) / 2f, (first.y + second.y) / 2f, 1f)
            val centerX = geometry.toViewX(midpoint).toInt()
            val centerY = geometry.toViewY(midpoint).toInt()
            (-5..5).any { dx -> (-5..5).any { dy ->
              val x = centerX + dx
              val y = centerY + dy
              if (x !in 0 until pixels.width || y !in 0 until pixels.height) false else {
                val color = pixels[x, y]
                color.alpha > .5f && kotlin.math.abs(color.red - expectedRed) < .12f &&
                  kotlin.math.abs(color.green - expectedGreen) < .12f && kotlin.math.abs(color.blue - expectedBlue) < .12f
              }
            } }
          }
        }.getOrDefault(false)
      }
      saveInkScreenshot("triangle-saved.png")
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
    }
  }

  @Test
  fun writingContinuesWhileRecognizedShapeIsSaving() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val annotations = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    annotations.writePage("document", 1, JsonArray(emptyList()))
    val store = ProjectPenStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnIdle {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.penStore.value = store
      session.penPageStore.value = PenPageStore()
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
      session.penTool.value = PenCanvasTool.PEN
    }
    try {
      composeRule.waitUntil(15_000) { composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 }
      val canvas = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread { canvas.set(findPenCanvas(composeRule.activity.window.decorView)) }
      val view = checkNotNull(canvas.get())
      resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_PEN_WRITE, null, null)
      val original = PenStroke("pen", "#185abd", 4.0, listOf(PenPoint(100f, 100f, 1f), PenPoint(400f, 100f, 1f)))
      composeRule.runOnUiThread {
        assertTrue(checkNotNull(view.onShapeRecognized).invoke(PenRecognizedShape("line", original.points.first(), original.points.last()), original))
      }
      composeRule.waitUntil(10_000) {
        resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_CHECK_PEN_WRITE, null, null)?.getBoolean("started") == true
      }
      composeRule.waitForIdle()
      assertTrue("saving a shape must not disable pen input", view.inputEnabled)
      val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
      assertTrue("the recognized shape should remain visible while its write is pending",
        (0 until pixels.width step 2).any { x -> (0 until pixels.height step 2).any { y ->
          val color = pixels[x, y]
          color.alpha > 0.5f && color.blue > 0.5f && color.red < 0.3f && color.green < 0.5f
        } })
      val down = SystemClock.uptimeMillis()
      dispatchStylus(view, MotionEvent.ACTION_DOWN, view.width * 0.2f, view.height * 0.4f, 0.5f, down, down)
      dispatchStylus(view, MotionEvent.ACTION_MOVE, view.width * 0.6f, view.height * 0.45f, 0.6f, down, down + 10)
      dispatchStylus(view, MotionEvent.ACTION_UP, view.width * 0.6f, view.height * 0.45f, 0.6f, down, down + 20)
      assertEquals(1, session.penStrokes.value.size)
    } finally {
      resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_RELEASE_PEN_WRITE, null, null)
    }
    try {
      composeRule.waitUntil(15_000) {
        val items = runCatching { annotations.readPage("document", 1) }.getOrNull() ?: return@waitUntil false
        items.size == 2
      }
      assertEquals(setOf("line", "pen"), annotations.readPage("document", 1).map { (it as JsonObject)["type"]!!.jsonPrimitive.content }.toSet())
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null); session.project.value = null }
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
      val startX = canvas.width * 0.15f
      val endX = canvas.width * 0.75f
      for (step in 1..12) {
        val progress = step / 12f
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, startX + (endX - startX) * progress, y, 0.8f, down, down + step * 10)
      }
      Thread.sleep(650)
      dispatchStylus(canvas, MotionEvent.ACTION_UP, endX, y, 0.6f, down, down + 700)
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
      for (step in 1..12) {
        val progress = step / 12f
        dispatchStylus(canvas, MotionEvent.ACTION_MOVE, startX + (endX - startX) * progress, y, 0.85f, downTime, downTime + step * 10)
      }
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
        val currentGeometry = PenCanvasGeometry(nativeCanvas.width.toFloat(), nativeCanvas.height.toFloat(), nativeCanvas.pageTransform)
        return Offset(currentGeometry.toViewX(point), currentGeometry.toViewY(point))
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

      // Use the HTML body interior so the drag is independent of overlapping outline edges.
      val htmlCenterY = ((points(htmlItem)[1] + points(htmlItem)[3]) / 2.0).toFloat()
      canvas.performTouchInput { click(pageOffset(330f, htmlCenterY)) }
      canvas.performTouchInput {
        down(pageOffset(330f, htmlCenterY))
        moveTo(pageOffset(380f, htmlCenterY + 50f), delayMillis = 500)
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
    timeoutMillis: Long = 10_000,
    predicate: (JsonArray) -> Boolean,
  ) {
    var lastReadError: IOException? = null
    try {
      composeRule.waitUntil(timeoutMillis) {
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
      timeout.addSuppressed(AssertionError("Synthetic annotation state: ${runCatching { store.readPage("document", 1) }.getOrNull()}"))
      throw timeout
    }
  }

  private fun waitForEnabledCommand(tag: String) {
    revealReaderMenuCommand(composeRule, "handwriting", tag.removePrefix("reader_menu_command_"))
    composeRule.waitUntil(10_000) {
      runCatching { composeRule.onNodeWithTag(tag).assertIsEnabled() }.isSuccess
    }
  }

  private fun collapseReaderMenuAndWait() {
    if (composeRule.onAllNodesWithTag("reader_menu_collapse").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("reader_menu_collapse").performClick()
    }
    composeRule.waitForIdle()
    Thread.sleep(700)
  }

  private fun saveAnnotationEvidence(name: String) {
    val target = InstrumentationRegistry.getInstrumentation().targetContext
    val uniqueName = "${UUID.randomUUID()}-$name"
    val uri = target.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, uniqueName)
      put(MediaStore.Images.Media.MIME_TYPE, "image/png")
      put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PageNoteEvidence")
    }) ?: error("MediaStore image insert failed for $uniqueName")
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap ->
      target.contentResolver.openOutputStream(uri).use { stream ->
        checkNotNull(stream)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
      }
      bitmap.recycle()
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

  private fun saveInkScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    composeRule.waitForIdle()
    instrumentation.uiAutomation.waitForIdle(300, 5_000)
    SystemClock.sleep(500) // Let the latest Compose frame reach SurfaceFlinger.
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    try {
      val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name)
      file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/pagenote-ink-evidence")).use { it.readBytes() }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/pagenote-ink-evidence/$name")).use { it.readBytes() }
    } finally { bitmap.recycle() }
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

  private data class TestPointer(
    val id: Int,
    val toolType: Int,
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
  )

  private fun dispatchMultiplePointers(
    canvas: PenCanvasView,
    action: Int,
    pointers: List<TestPointer>,
    downTime: Long,
    eventTime: Long,
  ) {
    val properties = pointers.map { pointer ->
      MotionEvent.PointerProperties().apply {
        id = pointer.id
        toolType = pointer.toolType
      }
    }.toTypedArray()
    val coordinates = pointers.map { pointer ->
      MotionEvent.PointerCoords().apply {
        x = pointer.x
        y = pointer.y
        pressure = pointer.pressure
        size = 0.1f
      }
    }.toTypedArray()
    val event = MotionEvent.obtain(
      downTime,
      eventTime,
      action,
      pointers.size,
      properties,
      coordinates,
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
