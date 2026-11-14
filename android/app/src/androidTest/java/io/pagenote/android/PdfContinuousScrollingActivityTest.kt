package io.pagenote.android

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.provider.MediaStore
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PdfContinuousScrollingActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun enableContinuousModeForEachTest() {
    runBlocking {
      val store = readerSettingsStore(composeRule.activity)
      store.save(store.settings.first().copy(continuousPdfScrolling = true))
    }
  }

  @Test
  fun continuousScrollPreservesOutgoingDraftAndLoadsTargetNote() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val projectName = installFixture(resolver)
    openReaderFixture(projectName)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]

    assertTrue(
      "default PDF reader should use the continuous list",
      composeRule.onAllNodesWithTag("pdf_continuous_page_list").fetchSemanticsNodes().isNotEmpty(),
    )
    composeRule.waitUntil(15_000) { session.pageIndex.intValue == 0 }
    captureEvidence("continuous-page-1")
    composeRule.runOnIdle { session.setNoteDraft("# Unsaved page 1 draft") }
    repeat(3) {
      composeRule.onNodeWithTag("pdf_continuous_page_list").performTouchInput { swipeUp() }
    }
    composeRule.waitUntil(15_000) {
      val activePage = session.pageIndex.intValue
      activePage >= 1 && session.noteDraft.value == NotePage.pageAt(
        session.notesMarkdown.value,
        activePage + 1,
      )
    }
    captureEvidence("continuous-active-page-note")

    assertTrue("scrolling to another page should update the reader checkpoint", session.saveCheckpoint()!!.pageIndex >= 1)
    repeat(4) {
      composeRule.onNodeWithTag("pdf_continuous_page_list").performTouchInput { swipeDown() }
    }
    composeRule.waitUntil(15_000) {
      session.pageIndex.intValue == 0 && session.noteDraft.value == "# Unsaved page 1 draft"
    }
    captureEvidence("continuous-page-1-restored-draft")
    assertTrue("page scrolling must not show a save success toast", session.noteStatus.value != composeRule.activity.getString(R.string.note_saved))
  }

  @Test
  fun continuousModeToggleAndCheckpointKeepActivePage() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val projectName = installFixture(resolver)
    openReaderFixture(projectName)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.onNodeWithTag("pdf_continuous_page_list").assertIsDisplayed()
    repeat(4) {
      composeRule.onNodeWithTag("pdf_continuous_page_list").performTouchInput { swipeUp() }
    }
    composeRule.waitUntil(15_000) { session.pageIndex.intValue >= 1 }
    val activePage = session.pageIndex.intValue

    revealReaderMenuCommand(composeRule, "settings", "settings_action")
    composeRule.onNodeWithTag("reader_menu_command_settings_action").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("reader_settings_page").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("settings_category_reading").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("continuous_pdf_scrolling_switch").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("continuous_pdf_scrolling_switch").performScrollTo()
    composeRule.onNodeWithTag("continuous_pdf_scrolling_switch").assertIsDisplayed()
    composeRule.onNodeWithTag("continuous_pdf_scrolling_switch").performClick()
    captureEvidence("setting-single-page")
    composeRule.onNodeWithTag("reader_settings_save").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("pdf_continuous_page_list").fetchSemanticsNodes().isEmpty()
    }
    collapseReaderMenuIfOpen()
    assertEquals("single-page mode should keep the current page", activePage, session.pageIndex.intValue)
    assertEquals("checkpoint should keep the current page", activePage, session.saveCheckpoint()?.pageIndex)
    captureEvidence("single-page-mode")

    revealReaderMenuCommand(composeRule, "settings", "settings_action")
    composeRule.onNodeWithTag("reader_menu_command_settings_action").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("reader_settings_page").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("settings_category_reading").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("continuous_pdf_scrolling_switch").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("continuous_pdf_scrolling_switch").performScrollTo()
    composeRule.onNodeWithTag("continuous_pdf_scrolling_switch").assertIsDisplayed()
    composeRule.onNodeWithTag("continuous_pdf_scrolling_switch").performClick()
    captureEvidence("setting-continuous")
    composeRule.onNodeWithTag("reader_settings_save").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("pdf_continuous_page_list").fetchSemanticsNodes().isNotEmpty()
    }
    collapseReaderMenuIfOpen()
    assertEquals(activePage, session.pageIndex.intValue)
    captureEvidence("continuous-mode-setting")
  }

  @Test
  fun continuousReaderSeparatesTouchScrollFromStylusAndPinch() {
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val projectName = installFixture(resolver)
    openReaderFixture(projectName)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    assertTrue(
      "continuous mode should expose a vertically scrollable PDF list",
      composeRule.onAllNodesWithTag("pdf_continuous_page_list").fetchSemanticsNodes().isNotEmpty(),
    )
    repeat(5) {
      composeRule.onNodeWithTag("pdf_continuous_page_list").performTouchInput { swipeUp() }
    }
    composeRule.waitUntil(15_000) { session.pageIndex.intValue >= 1 }
    val pageBeforePinch = session.pageIndex.intValue
    val project = requireNotNull(session.project.value)
    val canvasRef = AtomicReference<PenCanvasView>()
    composeRule.runOnUiThread {
      canvasRef.set(
        findPenCanvas(composeRule.activity.window.decorView) {
          it.contentIdentity == (project.sourceUri.toString() to pageBeforePinch)
        } ?: error("Continuous PDF ink canvas missing on page ${pageBeforePinch + 1}"),
      )
    }
    val canvas = canvasRef.get()
    composeRule.runOnIdle { session.penTool.value = PenCanvasTool.PEN }
    composeRule.waitUntil(5_000) { canvas.pageTransform != null && canvas.width > 0 && canvas.height > 0 }

    val geometry = PenCanvasGeometry(canvas.width.toFloat(), canvas.height.toFloat(), canvas.pageTransform)
    val start = PenPoint(200f, 200f, 1f)
    val end = PenPoint(500f, 260f, 1f)
    val now = SystemClock.uptimeMillis()
    dispatchStylus(canvas, MotionEvent.ACTION_DOWN, geometry.toViewX(start), geometry.toViewY(start), now, now)
    dispatchStylus(canvas, MotionEvent.ACTION_MOVE, geometry.toViewX(end), geometry.toViewY(end), now, now + 80)
    dispatchStylus(canvas, MotionEvent.ACTION_UP, geometry.toViewX(end), geometry.toViewY(end), now, now + 120)
    composeRule.waitUntil(10_000) {
      session.penPageStore.value.getItems(PenSide.DOCUMENT, pageBeforePinch + 1).isNotEmpty()
    }

    val scaleBeforePinch = session.viewportBookmark.value.scale
    composeRule.onNodeWithTag("pdf_continuous_page_$pageBeforePinch").performTouchInput {
      val midpoint = center
      down(0, midpoint.copy(x = midpoint.x - 32f))
      down(1, midpoint.copy(x = midpoint.x + 32f))
      moveTo(0, midpoint.copy(x = midpoint.x - 72f), delayMillis = 20)
      moveTo(1, midpoint.copy(x = midpoint.x + 72f), delayMillis = 20)
      up(1)
      up(0)
    }
    composeRule.waitUntil(10_000) { session.viewportBookmark.value.scale > scaleBeforePinch + 0.05f }
    captureEvidence("continuous-stylus-pinch")

    // Scrolling chooses the active page; stylus ink and pinch zoom remain bound to that page/session.
    assertTrue(session.penPageStore.value.getItems(PenSide.DOCUMENT, pageBeforePinch + 1).isNotEmpty())
    assertEquals(pageBeforePinch, session.saveCheckpoint()?.pageIndex)
  }

  private fun findPenCanvas(view: View, predicate: (PenCanvasView) -> Boolean = { true }): PenCanvasView? {
    if (view is PenCanvasView && predicate(view)) return view
    if (view is ViewGroup) {
      for (index in 0 until view.childCount) {
        findPenCanvas(view.getChildAt(index), predicate)?.let { return it }
      }
    }
    return null
  }

  private fun openReaderFixture(projectName: String) {
    openFixtureProjectFromBookshelf(composeRule, treeUri, projectName)
    collapseReaderMenuIfOpen()
  }

  private fun collapseReaderMenuIfOpen() {
    if (composeRule.onAllNodesWithTag("reader_menu_sidebar").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("reader_menu_collapse").performClick()
    }
  }

  private fun dispatchStylus(
    canvas: PenCanvasView,
    action: Int,
    x: Float,
    y: Float,
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
      pressure = .5f
      size = .1f
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

  private fun installFixture(resolver: ContentResolver): String {
    val projectName = "Continuous scroll fixture ${UUID.randomUUID()}"
    val projectId = UUID.randomUUID().toString().replace("-", "").repeat(2)
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"$projectId","name":"$projectName","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, pdfFixture())
        putByteArray(
          ExistingProjectSafTestProvider.EXTRA_NOTES,
          PageNotes.join(listOf("# Page 1 saved note", "# Page 2 saved note", "# Page 3 saved note"))
            .toByteArray(Charsets.UTF_8),
        )
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return projectName
  }

  private fun captureEvidence(name: String) {
    composeRule.waitForIdle()
    SystemClock.sleep(500)
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val image = resolver.insert(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PageNoteAcceptance")
      },
    ) ?: error("Could not create reader acceptance screenshot: $name")
    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    try {
      resolver.openOutputStream(image)?.use { output ->
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
      } ?: error("Could not write reader acceptance screenshot: $name")
    } catch (error: Exception) {
      resolver.delete(image, null, null)
      throw error
    } finally {
      bitmap.recycle()
    }
  }

  private fun pdfFixture(): ByteArray {
    val document = PdfDocument()
    return try {
      listOf(612 to 792, 792 to 612, 500 to 900).forEachIndexed { index, size ->
        val page = document.startPage(
          PdfDocument.PageInfo.Builder(size.first, size.second, index + 1).create(),
        )
        page.canvas.drawColor(android.graphics.Color.WHITE)
        page.canvas.drawText("Continuous page ${index + 1}", 36f, 64f, Paint(Paint.ANTI_ALIAS_FLAG))
        document.finishPage(page)
      }
      ByteArrayOutputStream().use { output ->
        document.writeTo(output)
        output.toByteArray()
      }
    } finally {
      document.close()
    }
  }

  private val treeUri: Uri
    get() = existingProjectTestTreeUri()
}
