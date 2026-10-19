package io.pagenote.android

import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.core.project.ProjectManifestCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ProjectCreateOptionalPdfActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun selectedPdfSurvivesCancellationAndActivityRecreationThenOpensAsOnePage() {
    val resolver = composeRule.activity.contentResolver
    val sourceBytes = blankOnePagePdf()
    val sourceName = "whiteboard-${UUID.randomUUID()}.pdf"
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      sourceName,
      sourceBytes,
    )
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val pendingPdfPicker = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.runOnUiThread {
      composeRule.activity.newProjectDirectoryPickerForTesting = { callback -> callback(treeUri) }
      composeRule.activity.newProjectPdfPickerForTesting = { types, callback ->
        assertEquals(listOf("application/pdf"), types.toList())
        pendingPdfPicker.set(callback)
      }
    }

    openNewProjectDialog()
    val projectName = "Blank worksheet ${UUID.randomUUID()}"
    setProjectName(projectName)
    composeRule.onNodeWithTag("new_project_choose_dir").performClick()
    composeRule.onNodeWithTag("new_project_choose_pdf").performClick()
    composeRule.waitUntil(10_000) { pendingPdfPicker.get() != null }
    composeRule.runOnUiThread { pendingPdfPicker.getAndSet(null)?.invoke(null) }
    composeRule.onNodeWithText("Choose folder ✓").assertExists()
    assertProjectName(projectName)

    composeRule.onNodeWithTag("new_project_choose_pdf").performClick()
    composeRule.waitUntil(10_000) { pendingPdfPicker.get() != null }
    composeRule.runOnUiThread { pendingPdfPicker.getAndSet(null)?.invoke(sourceUri) }
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("new_project_clear_pdf").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(sourceName).assertExists()
    composeRule.activityRule.scenario.recreate()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Choose folder ✓").assertExists()
    composeRule.onNodeWithText(sourceName).assertExists()
    assertProjectName(projectName)

    composeRule.onNodeWithTag("new_project_confirm").performClick()
    composeRule.waitUntil(30_000) {
      composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("1 / 1").assertExists()

    val projectUri = findChild(resolver, treeUri, treeUri, projectName)
    val manifestUri = findChild(resolver, treeUri, projectUri, "project.pagenote")
    val srcUri = findChild(resolver, treeUri, projectUri, "src")
    val pdfUri = findChild(resolver, treeUri, srcUri, "document.pdf")
    assertNotNull(findChild(resolver, treeUri, srcUri, "right.md"))
    assertNotNull(findChild(resolver, treeUri, srcUri, "assets"))
    assertNull(findChildOrNull(resolver, treeUri, srcUri, "left.md"))
    val manifestText = resolver.openInputStream(manifestUri)!!.bufferedReader().use { it.readText() }
    val manifest = ProjectManifestCodec.decode(manifestText)
    assertEquals("src/document.pdf", manifest.left)
    assertEquals("src/right.md", manifest.right)
    assertEquals(sourceName, manifest.leftName)
    assertFalse(manifestText.contains(sourceUri.toString()))
    assertArrayEquals(sourceBytes, resolver.openInputStream(pdfUri)!!.use { it.readBytes() })
    val descriptor = requireNotNull(resolver.openFileDescriptor(pdfUri, "r"))
    val renderer = android.graphics.pdf.PdfRenderer(descriptor)
    try {
      assertEquals(1, renderer.pageCount)
    } finally {
      renderer.close()
    }
  }

  @Test
  fun noPdfCreatesTheExistingMarkdownProjectStructure() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val name = "Markdown ${UUID.randomUUID()}"
    ProjectCreator(resolver, composeRule.activity.cacheDir).create(treeUri, name)

    val projectUri = findChild(resolver, treeUri, treeUri, name)
    val srcUri = findChild(resolver, treeUri, projectUri, "src")
    assertNotNull(findChild(resolver, treeUri, srcUri, "left.md"))
    assertNotNull(findChild(resolver, treeUri, srcUri, "right.md"))
    assertNotNull(findChild(resolver, treeUri, srcUri, "assets"))
    assertNull(findChildOrNull(resolver, treeUri, srcUri, "document.pdf"))
    val manifestUri = findChild(resolver, treeUri, projectUri, "project.pagenote")
    val manifestText = resolver.openInputStream(manifestUri)!!.bufferedReader().use { it.readText() }
    assertEquals("src/left.md", ProjectManifestCodec.decode(manifestText).left)
  }

  @Test
  fun malformedPdfIsRejectedAndLeavesNoPartialProject() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val name = "Malformed ${UUID.randomUUID()}"
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      "invalid-${UUID.randomUUID()}.pdf",
      "%PDF-invalid".toByteArray(),
    )

    try {
      ProjectCreator(resolver, composeRule.activity.cacheDir).create(
        treeUri,
        name,
        sourceUri,
        sourceUri.lastPathSegment,
      )
      throw AssertionError("malformed PDF was accepted")
    } catch (_: IOException) {
    }

    assertNull(findChildOrNull(resolver, treeUri, treeUri, name))
  }

  private fun openNewProjectDialog() {
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_new_project").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("reader_menu_trigger").fetchSemanticsNodes().isNotEmpty()
    }
    if (composeRule.onAllNodesWithTag("reader_menu_command_new_project").fetchSemanticsNodes().isEmpty()) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
    composeRule.onNodeWithTag("reader_menu_command_new_project").performClick()
  }

  private fun setProjectName(value: String) {
    composeRule.onNodeWithTag("new_project_name_input").performTextInput(value)
  }

  private fun assertProjectName(value: String) {
    composeRule.onNodeWithTag("new_project_name_input").assertTextEquals(
      composeRule.activity.getString(R.string.new_project_name),
      value,
    )
  }

  private fun blankOnePagePdf(): ByteArray {
    val pdf = PdfDocument()
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
    pdf.finishPage(page)
    val output = ByteArrayOutputStream()
    pdf.writeTo(output)
    pdf.close()
    return output.toByteArray()
  }

  private fun findChild(
    resolver: android.content.ContentResolver,
    treeUri: Uri,
    parentUri: Uri,
    name: String,
  ): Uri =
    requireNotNull(findChildOrNull(resolver, treeUri, parentUri, name)) { "missing document $name" }

  private fun findChildOrNull(
    resolver: android.content.ContentResolver,
    treeUri: Uri,
    parentUri: Uri,
    name: String,
  ): Uri? {
    val parentId = if (parentUri == treeUri) {
      DocumentsContract.getTreeDocumentId(treeUri)
    } else {
      DocumentsContract.getDocumentId(parentUri)
    }
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    resolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      ),
      null,
      null,
      null,
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == name) {
          return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
        }
      }
    }
    return null
  }
}
