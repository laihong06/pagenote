package io.pagenote.android

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.pen.PenStyle
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfImageAnnotationActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val resolver get() = context.contentResolver
  private val projectTreeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  @Test
  fun imageCommandRequiresWritablePdfAndCancellationWritesNothing() {
    val notes = PageNotes.join(listOf("First page", "Second page"))
    val project = installProject(notes, createPdf())
    openFixtureProjectFromBookshelf(composeRule, projectTreeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = null
      session.replaceRenderer(null)
      session.markdownSource.value = null
      session.noteWritable.value = false
      session.penWritable.value = false
    }
    handwritingCommand().assertIsNotEnabled()

    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = notes
      session.noteWritable.value = true
      session.penWritable.value = true
    }
    handwritingCommand().assertIsNotEnabled()

    val renderer = kotlinx.coroutines.runBlocking {
      PdfDocumentSession.open(context, project.sourceUri, context.cacheDir)
    }
    attachProject(project, renderer, notes)
    val callback = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.activity.pdfImagePickerForTesting = { callback.set(it) }
    try {
      waitForReader()
      handwritingCommand().assertIsEnabled().performClick()
      composeRule.waitUntil(10_000) { callback.get() != null }
      callback.getAndSet(null)?.invoke(null)
      val store = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
      assertTrue(store.readPage("document", 1).isEmpty())
      assertEquals(0, imageCount())
    } finally {
      composeRule.activity.pdfImagePickerForTesting = null
      renderer.close()
    }
  }

  @Test
  fun selectedImageBecomesOfflineCurrentPageAnnotationAndRendersInOverlay() {
    resetImageProvider()
    val notes = PageNotes.join(listOf("First page", "Second page"))
    val project = installProject(notes, createPdf())
    val renderer = kotlinx.coroutines.runBlocking {
      PdfDocumentSession.open(context, project.sourceUri, context.cacheDir)
    }
    val session = attachProject(project, renderer, notes)
    val callback = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.activity.pdfImagePickerForTesting = { callback.set(it) }
    val store = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    val pageTwoBefore = store.readPage("document", 2)
    try {
      waitForReader()
      handwritingCommand().assertIsEnabled().performClick()
      composeRule.waitUntil(10_000) { callback.get() != null }
      callback.getAndSet(null)?.invoke(sourceImageUri())

      waitUntilImageFlowIdle()
      val sourceStats = requireNotNull(
        resolver.call(NoteAssetSafTestProvider.AUTHORITY, NoteAssetSafTestProvider.METHOD_STATS, null, null),
      )
      assertEquals(
        "image flow should persist one annotation; status=${session.noteStatus.value}, assets=${imageCount()}, " +
          "sourceQueries=${sourceStats.getInt(NoteAssetSafTestProvider.EXTRA_QUERY_COUNT)}, " +
          "sourceOpens=${sourceStats.getInt(NoteAssetSafTestProvider.EXTRA_OPEN_COUNT)}",
        1,
        store.readPage("document", 1).size,
      )
      val imageItem = store.readPage("document", 1).single().jsonObject
      assertEquals("image", imageItem["type"]?.jsonPrimitive?.content)
      assertEquals(PenStyle.DEFAULT.color, imageItem["color"]?.jsonPrimitive?.content)
      assertEquals(PenStyle.DEFAULT.width, imageItem["width"]?.jsonPrimitive?.content?.toDouble())
      val url = imageItem["url"]?.jsonPrimitive?.content.orEmpty()
      val assetName = requireNotNull(PdfAnnotationImageReference.assetName(url))
      assertTrue("the annotation URL must use the desktop attachment contract", url.startsWith("/attachments/"))
      val points = imageItem["points"]!!.jsonArray
      val first = points[0].jsonArray
      val second = points[1].jsonArray
      assertEquals(80.0, first[0].jsonPrimitive.content.toDouble(), 0.01)
      assertEquals(430.0, second[0].jsonPrimitive.content.toDouble(), 0.01)
      assertEquals(350.0, second[1].jsonPrimitive.content.toDouble() - first[1].jsonPrimitive.content.toDouble(), 0.01)
      assertEquals(pageTwoBefore, store.readPage("document", 2))
      assertEquals(1, imageCount())

      composeRule.waitUntil(10_000) { overlayContainsMagenta() }
      assertNotNull(PdfAnnotationImageLoader(resolver, project.sourceDirectoryUri).load(assetName))
    } finally {
      composeRule.activity.pdfImagePickerForTesting = null
      renderer.close()
    }
  }

  @Test
  fun stalePickerResultIsIgnoredAndAnnotationFailureRemovesImportedAsset() {
    resetImageProvider()
    val notes = PageNotes.join(listOf("First page", "Second page"))
    val project = installProject(notes, createPdf())
    val renderer = kotlinx.coroutines.runBlocking {
      PdfDocumentSession.open(context, project.sourceUri, context.cacheDir)
    }
    val session = attachProject(project, renderer, notes)
    val callback = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.activity.pdfImagePickerForTesting = { callback.set(it) }
    val store = ProjectAnnotationStore(resolver, project.sourceDirectoryUri, project.annotationsUri)
    try {
      waitForReader()
      handwritingCommand().assertIsEnabled().performClick()
      composeRule.waitUntil(10_000) { callback.get() != null }
      composeRule.runOnUiThread { session.pageIndex.intValue = 1 }
      callback.getAndSet(null)?.invoke(sourceImageUri())
      waitUntilImageFlowIdle()
      assertTrue(store.readPage("document", 1).isEmpty())
      assertEquals(0, imageCount())

      composeRule.runOnUiThread { session.pageIndex.intValue = 0 }
      composeRule.waitUntil(10_000) { imageCommandIsEnabled() }
      resolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_CONFIGURE_ANNOTATION_WRITE_FAILURE,
        null,
        Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, 1) },
      )
      handwritingCommand().performClick()
      composeRule.waitUntil(10_000) { callback.get() != null }
      callback.getAndSet(null)?.invoke(sourceImageUri())
      waitUntilImageFlowIdle()
      assertTrue(store.readPage("document", 1).isEmpty())
      assertEquals("failed annotation persistence must delete the imported image", 0, imageCount())
    } finally {
      composeRule.activity.pdfImagePickerForTesting = null
      renderer.close()
    }
  }

  private fun handwritingCommand() = composeRule.onNodeWithTag("reader_menu_command_insert_ink_image").let { node ->
    if (composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("reader_menu_group_handwriting").performClick()
    } else if (composeRule.onAllNodesWithTag("reader_menu_command_insert_ink_image").fetchSemanticsNodes().isEmpty()) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_insert_ink_image").fetchSemanticsNodes().isNotEmpty()
    }
    node.performScrollTo()
  }

  private fun attachProject(
    project: OpenedProject,
    renderer: PdfDocumentSession,
    notes: String,
  ): ReaderSessionViewModel {
    openFixtureProjectFromBookshelf(composeRule, existingProjectTestTreeUri(), project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.pageIndex.intValue = 0
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = io.pagenote.core.project.PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.penWritable.value = true
      session.viewportBookmark.value = PdfViewportBookmark.DEFAULT
    }
    return session
  }

  private fun waitForReader() {
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty() &&
        composeRule.onAllNodesWithTag("pdf_page_viewport").fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitUntilImageFlowIdle() {
    composeRule.waitUntil(10_000) { imageCommandIsEnabled() }
  }

  private fun imageCommandIsEnabled(): Boolean = runCatching {
    composeRule.onNodeWithTag("reader_menu_command_insert_ink_image").assertIsEnabled()
  }.isSuccess

  private fun overlayContainsMagenta(): Boolean = runCatching {
    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    (0 until pixels.height).any { y ->
      (0 until pixels.width).any { x -> pixels[x, y] == Color(0xFFFF00FF) }
    }
  }.getOrDefault(false)

  private fun installProject(notes: String, pdf: ByteArray): OpenedProject {
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(
          ExistingProjectSafTestProvider.EXTRA_MANIFEST,
          """{"format":"pagenote-project","version":1,"id":"${"f".repeat(64)}","name":"Image fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}""".toByteArray(),
        )
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, pdf)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(resolver).open(projectTreeUri)
  }

  private fun createPdf(): ByteArray {
    val document = PdfDocument()
    repeat(2) { index ->
      val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
      page.canvas.drawColor(android.graphics.Color.WHITE)
      page.canvas.drawText(
        "PDF page ${index + 1}",
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
      document.writeTo(output)
      document.close()
      output.toByteArray()
    }
  }

  private fun sourceImageUri(): Uri {
    return DocumentsContract.buildDocumentUri(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.IMAGE_DOCUMENT_ID,
    )
  }

  private fun resetImageProvider() {
    resolver.call(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.METHOD_RESET,
      null,
      Bundle().apply { putString(NoteAssetSafTestProvider.EXTRA_FAILURE_MODE, NoteAssetSafTestProvider.FAILURE_NONE) },
    )
  }

  private fun imageCount(): Int = requireNotNull(
    resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_GET_IMAGE_COUNT, null, null),
  ).getInt(ExistingProjectSafTestProvider.EXTRA_COUNT)
}
