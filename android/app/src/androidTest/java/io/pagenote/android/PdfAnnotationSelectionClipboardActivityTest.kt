package io.pagenote.android

import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import org.junit.runner.RunWith
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfAnnotationSelectionClipboardActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun endpointDwellPersistsNativeShapeAndUsesAnnotationUndoHistory() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
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
      composeRule.waitUntil(10_000) { projectPenStore.readPage(PenSide.DOCUMENT, 1).size == 1 }
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
      val savedPage = annotations.readPage("document", 1)
      assertEquals(listOf("pen", "line"), savedPage.map { (it as JsonObject)["type"]?.jsonPrimitive?.content })
      val line = savedPage.last() as JsonObject
      assertEquals("#dc3545", line["color"]?.jsonPrimitive?.content)
      assertEquals(3.0, line["width"]?.jsonPrimitive?.content?.toDouble())
      assertTrue("native vector shapes must not serialize freehand pressure samples", "pressures" !in line)
      assertEquals(listOf(savedFreehand), projectPenStore.readPage(PenSide.DOCUMENT, 1))
      assertEquals(otherPageBefore, annotations.readPage("document", 2))

      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 1 &&
        (it.singleOrNull() as? JsonObject)?.get("type")?.jsonPrimitive?.content == "pen"
      }
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 2 &&
        (it.lastOrNull() as? JsonObject)?.get("type")?.jsonPrimitive?.content == "line"
      }
      assertEquals(listOf(savedFreehand), projectPenStore.readPage(PenSide.DOCUMENT, 1))
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
    installFixture(resolver)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
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
    val pageStore = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 1, originalStroke) }
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
      session.penPageStore.value = pageStore
      session.penStrokes.value = listOf(originalStroke)
      session.penWritable.value = true
      session.penTool.value = io.pagenote.feature.pen.PenCanvasTool.PEN
    }

    try {
      composeRule.waitUntil(15_000) {
        composeRule.onNodeWithTag("pdf_ink_canvas").fetchSemanticsNode().size.width > 0 &&
          annotations.readPage("document", 1).size == 3
      }
      composeRule.onNodeWithTag("reader_menu_command_select_tool").performScrollTo().performClick()
      val canvas = composeRule.onNodeWithTag("pdf_ink_canvas")
      val nativeCanvasRef = AtomicReference<PenCanvasView>()
      composeRule.runOnUiThread {
        nativeCanvasRef.set(findPenCanvas(composeRule.activity.window.decorView) ?: error("ink canvas missing"))
      }
      val nativeCanvas = nativeCanvasRef.get()
      composeRule.waitUntil(5_000) { nativeCanvas.selectedTool == PenCanvasTool.SELECT }
      assertEquals("Select command must reach the native ink view before input", PenCanvasTool.SELECT, nativeCanvas.selectedTool)
      val geometry = PenCanvasGeometry(nativeCanvas.width.toFloat(), nativeCanvas.height.toFloat(), nativeCanvas.pageTransform)
      fun pageOffset(x: Float, y: Float): Offset {
        val point = PenPoint(x, y, 1f)
        return Offset(geometry.toViewX(point), geometry.toViewY(point))
      }
      val tapOffset = pageOffset(250f, 100f)
      val existingPoint = requireNotNull(
        geometry.toDocumentPoint(tapOffset.x, tapOffset.y, pressure = null, clampToPage = false),
      )
      val pageItems = annotations.readPage("document", 1)
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
      canvas.performTouchInput {
        click(tapOffset)
      }
      composeRule.waitUntil(10_000) {
        runCatching {
          composeRule.onNodeWithTag("reader_menu_command_copy_ink").assertIsEnabled()
        }.isSuccess
      }
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performScrollTo().performClick()

      val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
      val payload = clipboard.primaryClip!!.getItemAt(0).text.toString()
      assertTrue(payload.startsWith("PAGE_NOTES_ANNOTATION:\n"))
      assertEquals("rect", PdfAnnotationClipboard.decode(payload)?.get("type")?.toString()?.trim('"'))

      composeRule.onNodeWithTag("reader_menu_command_paste_ink").performScrollTo().performClick()
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
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performScrollTo().performClick()
      val movedPayload = clipboard.primaryClip!!.getItemAt(0).text.toString()
      assertPoints(
        listOf(120.0, 130.0, 420.0, 430.0),
        points(PdfAnnotationClipboard.decode(movedPayload)!!),
      )
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(120.0, 130.0, 420.0, 430.0)) } == true
      }
      page = annotations.readPage("document", 1)
      assertPoints(listOf(120.0, 130.0, 420.0, 430.0), points(page[3] as JsonObject))

      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points) == listOf(80.0, 90.0, 380.0, 390.0)
      }
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(120.0, 130.0, 420.0, 430.0)) } == true
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
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) {
        (it[3] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(120.0, 130.0, 420.0, 430.0)) } == true
      }
      composeRule.onNodeWithTag("reader_menu_command_pen_redo").performScrollTo().performClick()
      waitForAnnotations(annotations) { (it[3] as? JsonObject)?.let(::points) == resizedPoints }

      composeRule.onNodeWithTag("reader_menu_command_delete_ink").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 3 }
      assertEquals(listOf(originalStroke), projectPenStore.readPage(PenSide.DOCUMENT, 1))
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 4 }
      assertEquals("line", (annotations.readPage("document", 1)[1] as JsonObject)["type"]?.toString()?.trim('"'))
      assertEquals(listOf(originalStroke), projectPenStore.readPage(PenSide.DOCUMENT, 1))

      val missingImage = """
        {"type":"image","color":"#123456","width":2,"points":[[80,90],[180,190]],"url":"/attachments/12345678-1234-1234-1234-123456789abc.png"}
      """.trimIndent()
      clipboard.setPrimaryClip(ClipData.newPlainText("image annotation", PdfAnnotationClipboard.MARKER + missingImage))
      composeRule.waitForIdle()
      composeRule.onNodeWithTag("reader_menu_command_paste_ink").performScrollTo().assertIsNotEnabled()
      assertEquals(4, annotations.readPage("document", 1).size)

      clipboard.setPrimaryClip(
        ClipData.newHtmlText(
          "rich HTML",
          "Rich note",
          "<h2 onclick=\"run()\">Rich <b>note</b></h2><script>owned()</script>" +
            "<img src=\"https://example.invalid/remote.png\">",
        ),
      )
      composeRule.onNodeWithTag("reader_menu_command_paste_ink").performScrollTo().assertIsEnabled().performClick()
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
      composeRule.onNodeWithTag("reader_menu_command_paste_ink").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 6 }
      assertEquals("preserve", (annotations.readPage("document", 1)[5] as JsonObject)["custom"]?.jsonPrimitive?.content)

      canvas.performTouchInput { click(pageOffset(100f, 100f)) }
      canvas.performTouchInput {
        down(pageOffset(100f, 100f))
        moveTo(pageOffset(150f, 150f), delayMillis = 500)
        up()
      }
      waitForAnnotations(annotations) {
        (it[5] as? JsonObject)?.let(::points)?.let { actual -> pointsNear(actual, listOf(130.0, 140.0, 630.0, 140.0 + points(htmlItem).last() - points(htmlItem)[1])) } == true
      }
      composeRule.waitUntil(10_000) {
        runCatching {
          composeRule.onNodeWithTag("reader_menu_command_copy_ink").assertIsEnabled()
        }.isSuccess
      }
      composeRule.onNodeWithTag("reader_menu_command_copy_ink").performScrollTo().performClick()
      composeRule.waitUntil(10_000) {
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
          ?.let(PdfAnnotationClipboard::decode)
        copied?.let(::points)?.let { pointsNear(it, listOf(130.0, 140.0, 630.0, 140.0 + points(htmlItem).last() - points(htmlItem)[1])) } == true
      }
      val copiedHtml = PdfAnnotationClipboard.decode(clipboard.primaryClip!!.getItemAt(0).text.toString())!!
      val copiedHtmlText = copiedHtml["html"]!!.jsonPrimitive.content
      assertTrue(!copiedHtmlText.contains("script", ignoreCase = true))
      assertTrue(!copiedHtmlText.contains("example.invalid", ignoreCase = true))
      composeRule.onNodeWithTag("reader_menu_command_delete_ink").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 5 }
      composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo().performClick()
      waitForAnnotations(annotations) { it.size == 6 }
      assertEquals("html", (annotations.readPage("document", 1)[5] as JsonObject)["type"]?.jsonPrimitive?.content)
      assertEquals(untouchedOtherPage, annotations.readPage("document", 2))
      assertEquals(listOf(originalStroke), projectPenStore.readPage(PenSide.DOCUMENT, 1))
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

  private fun assertPoints(expected: List<Double>, actual: List<Double>) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEachIndexed { index, (expectedValue, actualValue) ->
      org.junit.Assert.assertEquals("point coordinate $index", expectedValue, actualValue, 0.05)
    }
  }

  private fun pointsNear(actual: List<Double>, expected: List<Double>): Boolean =
    actual.size == expected.size && actual.zip(expected).all { (a, e) -> kotlin.math.abs(a - e) <= 0.05 }

  private fun installFixture(resolver: android.content.ContentResolver) {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"d".repeat(64)}","name":"Annotation clipboard fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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
