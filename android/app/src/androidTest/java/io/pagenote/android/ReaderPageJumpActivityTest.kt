package io.pagenote.android

import android.graphics.Paint
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.NotePage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReaderPageJumpActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()
  private var previousLibrary: BookshelfPreferences? = null
  private var releaseFixtureGrant = false
  private var previousReaderSettings: ReaderSettings? = null

  @After fun restoreLibrary() = runBlocking {
    composeRule.runOnUiThread {
      val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
      if (session.project.value?.sourceUri?.authority == ExistingProjectSafTestProvider.AUTHORITY) {
        session.noteDraft.value = NotePage.pageAt(session.notesMarkdown.value, session.pageIndex.intValue + 1)
        session.clearCurrentDraftRecovery()
      }
    }
    previousReaderSettings?.let { readerSettingsStore(composeRule.activity).save(it) }
    previousLibrary?.let { BookshelfStore(composeRule.activity).bind(it.tree, it.name) }
    if (releaseFixtureGrant) composeRule.activity.contentResolver.releasePersistableUriPermission(
      treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    Unit
  }

  @Test
  fun missingAgentModelOpensSettingsPageAndReturnsToTheSameTask() {
    org.junit.Assume.assumeTrue(composeRule.activity.resources.configuration.screenWidthDp >= 600)
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    attachProjectToReader(project, notes)
    val store = agentModelSettingsStore(composeRule.activity.applicationContext)
    val current = store.loadForEditing()
    org.junit.Assume.assumeFalse("requires a test profile without an API key", current.hasApiKey)
    composeRule.onNodeWithTag("reader_agent_toggle").performClick()
    composeRule.onNodeWithTag("agent_task_instruction").performTextInput("Explain the current page")
    composeRule.onNodeWithTag("agent_task_run").performClick()
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("save_agent_model_settings").fetchSemanticsNodes().isNotEmpty() }
    composeRule.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    assertTrue(composeRule.onAllNodesWithTag("agent_task_drawer").fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithTag("settings_subpage_back").performClick()
    composeRule.onNodeWithTag("reader_settings_cancel").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onNodeWithTag("agent_task_drawer").isDisplayed()
    }
    composeRule.onNodeWithTag("agent_task_drawer").assertIsDisplayed()
    composeRule.onNodeWithTag("agent_task_instruction").assertTextContains("Explain the current page")
  }

  @Test
  fun agentRunsBehindReaderAndDoesNotOverwriteConcurrentEdits() = exerciseAgentWithConcurrentEdits(false)

  @Test
  fun editingWhileAgentWriteIsInFlightPreservesTheDraft() = exerciseAgentWithConcurrentEdits(true)

  private fun exerciseAgentWithConcurrentEdits(editDuringWrite: Boolean) {
    val settingsFile = File(composeRule.activity.noBackupFilesDir, "agent-model-settings.enc")
    org.junit.Assume.assumeFalse("test requires an unused Agent configuration", settingsFile.exists())
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    val requested = java.util.concurrent.CountDownLatch(1)
    val release = java.util.concurrent.CountDownLatch(1)
    val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
    server.soTimeout = 20_000
    val serverError = AtomicReference<Throwable?>()
    val worker = Thread {
      try {
        repeat(2) { index ->
          server.accept().use { socket ->
            val input = socket.getInputStream()
            val headers = ByteArrayOutputStream()
            var matched = 0
            val end = byteArrayOf(13, 10, 13, 10)
            while (matched < end.size) {
              val b = input.read()
              check(b >= 0)
              headers.write(b)
              matched = if (b == end[matched].toInt()) matched + 1 else if (b == 13) 1 else 0
            }
            val length = Regex("(?im)^Content-Length: ([0-9]+)")
              .find(headers.toString("UTF-8"))!!.groupValues[1].toInt()
            var remaining = length
            while (remaining > 0) {
              val consumed = input.read(ByteArray(minOf(remaining, 4096)))
              check(consumed > 0)
              remaining -= consumed
            }
            if (index == 0) {
              requested.countDown()
              check(release.await(45, java.util.concurrent.TimeUnit.SECONDS))
            }
            val reply = if (index == 0) {
              "TOOL write_note page=1 markdown=AgentResult expectedRevision=${PageNotes.revision("First page note")}"
            } else "DONE"
            val body = "{\"choices\":[{\"message\":{\"content\":\"$reply\"}}]}".toByteArray()
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            output.write(body)
            output.flush()
          }
        }
      } catch (error: Throwable) { serverError.set(error) }
    }
    try {
      agentModelSettingsStore(composeRule.activity.applicationContext).save(
        AgentModelSettingsDraft("http://127.0.0.1:${server.localPort}/v1", "test"),
        AgentKeyChange.Replace("test-key"),
      )
      worker.start()
      composeRule.onNodeWithTag("reader_agent_toggle").performClick()
      composeRule.onNodeWithTag("agent_task_instruction").performTextInput("Update this page")
      composeRule.onNodeWithTag("agent_task_run").performClick()
      assertTrue("model request should start", requested.await(15, java.util.concurrent.TimeUnit.SECONDS))
      composeRule.onNodeWithTag("agent_task_drawer").assertDoesNotExist()
      composeRule.onNodeWithTag("reader_agent_status", useUnmergedTree = true).assertExists()
      val screen = if (composeRule.activity.resources.configuration.screenWidthDp >= 600) "tablet" else "phone"
      saveUiScreenshot("agent-running-$screen.png")
      if (screen == "tablet") {
        revealReaderMenuCommand(composeRule, "format", "bold")
        composeRule.onNodeWithTag("reader_menu_command_bold").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("reader_menu_collapse").performClick()
      }
      composeRule.onNodeWithTag("reader_next_page").performClick()
      composeRule.waitUntil(10_000) { session.pageIndex.intValue == 1 }
      composeRule.onNodeWithTag("reader_previous_page").performClick()
      composeRule.waitUntil(10_000) { session.pageIndex.intValue == 0 }
      if (editDuringWrite) {
        pauseNextNoteWrite()
        release.countDown()
        assertTrue("agent should reach the paused note write", waitForPausedNoteWrite())
        composeRule.runOnUiThread { session.setNoteDraft("My new edits") }
        releaseNoteWrite()
      } else {
        composeRule.runOnUiThread {
          session.setNoteDraft("My new edits")
          release.countDown()
        }
      }
      composeRule.waitUntil(20_000) {
        worker.state == Thread.State.TERMINATED
      }
      serverError.get()?.let { throw AssertionError("fake model failed", it) }
      composeRule.waitUntil(10_000) { session.noteDraft.value == "My new edits" }
      composeRule.onNodeWithTag("reader_agent_toggle").performClick()
      composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag("agent_task_result").fetchSemanticsNodes().isNotEmpty()
      }
      assertEquals("My new edits", session.noteDraft.value)
      saveUiScreenshot("agent-result-$screen.png")
    } finally {
      release.countDown()
      if (editDuringWrite) releaseNoteWrite()
      server.close()
      worker.join(2000)
      settingsFile.delete()
    }
  }

  @Test
  fun settingsReturnToTheSameReaderPageAndDraft() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    editNativeNote("First page note", "Draft before opening settings")
    revealReaderMenuCommand(composeRule, "settings", "settings_action")
    composeRule.onNodeWithTag("reader_menu_command_settings_action").performScrollTo().performClick()
    composeRule.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_settings_cancel").performClick()
    composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, session.pageIndex.intValue)
    assertEquals("Draft before opening settings", session.noteDraft.value)
    assertTrue(composeRule.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun readerMenuShowsCommandsFromTheSelectedCategory() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    attachProjectToReader(project, notes)
    composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    saveUiScreenshot("reader-menu-groups.png")
    assertTrue(composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithTag("reader_menu_tab_file").performClick()
    composeRule.onNodeWithTag("reader_menu_command_open_project").assertIsDisplayed()
    saveUiScreenshot("reader-menu-expanded.png")
  }

  @Test
  fun rapidPinchWithInkKeepsTheExpectedZoomAndDoesNotAddStrokes() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes, createWorkspacePdf())
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val ink = (0 until 60).map { line ->
      PenStroke("pen", if (line % 2 == 0) "#185abd" else "#dc3545", 3.0,
        (0..30).map { point -> PenPoint(100f + point * 24f, 500f + line * 10f + kotlin.math.sin(point.toFloat()) * 4f, 0.7f) })
    }
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penWritable.value = true
      session.penStrokes.value = ink
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    if (composeRule.onAllNodesWithTag("reader_menu_collapse").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("reader_menu_collapse").performClick()
    }
    saveUiScreenshot("pinch-start.png")
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      val radius = width * 0.12f
      down(0, center.copy(x = center.x - radius))
      down(1, center.copy(x = center.x + radius))
      repeat(12) { i ->
        val nextRadius = radius * (1f + (i + 1) / 12f)
        moveTo(0, center.copy(x = center.x - nextRadius), delayMillis = 1)
        moveTo(1, center.copy(x = center.x + nextRadius), delayMillis = 1)
      }
      up(0)
      up(1)
    }
    composeRule.waitForIdle()
    saveUiScreenshot("pinch-zoomed.png")
    assertEquals(2f, session.viewportBookmark.value.scale, 0.03f)
    assertEquals(ink, session.penStrokes.value)
    assertEquals(0, session.pageIndex.intValue)
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      val radius = width * 0.24f
      down(0, center.copy(x = center.x - radius))
      down(1, center.copy(x = center.x + radius))
      repeat(12) { i ->
        val nextRadius = radius * (1f - (i + 1) / 24f)
        moveTo(0, center.copy(x = center.x - nextRadius), delayMillis = 1)
        moveTo(1, center.copy(x = center.x + nextRadius), delayMillis = 1)
      }
      up(0)
      up(1)
    }
    composeRule.waitForIdle()
    assertEquals(1f, session.viewportBookmark.value.scale, 0.03f)
    assertEquals(ink, session.penStrokes.value)
  }

  @Test
  fun nativePdfShowsFloatingPenBoxAndRealThumbnailDrawer() {
    InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0).edit().clear().commit()
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penWritable.value = true
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    composeRule.onNodeWithTag("floating_pen_box").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_pen_ballpoint").performClick()
    composeRule.runOnIdle { assertEquals(io.pagenote.feature.pen.PenCanvasTool.BALLPOINT, session.penTool.value) }
    saveUiScreenshot("reader-floating-pen-box.png")
    composeRule.onNodeWithTag("reader_pen_eraser").performClick()
    composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { down(center) }
    saveUiScreenshot("reader-eraser-boundary.png")
    composeRule.onNodeWithTag("pdf_ink_canvas").performTouchInput { up() }
    composeRule.onNodeWithTag("reader_page_status").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("page_preview_drawer").assertIsDisplayed()
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("page_preview_bitmap_0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    saveUiScreenshot("reader-page-previews.png")
    composeRule.onNodeWithTag("page_preview_item_1").performScrollTo().performClick()
    composeRule.waitUntil(10_000) { session.pageIndex.intValue == 1 }
  }

  private fun saveUiScreenshot(name: String) {
    composeRule.waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.waitForIdle(300, 5_000)
    // Semantics may settle before SurfaceFlinger presents the updated frame.
    android.os.SystemClock.sleep(600)
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    try {
      val outputDir = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
      val file = File(outputDir, name)
      file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/pagenote-ui-evidence")).use { it.readBytes() }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/pagenote-ui-evidence/$name")).use { it.readBytes() }
    } finally { bitmap.recycle() }
  }

  @Test
  fun pageStatusValidatesAndJumpsAfterSavingDirtyPage() {
    val savedPages = listOf("First page note", "Second page note", "Third page note")
    val savedNotes = PageNotes.join(savedPages)
    val editedFirstPage = "First page note with an unsaved edit"
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    val targetStroke = PenStroke(
      type = "pen",
      color = "#185abd",
      width = 2.0,
      points = listOf(PenPoint(x = 120f, y = 240f, pressure = 0.8f)),
    )
    val penPageStore = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 3, targetStroke) }
    val nonDefaultBookmark = PdfViewportBookmark(scale = 2.0f, centerX = 0.25f, centerY = 0.75f)
    composeRule.runOnUiThread {
      session.penPageStore.value = penPageStore
      session.viewportBookmark.value = nonDefaultBookmark
    }
    editNativeNote("First page note", editedFirstPage)

    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("1 / 3").performClick()
    composeRule.onNodeWithTag("page_preview_drawer").assertIsDisplayed()

    enterPageNumber("4")
    assertTrue(composeRule.onAllNodesWithTag("page_preview_item_2").fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithTag("page_preview_invalid").assertIsDisplayed()

    enterPageNumber("3")
    composeRule.onNodeWithTag("page_preview_item_2").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 2 }

    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("3 / 3")
    val savedNotesAfterJump = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(editedFirstPage, NotePage.pageAt(savedNotesAfterJump, 1))
    assertEquals("Second page note", NotePage.pageAt(savedNotesAfterJump, 2))
    assertEquals("Third page note", session.noteDraft.value)
    assertEquals(PageNotes.revision("Third page note"), session.noteBaseRevision.value)
    assertEquals(listOf(targetStroke), session.penStrokes.value)
    assertEquals(PdfViewportBookmark.DEFAULT, session.viewportBookmark.value)
    assertTrue(composeRule.onAllNodesWithTag("page_preview_drawer").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun failedInkSaveKeepsRetryAndOtherErrorsVisible() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val session = attachProjectToReader(installProject(notes), notes)
    try {
      composeRule.runOnIdle {
        session.failedPenSaves[1] = listOf(PenStroke("pen", "#185abd", 2.0, listOf(PenPoint(120f, 240f, 0.8f))))
        session.failedPenSaveMessages[1] = composeRule.activity.getString(R.string.pen_not_saved, "storage full")
        session.penStatus.value = "OCR failed"
        session.noteStatus.value = composeRule.activity.getString(R.string.note_conflict)
      }
      composeRule.onNodeWithTag("reader_pen_save_retry").assertIsDisplayed().assertIsEnabled()
      composeRule.onNodeWithText("OCR failed").assertIsDisplayed()
      composeRule.onNodeWithText(composeRule.activity.getString(R.string.pen_not_saved, "storage full")).assertIsDisplayed()
      composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_conflict)).assertIsDisplayed()
      saveUiScreenshot("reader-status-error.png")
      composeRule.onNodeWithTag("reader_pen_save_retry").performClick()
      composeRule.waitUntil(10_000) { session.failedPenSaves.isEmpty() }
      composeRule.runOnIdle { assertTrue(session.failedPenSaveMessages.isEmpty()) }
      composeRule.onNodeWithText("OCR failed").assertIsDisplayed()
      composeRule.onNodeWithText(composeRule.activity.getString(R.string.note_conflict)).assertIsDisplayed()
      composeRule.runOnIdle { session.noteStatus.value = null }
      composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.note_conflict)).fetchSemanticsNodes().isEmpty() }
      saveUiScreenshot("reader-status-single.png")
      composeRule.runOnIdle {
        session.failedPenSaves[1] = listOf(PenStroke("pen", "#185abd", 2.0, listOf(PenPoint(120f, 240f, 0.8f))))
        session.penStatus.value = null
      }
      composeRule.onNodeWithTag("reader_pen_save_retry").assertIsDisplayed()
      val savedError = composeRule.activity.getString(R.string.pen_not_saved, "storage full")
      composeRule.runOnIdle { session.penStatus.value = savedError }
      composeRule.onNodeWithText(savedError).assertIsDisplayed()
      composeRule.onNodeWithTag("reader_next_page").assertIsNotEnabled()
    } finally { composeRule.runOnIdle { session.failedPenSaves.clear(); session.failedPenSaveMessages.clear(); session.penStatus.value = null; session.noteStatus.value = null } }
  }

  @Test
  fun queuedInkWritesStayOnTheirPagesAcrossNavigation() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val store = ProjectPenStore(testResolver, project.sourceDirectoryUri, project.annotationsUri)
    val initialSecondPage = store.readPage(PenSide.DOCUMENT, 2)
    composeRule.runOnIdle {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penStore.value = store
      session.penWritable.value = true
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    val locked = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = Thread {
      runBlocking {
        session.penSaveMutex.lock()
        try { locked.countDown(); release.await(10, TimeUnit.SECONDS) } finally { session.penSaveMutex.unlock() }
      }
    }
    holder.start()
    assertTrue(locked.await(5, TimeUnit.SECONDS))
    val first = PenStroke("pen", "#185abd", 2.0, listOf(PenPoint(120f, 240f, 0.8f)))
    val second = PenStroke("pen", "#185abd", 2.0, listOf(PenPoint(200f, 300f, 0.8f)))
    try {
      composeRule.runOnIdle { requireNotNull(findPenCanvas(composeRule.activity.window.decorView)?.onStrokeAdded).invoke(first) }
      composeRule.waitUntil(5_000) { session.pendingPenSaves.intValue == 1 }
      composeRule.onNodeWithTag("reader_next_page").performClick()
      composeRule.waitUntil(5_000) { session.pageIndex.intValue == 1 }
      composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
      composeRule.runOnIdle { requireNotNull(findPenCanvas(composeRule.activity.window.decorView)?.onStrokeAdded).invoke(second) }
      composeRule.waitUntil(5_000) { session.pendingPenSaves.intValue == 2 }
    } finally {
      release.countDown()
      holder.join(5_000)
    }
    composeRule.waitUntil(15_000) { session.pendingPenSaves.intValue == 0 }
    assertEquals(listOf(first), store.readPage(PenSide.DOCUMENT, 1))
    assertEquals(initialSecondPage + second, store.readPage(PenSide.DOCUMENT, 2))
  }

  @Test
  fun canTurnPageWhileInkSaveIsPending() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val session = attachProjectToReader(installProject(notes), notes)
    try {
      composeRule.runOnIdle { session.pendingPenSaves.intValue = 1 }
      composeRule.onNodeWithTag("reader_next_page").assertIsEnabled().performClick()
      composeRule.waitUntil(5_000) { session.pageIndex.intValue == 1 }
    } finally {
      composeRule.runOnIdle { session.pendingPenSaves.intValue = 0 }
    }
  }

  @Test
  fun canNavigateAgainWhilePreviousPdfPageIsRendering() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    composeRule.runOnIdle { session.markdownSource.value = null; session.replaceRenderer(renderer) }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    val locked = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = Thread { synchronized(renderer) { locked.countDown(); release.await(10, TimeUnit.SECONDS) } }
    holder.start()
    assertTrue(locked.await(5, TimeUnit.SECONDS))
    try {
      composeRule.onNodeWithTag("reader_next_page").performClick()
      composeRule.waitUntil(5_000) { session.pageIndex.intValue == 1 }
      composeRule.onNodeWithTag("reader_next_page").assertIsEnabled().performClick()
      composeRule.waitUntil(5_000) { session.pageIndex.intValue == 2 }
      composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
        val start = center.copy(y = center.y - height * 0.35f)
        val end = center.copy(y = center.y + height * 0.35f)
        down(0, start); moveTo(0, end, delayMillis = 60); up(0)
      }
      composeRule.waitUntil(5_000) { session.pageIndex.intValue == 1 }
      composeRule.onNodeWithTag("reader_next_page").performClick()
      composeRule.waitUntil(5_000) { session.pageIndex.intValue == 2 }
    } finally {
      release.countDown()
      holder.join(5_000)
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("3 / 3")
  }

  @Test
  fun adjacentPageButtonsNavigateAndRespectDocumentBoundaries() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)

    composeRule.onNodeWithTag("reader_previous_page").assertIsNotEnabled()
    composeRule.onNodeWithTag("reader_next_page").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 1 }
    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("2 / 3")

    composeRule.onNodeWithTag("reader_previous_page").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 0 }
    composeRule.onNodeWithTag("reader_previous_page").assertIsNotEnabled()
  }

  @Test
  fun verticalSwipesAndAdjacentButtonsNavigateTheSamePdfReaderPage() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes, createThreePagePdf())
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.pageIndex.intValue = 0
      session.penTool.value = io.pagenote.feature.pen.PenCanvasTool.PEN
    }

    try {
      composeRule.waitUntil(timeoutMillis = 15_000) {
        session.pageIndex.intValue == 0 &&
          composeRule.onAllNodesWithTag("pdf_page_viewport").fetchSemanticsNodes().isNotEmpty() &&
          runCatching { composeRule.onNodeWithTag("reader_next_page").assertIsEnabled() }.isSuccess
      }
      composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
        val start = center.copy(y = center.y + height * 0.35f)
        val end = center.copy(y = center.y - height * 0.35f)
        down(0, start)
        moveTo(0, end, delayMillis = 60)
        up(0)
      }
      composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 1 }
      composeRule.onNodeWithTag("reader_page_status").assertTextEquals("2 / 3")

      composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
        val start = center.copy(y = center.y - height * 0.35f)
        val end = center.copy(y = center.y + height * 0.35f)
        down(0, start)
        moveTo(0, end, delayMillis = 60)
        up(0)
      }
      composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 0 }
      composeRule.onNodeWithTag("reader_page_status").assertTextEquals("1 / 3")

      composeRule.onNodeWithTag("reader_next_page").assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 1 }
      composeRule.onNodeWithTag("reader_previous_page").assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 0 }
      composeRule.onNodeWithTag("reader_previous_page").assertIsNotEnabled()
    } finally {
      composeRule.runOnUiThread { session.replaceRenderer(null) }
    }
  }

  @Test
  fun revisionConflictKeepsCurrentPageDraftAndJumpDialogOpen() {
    val savedNotes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val editedFirstPage = "First page note with a local edit"
    val externalNotes = PageNotes.join(listOf("First page note from another editor", "Second page note", "Third page note"))
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    editNativeNote("First page note", editedFirstPage)
    replaceNotes(externalNotes)
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())

    composeRule.onNodeWithTag("reader_page_status").assertIsEnabled().performClick()
    enterPageNumber("3")
    composeRule.onNodeWithTag("page_preview_item_2").assertIsEnabled().performClick()

    val expectedConflict = PageNoteConflictException().message
    try {
      composeRule.waitUntil(timeoutMillis = 10_000) { session.noteStatus.value == expectedConflict }
    } catch (timeout: Throwable) {
      throw AssertionError(
        "Expected page conflict status; got ${session.noteStatus.value}, page ${session.pageIndex.intValue}, draft ${session.noteDraft.value}",
        timeout,
      )
    }
    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("1 / 3")
    composeRule.onNodeWithTag("page_preview_drawer").assertIsDisplayed()
    assertEquals(0, session.pageIndex.intValue)
    assertEquals(editedFirstPage, session.noteDraft.value)
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())
  }

  @Test
  fun pendingDirtySaveDisablesRepeatJumpInputAndDismissal() {
    val savedNotes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    editNativeNote("First page note", "First page note with a pending edit")
    pauseNextNoteWrite()

    composeRule.onNodeWithTag("reader_page_status").performClick()
    enterPageNumber("3")
    composeRule.onNodeWithTag("page_preview_item_2").assertIsEnabled().performClick()
    try {
      assertTrue("the note save should reach the paused SAF write", waitForPausedNoteWrite())
      composeRule.onNodeWithTag("page_preview_search").assertIsNotEnabled()
      composeRule.onNodeWithTag("page_preview_item_2").assertIsNotEnabled()
      composeRule.onNodeWithTag("page_preview_close").assertIsNotEnabled()
      assertEquals(0, session.pageIndex.intValue)
    } finally {
      releaseNoteWrite()
    }

    composeRule.waitUntil(timeoutMillis = 10_000) { session.pageIndex.intValue == 2 }
    assertEquals("Third page note", session.noteDraft.value)
  }

  @Test
  fun narrowWindowSwitchesPanesWithoutLosingDraft() {
    org.junit.Assume.assumeTrue(composeRule.activity.resources.configuration.screenWidthDp < 840)
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    editNativeNote("First page note", "Draft in narrow view")
    composeRule.onNodeWithTag("reader_show_source").performClick()
    composeRule.onNodeWithTag("markdown_source_pane").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_show_note").performClick()
    composeRule.waitUntil(10_000) { findEditText("Draft in narrow view") != null }
    assertEquals("Draft in narrow view", session.noteDraft.value)
    val editor = requireNotNull(findEditText("Draft in narrow view"))
    composeRule.runOnUiThread {
      editor.requestFocus()
      composeRule.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    composeRule.waitForIdle()
    saveUiScreenshot("workspace-narrow.png")
  }

  @Test
  fun homeKeepsTheDraftWhenSavingConflicts() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    editNativeNote("First page note", "Local unsaved draft")
    val externalNotes = PageNotes.join(listOf("Changed externally", "Second page note", "Third page note"))
    replaceNotes(externalNotes)
    composeRule.onNodeWithTag("reader_bookshelf").assertIsEnabled().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteStatus.value == PageNoteConflictException().message }
    assertEquals(0, session.pageIndex.intValue)
    assertEquals("Local unsaved draft", session.noteDraft.value)
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())
    assertTrue(composeRule.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithTag("note_copy_reload").performClick()
    composeRule.onNodeWithTag("note_copy_reload_confirm").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == "Changed externally" }
    val clipboard = composeRule.activity.getSystemService(android.content.ClipboardManager::class.java)
    assertEquals("Local unsaved draft", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())
  }

  @Test
  fun idleEditPersistsWithoutSavedNotice() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    val edited = "First page note saved automatically"
    editNativeNote("First page note", edited)
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == edited }.getOrDefault(false)
    }
    assertEquals(edited, session.noteDraft.value)
    assertTrue(session.noteStatus.value != composeRule.activity.getString(R.string.note_saved))
  }

  @Test
  fun homeWaitsForPendingAutosaveAndPreservesLaterEdits() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    pauseNextNoteWrite()
    editNativeNote("First page note", "First pending version")
    try {
      composeRule.waitUntil(timeoutMillis = 15_000) {
        testResolver.call(ExistingProjectSafTestProvider.AUTHORITY,
          ExistingProjectSafTestProvider.METHOD_CHECK_NOTE_WRITE, null, null)?.getBoolean("started") == true
      }
      editNativeNote("First pending version", "Latest version before returning home")
      composeRule.onNodeWithTag("reader_bookshelf").assertIsEnabled().performClick()
      assertEquals(0, session.pageIndex.intValue)
    } finally {
      releaseNoteWrite()
    }
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("Latest version before returning home", NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1))
  }

  @Test
  fun rapidInkSavesAreCoalescedAndHomeWaitsForTheLatestStroke() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penStore.value = ProjectPenStore(testResolver, project.sourceDirectoryUri, project.annotationsUri)
      session.penPageStore.value = PenPageStore()
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_PEN_WRITE, null, null)
    fun addStroke(index: Int) {
      composeRule.runOnUiThread {
        val canvas = checkNotNull(findPenCanvas(composeRule.activity.window.decorView))
        val x = 200f + index * 45f
        val y = 300f + index * 30f
        checkNotNull(canvas.onStrokeAdded).invoke(PenStroke("pen", "#185abd", 4.0,
          listOf(PenPoint(x, y, 1f), PenPoint(x + 35f, y + 15f, 1f))))
      }
    }
    addStroke(1)
    try {
      composeRule.waitUntil(10_000) {
        testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_CHECK_PEN_WRITE, null, null)?.getBoolean("started") == true
      }
      (2..8).forEach(::addStroke)
      android.os.SystemClock.sleep(2_500) // Allow the system clipboard notice from fixture setup to clear.
      saveUiScreenshot("saving-ink-pending.png")
      composeRule.onNodeWithTag("reader_bookshelf").assertIsEnabled().performClick()
      assertTrue(composeRule.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isEmpty())
    } finally {
      testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_RELEASE_PEN_WRITE, null, null)
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isNotEmpty() }
    val writes = testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_GET_PEN_WRITE_COUNT, null, null)!!.getInt(ExistingProjectSafTestProvider.EXTRA_COUNT)
    assertEquals("only the first and latest snapshots need writing", 2, writes)
    assertEquals(8, ProjectPenStore(testResolver, project.sourceDirectoryUri, project.annotationsUri).readPage(PenSide.DOCUMENT, 1).size)
  }

  @Test
  fun failedPendingInkWriteRemainsRecoverableOnItsOriginPageAfterNavigation() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    val store = ProjectPenStore(testResolver, project.sourceDirectoryUri, project.annotationsUri)
    val targetPageBefore = store.readPage(PenSide.DOCUMENT, 2)
    val stroke = PenStroke("pen", "#185abd", 2.0, listOf(PenPoint(10f, 20f, 1f)))
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penStore.value = store
      session.penPageStore.value = PenPageStore().apply {
        targetPageBefore.forEach { addStroke(PenSide.DOCUMENT, 2, it) }
      }
      session.penStrokes.value = emptyList()
      session.penWritable.value = true
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_PEN_WRITE, null, null)
    testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_CONFIGURE_ANNOTATION_WRITE_FAILURE, null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, 1) })
    composeRule.runOnUiThread {
      val canvas = checkNotNull(findPenCanvas(composeRule.activity.window.decorView))
      checkNotNull(canvas.onStrokeAdded).invoke(stroke)
    }
    try {
      composeRule.waitUntil(10_000) {
        testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_CHECK_PEN_WRITE, null, null)?.getBoolean("started") == true
      }
      composeRule.onNodeWithTag("reader_next_page").assertIsEnabled().performClick()
      composeRule.waitUntil(10_000) { session.pageIndex.intValue == 1 && !session.isNavigating.value }
      assertEquals(targetPageBefore, session.penStrokes.value)
    } finally {
      testResolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_RELEASE_PEN_WRITE, null, null)
    }
    composeRule.waitUntil(15_000) { session.failedPenSaves[1] == listOf(stroke) && session.pendingPenSaves.intValue == 0 }
    assertEquals(1, session.pageIndex.intValue)
    assertEquals(targetPageBefore, session.penStrokes.value)
    assertEquals(targetPageBefore, store.readPage(PenSide.DOCUMENT, 2))
    assertTrue(session.failedPenSaveMessages[1] != null)

    // The failure notice can retry the origin page while the destination remains visible.
    composeRule.onNodeWithTag("reader_pen_save_retry").assertIsDisplayed().assertIsEnabled().performClick()
    composeRule.waitUntil(15_000) { session.failedPenSaves.isEmpty() && session.pendingPenSaves.intValue == 0 }
    assertEquals(1, session.pageIndex.intValue)
    assertEquals(targetPageBefore, session.penStrokes.value)
    assertEquals(listOf(stroke), store.readPage(PenSide.DOCUMENT, 1))
    assertEquals(targetPageBefore, store.readPage(PenSide.DOCUMENT, 2))
    composeRule.onNodeWithTag("reader_previous_page").assertIsEnabled().performClick()
    composeRule.waitUntil(10_000) { session.pageIndex.intValue == 0 && session.penStrokes.value == listOf(stroke) }
  }

  private fun findPenCanvas(view: View): PenCanvasView? {
    if (view is PenCanvasView) return view
    if (view is ViewGroup) for (index in 0 until view.childCount) {
      findPenCanvas(view.getChildAt(index))?.let { return it }
    }
    return null
  }

  @Test
  fun floatingPenBoxMovesAndAgentDrawerClosesWithoutChangingThePage() {
    composeRule.activity.getSharedPreferences("reader_floating_pen_box", 0).edit().clear().commit()
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes, createWorkspacePdf())
    val session = attachProjectToReader(project, notes, singlePagePdf = true)
    val sampleNote = """
      # 蚁群算法：从局部选择到完整路线

      信息素记录此前搜索的经验，距离启发让较近的城市更容易被选择。

      ## 下一步怎样选择？

      对尚未访问的城市，综合考虑信息素与距离，计算选择概率。

      ```python
      weight = pheromone ** alpha / distance ** beta
      ```

      | 要素 | 在搜索中的作用 |
      | --- | --- |
      | 信息素 | 共享成功路线的经验 |
      | 距离启发 | 偏向较近的城市 |

      这是自编离线学习材料，不是在线 AI 生成结果。
    """.trimIndent()
    composeRule.runOnUiThread { session.setNoteDraft(sampleNote) }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penWritable.value = true
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("floating_pen_box").fetchSemanticsNodes().isNotEmpty() }
    saveUiScreenshot("workspace-reading.png")
    composeRule.onNodeWithTag("reader_pen_ballpoint").assertIsDisplayed()
    saveUiScreenshot("workspace-pen-expanded.png")
    val before = composeRule.onNodeWithTag("floating_pen_box").fetchSemanticsNode().boundsInRoot.left
    composeRule.onNodeWithTag("floating_pen_box_handle").performTouchInput {
      down(center)
      moveBy(Offset(1800f, 50f))
      up()
    }
    composeRule.waitForIdle()
    val after = composeRule.onNodeWithTag("floating_pen_box").fetchSemanticsNode().boundsInRoot.left
    val viewport = composeRule.onNodeWithTag("pdf_page_viewport").fetchSemanticsNode().boundsInRoot
    assertTrue("pen box should snap to the opposite edge: before=$before after=$after viewport=$viewport", after > viewport.center.x)
    saveUiScreenshot("workspace-pen-moved.png")
    composeRule.onNodeWithTag("floating_pen_box_handle").performClick()
    composeRule.onNodeWithTag("reader_pen_ballpoint").assertDoesNotExist()
    saveUiScreenshot("workspace-pen-collapsed.png")
    composeRule.onNodeWithTag("floating_pen_box_handle").performClick()
    composeRule.onNodeWithTag("reader_pen_ballpoint").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_agent_toggle").performClick()
    composeRule.onNodeWithTag("agent_task_drawer").assertIsDisplayed()
    saveUiScreenshot("workspace-agent-drawer.png")
    composeRule.onNodeWithTag("agent_task_dismiss").performClick()
    assertEquals(0, session.pageIndex.intValue)
    assertEquals(sampleNote, session.noteDraft.value)
  }

  private fun attachProjectToReader(
    project: OpenedProject,
    notes: String,
    singlePagePdf: Boolean = false,
  ): ReaderSessionViewModel {
    val activity = composeRule.activity
    if (singlePagePdf) {
      runBlocking {
        val store = readerSettingsStore(activity)
        previousReaderSettings = store.settings.first()
        store.save(requireNotNull(previousReaderSettings).copy(continuousPdfScrolling = false))
      }
    }
    val resolver = activity.contentResolver
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    releaseFixtureGrant = resolver.persistedUriPermissions.none { it.uri == treeUri && it.isReadPermission && it.isWritePermission }
    activity.grantUriPermission(activity.packageName, treeUri,
      flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    resolver.takePersistableUriPermission(treeUri, flags)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    val fixtureTarget = ProjectOpenTarget(treeUri, ExistingProjectSafTestProvider.ROOT_ID, needsPersistablePermission = false)
    composeRule.runOnUiThread {
      session.findDraftRecoveries(fixtureTarget, project.manifest.id).forEach { record ->
        session.clearDraftRecovery(fixtureTarget, project.manifest.id, record.page)
      }
    }
    val library = BookshelfStore(composeRule.activity)
    runBlocking {
      previousLibrary = library.preferences.first()
      library.bind(treeUri.toString(), "Navigation fixture")
    }
    try {
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("bookshelf_book_Page jump fixture").fetchSemanticsNodes().isNotEmpty()
      }
    } catch (failure: Throwable) {
      saveUiScreenshot("bookshelf-fixture-error.png")
      throw AssertionError("Fixture missing from bookshelf:\n${composeRule.onRoot().printToString()}", failure)
    }
    if (activity.resources.configuration.screenWidthDp < 600) saveUiScreenshot("bookshelf-narrow.png")
    composeRule.onNodeWithTag("bookshelf_book_Page jump fixture").performClick()
    try {
      composeRule.waitUntil(timeoutMillis = 15_000) {
        composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
      }
    } catch (failure: Throwable) {
      throw AssertionError("Fixture open failed: ${session.errorMessage.value}; ${session.noteStatus.value}\n${composeRule.onRoot().printToString()}", failure)
    }
    composeRule.runOnUiThread {
      session.replaceRenderer(null)
      session.project.value = project
      session.pageIndex.intValue = 0
      session.penTool.value = io.pagenote.feature.pen.PenCanvasTool.PEN
      session.markdownSource.value = PageNotes.join(listOf("Source one", "Source two", "Source three"))
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      findEditText("First page note") != null || composeRule.onAllNodesWithTag("reader_show_note").fetchSemanticsNodes().isNotEmpty()
    }
    if (composeRule.onAllNodesWithTag("reader_show_note").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("reader_show_note").performClick()
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditText("First page note") != null }
    return session
  }

  private fun editNativeNote(original: String, replacement: String) {
    val editor = requireNotNull(findEditText(original))
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      assertTrue(
        editor.performAccessibilityAction(
          AccessibilityNodeInfo.ACTION_SET_TEXT,
          Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replacement)
          },
        ),
      )
    }
    composeRule.waitForIdle()
  }

  private fun enterPageNumber(value: String) {
    val field = composeRule.onNodeWithTag("page_preview_search")
    field.performTextClearance()
    field.performTextInput(value)
  }

  private fun pauseNextNoteWrite() {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_NOTE_WRITE,
      null,
      null,
    )
  }

  private fun waitForPausedNoteWrite(): Boolean = testResolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_WAIT_NOTE_WRITE,
    null,
    null,
  )?.getBoolean("started") == true

  private fun releaseNoteWrite() {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_RELEASE_NOTE_WRITE,
      null,
      null,
    )
  }

  private fun installProject(notes: String, pdf: ByteArray = createThreePagePdf()): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"b".repeat(64)}","name":"Page jump fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, pdf)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }

  private fun createWorkspacePdf(): ByteArray {
    val doc = PdfDocument()
    for (pageNo in 1..3) {
      val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, pageNo).create())
      val canvas = page.canvas
      val paint = Paint(Paint.ANTI_ALIAS_FLAG)
      canvas.drawColor(android.graphics.Color.WHITE)
      paint.color = android.graphics.Color.rgb(24, 90, 189); paint.textSize = 13f
      canvas.drawText("PAGENOTE / ORIGINAL LEARNING SAMPLE", 44f, 54f, paint)
      paint.color = android.graphics.Color.rgb(30, 45, 65); paint.textSize = 28f; paint.isFakeBoldText = true
      canvas.drawText(if (pageNo == 1) "Ant Colony Optimization" else "Recall and explain", 44f, 108f, paint)
      paint.isFakeBoldText = false; paint.textSize = 16f
      val lines = listOf("How do local choices produce a good route?", "", "1. Start with several candidate routes.",
        "2. Prefer short edges with stronger pheromone.", "3. Compare the total route lengths.",
        "4. Evaporate old pheromone and reinforce good routes.", "", "Three ideas to connect:",
        "distance  /  shared experience  /  exploration")
      lines.forEachIndexed { i, line -> canvas.drawText(line, 44f, 155f + i * 31f, paint) }
      paint.color = android.graphics.Color.rgb(235, 243, 252); canvas.drawRect(44f, 555f, 568f, 700f, paint)
      paint.color = android.graphics.Color.rgb(24, 90, 189); paint.textSize = 18f
      canvas.drawText("Try it on this page", 62f, 586f, paint)
      paint.color = android.graphics.Color.rgb(50, 70, 90); paint.textSize = 15f
      canvas.drawText("Draw a shape, undo it, then restore it.", 62f, 620f, paint)
      canvas.drawText("Use the note to create a review card.", 62f, 650f, paint)
      doc.finishPage(page)
    }
    return ByteArrayOutputStream().use { out -> doc.writeTo(out); doc.close(); out.toByteArray() }
  }

  private fun createThreePagePdf(): ByteArray {
    val document = PdfDocument()
    repeat(3) { index ->
      val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
      page.canvas.drawColor(android.graphics.Color.WHITE)
      page.canvas.drawText(
        "Navigation page ${index + 1}",
        24f,
        48f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = android.graphics.Color.BLACK
          textSize = 18f
        },
      )
      document.finishPage(page)
    }
    return ByteArrayOutputStream().use { output ->
      try {
        document.writeTo(output)
        output.toByteArray()
      } finally {
        document.close()
      }
    }
  }

  private fun replaceNotes(notes: String) {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_REPLACE_NOTES,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
      },
    )
  }

  private fun findEditText(expectedText: String): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val matches = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditTexts(matches)
      result.set(matches.firstOrNull { it.text.toString() == expectedText })
    }
    return result.get()
  }

  private fun View.collectEditTexts(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}
