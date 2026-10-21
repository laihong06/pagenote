package io.pagenote.android

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PdfTextGlyph
import io.pagenote.core.reader.PdfTextPoint
import io.pagenote.core.reader.PdfTextSelection
import io.pagenote.core.reader.PdfTextSelectionMapper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfTextSelectionReviewTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun selectsPdfTextPersistsReviewAndRejectsStaleNoteTarget() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = File(context.cacheDir, "pdf-text-selection-${System.nanoTime()}.pdf")
      .apply { writeBytes(createTextPdf()) }
    val renderer = runBlocking {
      PdfDocumentSession.open(context, Uri.fromFile(source), context.cacheDir)
    }
    val notes = "Existing page note"
    val editedNote = "Existing page note edited before PDF review"
    val project = installProject(notes)
    val session = attachProject(renderer, project, notes)
    val pageText = requireNotNull(renderer.extractSelectableText(0))
    assertSame(pageText, renderer.extractSelectableText(0))
    val rotatedSource = File(context.cacheDir, "pdf-rotated-crop-${System.nanoTime()}.pdf")
      .apply { writeBytes(createRotatedCroppedTextPdf(context)) }
    val rotatedSession = runBlocking {
      PdfDocumentSession.open(context, Uri.fromFile(rotatedSource), context.cacheDir)
    }
    try {
      val rotatedPage = requireNotNull(rotatedSession.extractSelectableText(0))
      val rotatedQuote = "Rotated crop target"
      val rotatedStart = rotatedPage.text.indexOf(rotatedQuote)
      assertTrue(rotatedPage.text, rotatedStart >= 0)
      assertEquals(400.0, rotatedPage.pageWidth, 0.01)
      assertEquals(300.0, rotatedPage.pageHeight, 0.01)
      val rotatedEnd = rotatedStart + rotatedQuote.length
      val firstRotatedGlyph = requireNotNull(
        rotatedPage.glyphs.firstOrNull { rotatedStart in it.start until it.end },
      )
      val lastRotatedGlyph = requireNotNull(
        rotatedPage.glyphs.firstOrNull { rotatedEnd - 1 in it.start until it.end },
      )
      val rotatedSelection = requireNotNull(PdfTextSelectionMapper.select(
        rotatedPage,
        PdfTextPoint(
          (firstRotatedGlyph.bounds.left + firstRotatedGlyph.bounds.right) / 2.0,
          (firstRotatedGlyph.bounds.top + firstRotatedGlyph.bounds.bottom) / 2.0,
        ),
        PdfTextPoint(
          (lastRotatedGlyph.bounds.left + lastRotatedGlyph.bounds.right) / 2.0,
          (lastRotatedGlyph.bounds.top + lastRotatedGlyph.bounds.bottom) / 2.0,
        ),
      ))
      assertEquals(rotatedQuote, rotatedSelection.quote)
      val rotatedDraft = PdfSelectionReviewDraft.capture(
        page = 1,
        source = rotatedPage,
        selection = rotatedSelection,
        targetNoteRevision = PageNotes.revision(notes),
      )
      val rotatedAnchorPoints = requireNotNull(rotatedDraft.anchor["shape"])
        .jsonObject["points"]!!.jsonArray
      val expectedRotatedBounds = rotatedSelection.toWidth1000Bounds(rotatedPage.pageWidth)
      assertEquals(expectedRotatedBounds.left, rotatedAnchorPoints[0].jsonArray[0].jsonPrimitive.double, 0.5)
      assertEquals(expectedRotatedBounds.top, rotatedAnchorPoints[0].jsonArray[1].jsonPrimitive.double, 0.5)
      assertEquals(expectedRotatedBounds.right, rotatedAnchorPoints[1].jsonArray[0].jsonPrimitive.double, 0.5)
      assertEquals(expectedRotatedBounds.bottom, rotatedAnchorPoints[1].jsonArray[1].jsonPrimitive.double, 0.5)
    } finally {
      rotatedSession.close()
      rotatedSource.delete()
    }
    composeRule.runOnUiThread { session.noteDraft.value = editedNote }

    try {
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
      }
      assertTrue(pageText.text.contains("text on Android"))
      assertEquals(PageNotes.revision(pageText.text), pageText.revision)
      assertFalse(pageText.glyphs.isEmpty())

      val expectedSelection = selectPdfText("text on Android", pageText)
      composeRule.onNodeWithTag("pdf_text_selection_review").assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("review_selection_quote").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("review_selection_quote")
        .assertIsDisplayed().assertTextEquals("text on Android")
      enterReviewInstruction("Explain this PDF passage.")
      val concurrentNote = "Concurrent app save after review serialization"
      testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_REVIEW_READ,
        null,
        null,
      )
      composeRule.onNodeWithTag("review_create").assertIsEnabled().performClick()
      val reviewSnapshotReady = testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_WAIT_REVIEW_SNAPSHOT,
        null,
        null,
      )?.getBoolean("ready") ?: false
      assertTrue("review read should pause after the note revision check", reviewSnapshotReady)
      testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_NOTE_WRITE,
        null,
        null,
      )
      val noteWriteStarted = CountDownLatch(1)
      val noteWriteFinished = CountDownLatch(1)
      val noteWriteFailure = AtomicReference<Throwable?>()
      Thread {
        noteWriteStarted.countDown()
        try {
          ProjectNoteStore(testResolver, project.notesUri).write(concurrentNote, editedNote)
        } catch (error: Throwable) {
          noteWriteFailure.set(error)
        } finally {
          noteWriteFinished.countDown()
        }
      }.start()
      try {
        assertTrue(noteWriteStarted.await(5, TimeUnit.SECONDS))
        Thread.sleep(150)
        val noteWriteReachedProvider = testResolver.call(
          ExistingProjectSafTestProvider.AUTHORITY,
          ExistingProjectSafTestProvider.METHOD_CHECK_NOTE_WRITE,
          null,
          null,
        )?.getBoolean("started") ?: false
        assertFalse(
          "a note write must wait until the review revision check and save finish",
          noteWriteReachedProvider,
        )
      } finally {
        testResolver.call(
          ExistingProjectSafTestProvider.AUTHORITY,
          ExistingProjectSafTestProvider.METHOD_RELEASE_REVIEW_READ,
          null,
          null,
        )
        testResolver.call(
          ExistingProjectSafTestProvider.AUTHORITY,
          ExistingProjectSafTestProvider.METHOD_RELEASE_NOTE_WRITE,
          null,
          null,
        )
      }
      assertTrue("concurrent note write should finish", noteWriteFinished.await(10, TimeUnit.SECONDS))
      noteWriteFailure.get()?.let { throw AssertionError("concurrent note write failed", it) }
      composeRule.waitUntil(timeoutMillis = 10_000) { reviewWriteCount() == 1 }

      val storedReviews = readReviews(project)
      val record = Json.parseToJsonElement(storedReviews).jsonArray.single() as JsonObject
      assertEquals("document", record["side"]?.jsonPrimitive?.content)
      assertEquals("selection", record["kind"]?.jsonPrimitive?.content)
      assertEquals("page", record["scope"]?.jsonPrimitive?.content)
      assertEquals(1, record["page"]?.jsonPrimitive?.content?.toInt())
      assertEquals("Explain this PDF passage.", record["instruction"]?.jsonPrimitive?.content)
      assertEquals("text on Android", record["quote"]?.jsonPrimitive?.content)
      assertEquals(pageText.text, record["context"]?.jsonPrimitive?.content)
      assertEquals(pageText.revision, record["revision"]?.jsonPrimitive?.content)
      assertEquals(concurrentNote, ProjectNoteStore(testResolver, project.notesUri).read())
      assertEquals(PageNotes.revision(editedNote), session.noteBaseRevision.value)
      assertEquals(PageNotes.revision(editedNote), record["targetNoteRevision"]?.jsonPrimitive?.content)
      val anchor = record["anchor"] as JsonObject
      assertEquals("width-1000", anchor["coordinateSystem"]?.jsonPrimitive?.content)
      assertEquals("text on Android", anchor["text"]?.jsonPrimitive?.content)
      val shape = anchor["shape"] as JsonObject
      assertEquals("rect", shape["type"]?.jsonPrimitive?.content)
      val expectedBounds = expectedSelection.toWidth1000Bounds(pageText.pageWidth)
      val anchorPoints = requireNotNull(shape["points"]).jsonArray
      assertEquals(expectedBounds.left, anchorPoints[0].jsonArray[0].jsonPrimitive.double, 0.5)
      assertEquals(expectedBounds.top, anchorPoints[0].jsonArray[1].jsonPrimitive.double, 0.5)
      assertEquals(expectedBounds.right, anchorPoints[1].jsonArray[0].jsonPrimitive.double, 0.5)
      assertEquals(expectedBounds.bottom, anchorPoints[1].jsonArray[1].jsonPrimitive.double, 0.5)

      composeRule.onNodeWithTag("reviews_close").performClick()
      selectPdfText("text on Android", pageText)
      composeRule.onNodeWithTag("pdf_text_selection_review").assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("review_instruction_editor").fetchSemanticsNodes().isNotEmpty()
      }
      val changedNote = "Note changed outside the review dialog"
      replaceNotes(changedNote)
      enterReviewInstruction("This stale review must not be saved.")
      composeRule.onNodeWithTag("review_create").assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        session.noteStatus.value == context.getString(R.string.review_selection_stale)
      }
      assertEquals(1, reviewWriteCount())
      assertEquals(storedReviews, readReviews(project))
      assertEquals(changedNote, ProjectNoteStore(testResolver, project.notesUri).read())
    } finally {
      session.replaceRenderer(null)
      source.delete()
    }
  }

  private fun selectPdfText(quote: String, pageText: PdfPageText): PdfTextSelection {
    val commandTag = "reader_menu_command_pdf_text_selection"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      runCatching { composeRule.onNodeWithTag(commandTag).assertIsEnabled() }.isSuccess
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo().performClick()

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("pdf_page_viewport").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      runCatching { composeRule.onNodeWithTag("pdf_text_selection_review").assertIsDisplayed() }.isSuccess
    }
    val startIndex = pageText.text.indexOf(quote)
    require(startIndex >= 0) { "generated PDF text does not contain requested quote" }
    val endIndex = startIndex + quote.length
    val firstGlyph = requireNotNull(pageText.glyphs.firstOrNull { startIndex in it.start until it.end })
    val lastGlyph = requireNotNull(pageText.glyphs.firstOrNull { endIndex - 1 in it.start until it.end })
    val expectedSelection = requireNotNull(PdfTextSelectionMapper.select(
      pageText,
      PdfTextPoint(
        (firstGlyph.bounds.left + firstGlyph.bounds.right) / 2.0,
        (firstGlyph.bounds.top + firstGlyph.bounds.bottom) / 2.0,
      ),
      PdfTextPoint(
        (lastGlyph.bounds.left + lastGlyph.bounds.right) / 2.0,
        (lastGlyph.bounds.top + lastGlyph.bounds.bottom) / 2.0,
      ),
    ))
    val viewportNode = composeRule.onNodeWithTag("pdf_page_viewport").fetchSemanticsNode()
    val viewportWidth = viewportNode.size.width.toFloat()
    val viewportHeight = viewportNode.size.height.toFloat()
    val pageAspect = (pageText.pageWidth / pageText.pageHeight).toFloat()
    val pageWidth = minOf(viewportWidth, viewportHeight * pageAspect)
    val pageHeight = pageWidth / pageAspect
    val bounds = PdfViewportBounds(viewportWidth, viewportHeight, pageWidth, pageHeight)
    val bookmark = PdfViewportBookmark(scale = 2f, centerX = 0.44f, centerY = 0.52f)
    val transform = bookmark.toTransform(bounds)
    val start = glyphScreenPoint(firstGlyph, pageText, bounds, transform)
    val end = glyphScreenPoint(lastGlyph, pageText, bounds, transform)

    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(start)
      moveTo(end, delayMillis = 250)
      up()
    }
    composeRule.onNodeWithTag("pdf_text_selection_review").assertIsEnabled()
    return expectedSelection
  }

  private fun glyphScreenPoint(
    glyph: PdfTextGlyph,
    pageText: PdfPageText,
    bounds: PdfViewportBounds,
    transform: PdfViewportTransform,
  ): Offset {
    val pageX = ((glyph.bounds.left + glyph.bounds.right) / 2.0 / pageText.pageWidth * bounds.pageWidthPx).toFloat()
    val pageY = ((glyph.bounds.top + glyph.bounds.bottom) / 2.0 / pageText.pageHeight * bounds.pageHeightPx).toFloat()
    return Offset(
      x = bounds.viewportWidthPx / 2f + transform.translationX +
        (pageX - bounds.pageWidthPx / 2f) * transform.scale,
      y = bounds.viewportHeightPx / 2f + transform.translationY +
        (pageY - bounds.pageHeightPx / 2f) * transform.scale,
    )
  }

  private fun attachProject(
    renderer: PdfDocumentSession,
    project: OpenedProject,
    notes: String,
  ): ReaderSessionViewModel {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = notes
      session.noteDraft.value = notes
      session.noteBaseRevision.value = PageNotes.revision(notes)
      session.noteWritable.value = true
      session.penWritable.value = false
      session.viewportBookmark.value = PdfViewportBookmark(scale = 2f, centerX = 0.44f, centerY = 0.52f)
    }
    return session
  }

  private fun enterReviewInstruction(instruction: String) {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      findEditableNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow) != null
    }
    val editor = requireNotNull(
      findEditableNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow),
    )
    assertTrue(
      editor.performAction(
        AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, instruction)
        },
      ),
    )
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"PDF selection fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, byteArrayOf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }

  private fun replaceNotes(text: String) {
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_REPLACE_NOTES,
      null,
      Bundle().apply { putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, text.toByteArray(Charsets.UTF_8)) },
    )
  }

  private fun reviewWriteCount(): Int = testResolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_REVIEW_WRITE_COUNT,
    null,
    null,
  )?.getInt(ExistingProjectSafTestProvider.EXTRA_COUNT) ?: -1

  private fun readReviews(project: OpenedProject): String =
    testResolver.openInputStream(requireNotNull(project.reviewsUri))
      ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: error("review JSON should be readable")

  private fun createTextPdf(): ByteArray {
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
    page.canvas.drawColor(android.graphics.Color.WHITE)
    page.canvas.drawText(
      "Selectable text on Android 14",
      36f,
      220f,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 18f
      },
    )
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

  private fun createRotatedCroppedTextPdf(context: android.content.Context): ByteArray {
    PDFBoxResourceLoader.init(context.applicationContext)
    val document = PDDocument()
    return try {
      val page = PDPage(PDRectangle(500f, 700f)).apply {
        cropBox = PDRectangle(40f, 50f, 300f, 400f)
        rotation = 90
      }
      document.addPage(page)
      PDPageContentStream(document, page).use { content ->
        content.beginText()
        content.setFont(PDType1Font.HELVETICA, 18f)
        content.newLineAtOffset(70f, 300f)
        content.showText("Rotated crop target")
        content.endText()
      }
      ByteArrayOutputStream().use { output ->
        document.save(output)
        output.toByteArray()
      }
    } finally {
      document.close()
    }
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}

private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
  if (node == null) return null
  if (node.isEditable) return node
  for (index in 0 until node.childCount) findEditableNode(node.getChild(index))?.let { return it }
  return null
}
