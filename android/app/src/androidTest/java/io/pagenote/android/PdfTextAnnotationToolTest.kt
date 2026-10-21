package io.pagenote.android

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PdfTextAnnotationToolTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  private val resolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  @Test
  fun textToolPlacesAndPersistsOnlyTextAnnotations() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val notes = PageNotes.join(listOf("First page note", "Second page note"))
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = null
      session.replaceRenderer(null)
      session.markdownSource.value = null
      session.noteWritable.value = false
      session.penWritable.value = false
    }
    handwritingCommand("ink_text").assertIsNotEnabled()

    val project = installProject(notes, createPdf())
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = notes
      session.noteWritable.value = true
      session.penWritable.value = true
    }
    handwritingCommand("ink_text").assertIsNotEnabled()

    val renderer = kotlinx.coroutines.runBlocking {
      PdfDocumentSession.open(context, project.sourceUri, context.cacheDir)
    }
    attachProject(project, renderer, notes)
    val annotationStore = ProjectAnnotationStore(
      resolver,
      project.sourceDirectoryUri,
      project.annotationsUri,
    )
    val pageTwoBefore = annotationStore.readPage("document", 2)

    try {
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
      }
      handwritingCommand("ink_text").assertIsEnabled().performClick()
      composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput { click(center) }
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("annotation_confirm").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("annotation_confirm").assertIsDisplayed().assertIsNotEnabled()
      assertTrue(
        "Text mode must not expose the general Annotate region switch",
        composeRule.onAllNodesWithTag("annotation_kind_region").fetchSemanticsNodes().isEmpty(),
      )
      composeRule.onNodeWithTag("annotation_cancel").performClick()
      assertTrue(annotationStore.readPage("document", 1).isEmpty())

      composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput { click(center) }
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("annotation_confirm").fetchSemanticsNodes().isNotEmpty()
      }
      enterDialogText("Placed note")
      composeRule.onNodeWithTag("annotation_confirm").assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        annotationStore.readPage("document", 1).size == 1
      }

      val item = annotationStore.readPage("document", 1).single().jsonObject
      assertEquals("text", item["type"]?.jsonPrimitive?.content)
      assertEquals(PenStyle.DEFAULT.color, item["color"]?.jsonPrimitive?.content)
      assertEquals(
        PenStyle.DEFAULT.width,
        requireNotNull(item["width"]?.jsonPrimitive?.content).toDouble(),
        0.01,
      )
      assertEquals("Placed note", item["text"]?.jsonPrimitive?.content)
      val point = item["points"]!!.jsonArray.single().jsonArray
      assertEquals(500.0, point[0].jsonPrimitive.content.toDouble(), 3.0)
      assertEquals(1000.0 * 200.0 / 300.0, point[1].jsonPrimitive.content.toDouble(), 3.0)
      assertEquals(pageTwoBefore, annotationStore.readPage("document", 2))

      composeRule.waitUntil(timeoutMillis = 10_000) { overlayContainsPlacedText() }

      handwritingCommand("annotate_mode").performClick()
      composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput { click(center) }
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("annotation_kind_region").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("annotation_kind_region").assertIsDisplayed()
      composeRule.onNodeWithTag("annotation_cancel").performClick()
    } finally {
      renderer.close()
    }
  }

  private fun handwritingCommand(id: String) = run {
    val tag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_handwriting").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(tag).performScrollTo()
  }

  private fun attachProject(
    project: OpenedProject,
    renderer: PdfDocumentSession,
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
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.penWritable.value = true
      session.viewportBookmark.value = PdfViewportBookmark.DEFAULT
    }
    return session
  }

  private fun installProject(notes: String, pdf: ByteArray): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"f".repeat(64)}","name":"Text tool fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    resolver.call(
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
    return ProjectDocumentOpener(resolver).open(treeUri)
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

  private fun enterDialogText(text: String) {
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
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        },
      ),
    )
  }

  private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
    if (node == null) return null
    if (node.isEditable) return node
    for (index in 0 until node.childCount) {
      findEditableNode(node.getChild(index))?.let { return it }
    }
    return null
  }

  private fun overlayContainsPlacedText(): Boolean {
    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    val centerX = pixels.width / 2
    val centerY = pixels.height / 2
    return (centerY - 24..centerY).any { y ->
      (centerX..(centerX + pixels.width / 4).coerceAtMost(pixels.width - 1)).any { x ->
        val color = pixels[x, y]
        color.alpha > 0.5f && color.red > 0.7f && color.green < 0.4f && color.blue < 0.5f
      }
    }
  }
}
