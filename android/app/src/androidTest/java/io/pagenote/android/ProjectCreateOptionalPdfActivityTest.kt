package io.pagenote.android

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Assert.assertTrue
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
  fun safFixtureCanPersistAndReleaseReadPermission() {
    val activity = composeRule.activity
    val resolver = activity.contentResolver
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      "permission-${UUID.randomUUID()}.pdf",
      blankOnePagePdf(),
    )
    activity.grantUriPermission(
      activity.packageName,
      sourceUri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    )

    resolver.takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    assertTrue(resolver.persistedUriPermissions.any { it.uri == sourceUri && it.isReadPermission })

    resolver.releasePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    assertTrue(resolver.persistedUriPermissions.none { it.uri == sourceUri })
  }

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
      composeRule.activity.newProjectDocumentPickerForTesting = { types, callback ->
        assertEquals(
          listOf("application/pdf", "text/markdown", "text/x-markdown", "text/plain"),
          types.toList(),
        )
        pendingPdfPicker.set { uri ->
          if (uri != null) grantPersistableReadPermission(uri)
          callback(uri)
        }
      }
    }

    openNewProjectDialog()
    val projectName = "Blank worksheet ${UUID.randomUUID()}"
    setProjectName(projectName)
    composeRule.onNodeWithTag("new_project_choose_dir").performClick()
    composeRule.onNodeWithTag("new_project_choose_document").performClick()
    composeRule.waitUntil(10_000) { pendingPdfPicker.get() != null }
    composeRule.runOnUiThread { pendingPdfPicker.getAndSet(null)?.invoke(null) }
    composeRule.onNodeWithText("Choose folder ✓").assertExists()
    assertProjectName(projectName)

    composeRule.onNodeWithTag("new_project_choose_document").performClick()
    composeRule.waitUntil(10_000) { pendingPdfPicker.get() != null }
    composeRule.runOnUiThread { pendingPdfPicker.getAndSet(null)?.invoke(sourceUri) }
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("new_project_clear_document").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.waitUntil(10_000) { hasPersistedReadGrant(resolver, sourceUri) }
    composeRule.onNodeWithText(sourceName).assertExists()
    composeRule.activityRule.scenario.recreate()
    composeRule.waitForIdle()
    assertTrue(hasPersistedReadGrant(composeRule.activity.contentResolver, sourceUri))
    composeRule.onNodeWithText("Choose folder ✓").assertExists()
    composeRule.onNodeWithText(sourceName).assertExists()
    assertProjectName(projectName)

    composeRule.onNodeWithTag("new_project_confirm").performClick()
    composeRule.waitUntil(30_000) {
      composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.waitUntil(10_000) {
      !hasPersistedReadGrant(composeRule.activity.contentResolver, sourceUri)
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

  @Test
  fun copiedPdfValidationFailureRollsBackCreatedDocuments() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val name = "Corrupted copy ${UUID.randomUUID()}"
    val sourceName = "valid-${UUID.randomUUID()}.pdf"
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      sourceName,
      blankOnePagePdf(),
    )
    ProjectCreateSafTestProvider.corruptNextCopiedPdfForValidation(resolver)

    try {
      ProjectCreator(resolver, composeRule.activity.cacheDir).create(
        treeUri,
        name,
        sourceUri,
        sourceName,
      )
      throw AssertionError("corrupted project PDF copy was accepted")
    } catch (_: IOException) {
    } finally {
      ProjectCreateSafTestProvider.clearTestFailures(resolver)
    }

    assertNull(findChildOrNull(resolver, treeUri, treeUri, name))
  }

  @Test
  fun clearingOrDismissingSourceReleasesPersistedReadGrant() {
    val resolver = composeRule.activity.contentResolver
    val sourceName = "grant-${UUID.randomUUID()}.pdf"
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      sourceName,
      blankOnePagePdf(),
    )
    val pendingPicker = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.runOnUiThread {
      composeRule.activity.newProjectDocumentPickerForTesting = { _, callback ->
        pendingPicker.set { uri ->
          if (uri != null) grantPersistableReadPermission(uri)
          callback(uri)
        }
      }
    }

    openNewProjectDialog()
    composeRule.onNodeWithTag("new_project_choose_document").performClick()
    composeRule.waitUntil(10_000) { pendingPicker.get() != null }
    composeRule.runOnUiThread { pendingPicker.getAndSet(null)?.invoke(sourceUri) }
    composeRule.waitUntil(10_000) { hasPersistedReadGrant(resolver, sourceUri) }
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("new_project_clear_document").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("new_project_clear_document").performClick()
    composeRule.waitUntil(10_000) { !hasPersistedReadGrant(resolver, sourceUri) }

    composeRule.onNodeWithTag("new_project_choose_document").performClick()
    composeRule.waitUntil(10_000) { pendingPicker.get() != null }
    composeRule.runOnUiThread { pendingPicker.getAndSet(null)?.invoke(sourceUri) }
    composeRule.waitUntil(10_000) { hasPersistedReadGrant(resolver, sourceUri) }
    composeRule.onNodeWithTag("new_project_cancel").performClick()
    composeRule.waitUntil(10_000) { !hasPersistedReadGrant(resolver, sourceUri) }
  }

  @Test
  fun duplicateProjectNameIsRejectedWithoutChangingExistingProject() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val name = "Duplicate ${UUID.randomUUID()}"
    ProjectCreator(resolver, composeRule.activity.cacheDir).create(treeUri, name)
    val sourceName = "replacement-${UUID.randomUUID()}.md"
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      sourceName,
      "# replacement".toByteArray(Charsets.UTF_8),
    )

    try {
      ProjectCreator(resolver, composeRule.activity.cacheDir).create(
        treeUri,
        name,
        sourceUri,
        sourceName,
      )
      throw AssertionError("duplicate project name was accepted")
    } catch (_: IOException) {
    }

    val projectUri = findChild(resolver, treeUri, treeUri, name)
    val srcUri = findChild(resolver, treeUri, projectUri, "src")
    assertNotNull(findChild(resolver, treeUri, srcUri, "left.md"))
    assertNull(findChildOrNull(resolver, treeUri, srcUri, "document.md"))
  }

  @Test
  fun markdownSourceIsCopiedByteForByteIntoProject() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val sourceName = "review-${UUID.randomUUID()}.Md"
    val sourceBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
      "# Imported\r\n\r\nPage notes\r\n".toByteArray(Charsets.UTF_8)
    val sourceUri = ProjectCreateSafTestProvider.createSource(resolver, sourceName, sourceBytes)
    val projectName = "Markdown import ${UUID.randomUUID()}"

    ProjectCreator(resolver, composeRule.activity.cacheDir).create(
      treeUri,
      projectName,
      sourceUri,
      sourceName,
    )

    val projectUri = findChild(resolver, treeUri, treeUri, projectName)
    val srcUri = findChild(resolver, treeUri, projectUri, "src")
    val importedUri = findChild(resolver, treeUri, srcUri, "document.md")
    assertArrayEquals(sourceBytes, resolver.openInputStream(importedUri)!!.use { it.readBytes() })
    assertNotNull(findChild(resolver, treeUri, srcUri, "right.md"))
    assertNotNull(findChild(resolver, treeUri, srcUri, "assets"))
    assertNull(findChildOrNull(resolver, treeUri, srcUri, "left.md"))
    val manifestUri = findChild(resolver, treeUri, projectUri, "project.pagenote")
    val manifest = ProjectManifestCodec.decode(
      resolver.openInputStream(manifestUri)!!.bufferedReader().use { it.readText() },
    )
    assertEquals("src/document.md", manifest.left)
    assertEquals(sourceName, manifest.leftName)
  }

  @Test
  fun emptyMarkdownSourceIsValidAndUnsupportedExtensionCreatesNoProject() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val emptyName = "empty-${UUID.randomUUID()}.markdown"
    val emptyUri = ProjectCreateSafTestProvider.createSource(resolver, emptyName, byteArrayOf())
    val emptyProjectName = "Empty Markdown ${UUID.randomUUID()}"

    ProjectCreator(resolver, composeRule.activity.cacheDir).create(
      treeUri,
      emptyProjectName,
      emptyUri,
      emptyName,
    )

    val projectUri = findChild(resolver, treeUri, treeUri, emptyProjectName)
    val srcUri = findChild(resolver, treeUri, projectUri, "src")
    val importedUri = findChild(resolver, treeUri, srcUri, "document.markdown")
    assertArrayEquals(byteArrayOf(), resolver.openInputStream(importedUri)!!.use { it.readBytes() })
    val manifestUri = findChild(resolver, treeUri, projectUri, "project.pagenote")
    assertEquals(
      "src/document.markdown",
      ProjectManifestCodec.decode(
        resolver.openInputStream(manifestUri)!!.bufferedReader().use { it.readText() },
      ).left,
    )

    val unsupportedName = "unsupported-${UUID.randomUUID()}.txt"
    val unsupportedUri = ProjectCreateSafTestProvider.createSource(resolver, unsupportedName, byteArrayOf())
    val unsupportedProjectName = "Unsupported ${UUID.randomUUID()}"
    try {
      ProjectCreator(resolver, composeRule.activity.cacheDir).create(
        treeUri,
        unsupportedProjectName,
        unsupportedUri,
        unsupportedName,
      )
      throw AssertionError("unsupported source extension was accepted")
    } catch (_: IOException) {
    }
    assertNull(findChildOrNull(resolver, treeUri, treeUri, unsupportedProjectName))
  }

  @Test
  fun openDocumentSelectionPrepopulatesProjectCreationWithoutReplacingCurrentReader() {
    val resolver = composeRule.activity.contentResolver
    val treeUri = ProjectCreateSafTestProvider.treeUri()
    val currentProjectName = "Current ${UUID.randomUUID()}"
    composeRule.runOnUiThread {
      composeRule.activity.newProjectDirectoryPickerForTesting = { callback -> callback(treeUri) }
    }
    openNewProjectDialog()
    setProjectName(currentProjectName)
    composeRule.onNodeWithTag("new_project_choose_dir").performClick()
    composeRule.onNodeWithTag("new_project_confirm").performClick()
    composeRule.waitUntil(30_000) {
      composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
    }

    val sourceName = "Exam: Notes.MARKDOWN"
    val sourceUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      sourceName,
      "# Imported notes\r\n".toByteArray(Charsets.UTF_8),
    )
    val unsupportedName = "notes.txt"
    val unsupportedUri = ProjectCreateSafTestProvider.createSource(
      resolver,
      unsupportedName,
      "not markdown".toByteArray(Charsets.UTF_8),
    )
    val pendingPicker = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.runOnUiThread {
      composeRule.activity.newProjectDocumentPickerForTesting = { types, callback ->
        assertEquals("application/pdf", types.first())
        assertEquals("text/plain", types.last())
        pendingPicker.set(callback)
      }
    }

    clickMenuCommand("open_document")
    composeRule.waitUntil(10_000) { pendingPicker.get() != null }
    composeRule.runOnUiThread { pendingPicker.getAndSet(null)?.invoke(null) }
    composeRule.waitForIdle()
    assertEquals(0, composeRule.onAllNodesWithTag("new_project_confirm").fetchSemanticsNodes().size)
    composeRule.onNodeWithTag("reader_page_status").assertExists()

    clickMenuCommand("open_document")
    composeRule.waitUntil(10_000) { pendingPicker.get() != null }
    composeRule.runOnUiThread { pendingPicker.getAndSet(null)?.invoke(unsupportedUri) }
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("new_project_confirm").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(
      composeRule.activity.getString(R.string.new_project_unsupported_document, unsupportedName),
    ).assertExists()
    composeRule.onNodeWithTag("reader_page_status").assertExists()
    composeRule.onNodeWithTag("new_project_cancel").performClick()
    composeRule.waitForIdle()
    assertEquals(0, composeRule.onAllNodesWithTag("new_project_confirm").fetchSemanticsNodes().size)

    clickMenuCommand("open_document")
    composeRule.waitUntil(10_000) { pendingPicker.get() != null }
    composeRule.runOnUiThread { pendingPicker.getAndSet(null)?.invoke(sourceUri) }
    composeRule.waitForIdle()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("new_project_choose_document").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(sourceName).assertExists()
    composeRule.onNodeWithTag("new_project_name_input").assertTextEquals(
      composeRule.activity.getString(R.string.new_project_name),
      "Exam_ Notes",
    )
    composeRule.onNodeWithTag("reader_page_status").assertExists()
    composeRule.onNodeWithTag("new_project_choose_dir").performClick()
    composeRule.onNodeWithTag("new_project_confirm").performClick()
    composeRule.waitUntil(30_000) {
      composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty() &&
        composeRule.onAllNodesWithText("Exam_ Notes").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_page_status").assertTextEquals("1 / 1")
    assertEquals(0, composeRule.onAllNodesWithTag("new_project_confirm").fetchSemanticsNodes().size)
  }

  private fun openNewProjectDialog() {
    clickMenuCommand("new_project")
  }

  private fun clickMenuCommand(commandId: String) {
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_$commandId").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("reader_menu_trigger").fetchSemanticsNodes().isNotEmpty()
    }
    if (composeRule.onAllNodesWithTag("reader_menu_command_$commandId").fetchSemanticsNodes().isEmpty()) {
      composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    }
    composeRule.onNodeWithTag("reader_menu_command_$commandId").performClick()
  }

  private fun setProjectName(value: String) {
    composeRule.onNodeWithTag("new_project_name_input").performTextInput(value)
  }

  private fun grantPersistableReadPermission(uri: Uri) {
    val activity = composeRule.activity
    activity.runOnUiThread {
      activity.grantUriPermission(
        activity.packageName,
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
      )
      activity.persistNewProjectDocumentGrantForTesting = true
    }
  }

  private fun hasPersistedReadGrant(
    resolver: android.content.ContentResolver,
    uri: Uri,
  ): Boolean = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

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
