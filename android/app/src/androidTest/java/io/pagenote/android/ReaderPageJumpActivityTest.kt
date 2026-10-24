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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
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

@RunWith(AndroidJUnit4::class)
class ReaderPageJumpActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()
  private var previousLibrary: BookshelfPreferences? = null
  private var releaseFixtureGrant = false

  @After fun restoreLibrary() = runBlocking {
    composeRule.runOnUiThread {
      val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
      if (session.project.value?.sourceUri?.authority == ExistingProjectSafTestProvider.AUTHORITY) {
        session.noteDraft.value = NotePage.pageAt(session.notesMarkdown.value, session.pageIndex.intValue + 1)
        session.clearCurrentDraftRecovery()
      }
    }
    previousLibrary?.let { BookshelfStore(composeRule.activity).bind(it.tree, it.name) }
    if (releaseFixtureGrant) composeRule.activity.contentResolver.releasePersistableUriPermission(
      treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    Unit
  }

  @Test
  fun nativePdfShowsNarrowRailAndRealThumbnailDrawer() {
    val notes = PageNotes.join(listOf("First page note", "Second page note", "Third page note"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = runBlocking { PdfDocumentSession.open(context, project.sourceUri, context.cacheDir) }
    composeRule.runOnUiThread {
      session.markdownSource.value = null
      session.replaceRenderer(renderer)
      session.penWritable.value = true
    }
    composeRule.waitUntil(15_000) { composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().isNotEmpty() }
    composeRule.onNodeWithTag("reader_pen_rail").assertWidthIsEqualTo(44.dp)
    composeRule.onNodeWithTag("reader_pen_ballpoint").performClick()
    composeRule.runOnIdle { assertEquals(io.pagenote.feature.pen.PenCanvasTool.BALLPOINT, session.penTool.value) }
    saveUiScreenshot("reader-pen-rail.png")
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
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    try { File(instrumentation.targetContext.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    finally { bitmap.recycle() }
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
    val session = attachProjectToReader(project, notes)
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
          composeRule.onAllNodesWithTag("pdf_page_viewport").fetchSemanticsNodes().isNotEmpty()
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

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    val activity = composeRule.activity
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
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("bookshelf_book_Page jump fixture").fetchSemanticsNodes().isNotEmpty()
    }
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
