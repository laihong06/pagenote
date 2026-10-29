package io.pagenote.android

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.image.AsyncDrawableSpan
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteMarkdownImageInsertionActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun pickerCancellationWriteRollbackAndOfflineProjectRoundTrip() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    imageCommand().assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    imageCommand().assertIsEnabled()

    val originalDraft = session.noteDraft.value
    launchPickerResult(false, null)
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_image").fetchSemanticsNodes().isNotEmpty()
    }
    imageCommand().assertIsEnabled()
    assertEquals(originalDraft, session.noteDraft.value)
    assertEquals(0, imageCount())

    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_IMAGE_WRITE_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, 1) },
    )
    launchPickerResult(true, imageResultIntent())
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_image_import_failed)
    }
    assertEquals(originalDraft, session.noteDraft.value)
    assertEquals(0, imageCount())
    assertTrue("a failed import must remove its newly-created assets directory", !assetsDirectoryExists())

    selectWord("Selected")
    launchPickerResult(true, imageResultIntent())
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteDraft.value.contains("![01234567-89ab-cdef-0123-456789abcdef](assets/")
    }
    val insertedDraft = session.noteDraft.value
    val assetName = ProjectAssetRefs.scan(insertedDraft).single()
    assertTrue(assetName.endsWith(".png"))
    assertEquals(1, imageCount())
    val referenceEnd = insertedDraft.indexOf(')', insertedDraft.indexOf("![")) + 1
    assertEquals(referenceEnd + 1, requireNotNull(findNoteEditor()).selectionStart)

    assertProjectAssetRenders(project.sourceDirectoryUri, insertedDraft)

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == insertedDraft
    }
    val reopened = ProjectDocumentOpener(testResolver).open(treeUri)
    val reopenedNotes = ProjectNoteStore(testResolver, reopened.notesUri).read()
    assertEquals(insertedDraft, NotePage.pageAt(reopenedNotes, 1))
    assertEquals(savedPages[1], NotePage.pageAt(reopenedNotes, 2))
    assertProjectAssetRenders(reopened.sourceDirectoryUri, NotePage.pageAt(reopenedNotes, 1))
  }

  @Test
  fun pastesClipboardImageIntoCurrentPageOnly() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)
    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
    try {
      clipboard.setPrimaryClip(ClipData.newPlainText("text only", "ordinary clipboard text"))
      pasteImageCommand().assertIsEnabled().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) { session.noteStatus.value != null }
      assertEquals(
        composeRule.activity.getString(R.string.reader_menu_paste_image_no_image),
        session.noteStatus.value,
      )
      assertEquals(savedPages[0], session.noteDraft.value)
      assertEquals(0, imageCount())

      selectWord("Selected")
      testResolver.call(
        NoteAssetSafTestProvider.AUTHORITY,
        NoteAssetSafTestProvider.METHOD_SET_HIDE_IMAGE_DISPLAY_NAME,
        null,
        Bundle().apply {
          putBoolean(NoteAssetSafTestProvider.EXTRA_HIDE_IMAGE_DISPLAY_NAME, true)
        },
      )
      val sourceNameCursor = testResolver.query(
        selectedImageUri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
      )
      assertNotNull(sourceNameCursor)
      sourceNameCursor!!.use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertNull(cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
      }
      clipboard.setPrimaryClip(
        ClipData.newUri(composeRule.activity.contentResolver, "copied image", selectedImageUri),
      )
      pasteImageCommand().performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        session.noteDraft.value.contains("![")
      }

      val insertedDraft = session.noteDraft.value
      val assetName = ProjectAssetRefs.scan(insertedDraft).single()
      assertTrue(assetName.endsWith(".png"))
      assertEquals(1, imageCount())
      val clipboardAlt = composeRule.activity.getString(R.string.reader_menu_paste_image_alt)
      assertEquals("Lead \n![$clipboardAlt](assets/$assetName)\n tail", insertedDraft)
      val referenceEnd = insertedDraft.indexOf(')', insertedDraft.indexOf("![")) + 1
      assertEquals(referenceEnd + 1, requireNotNull(findNoteEditor()).selectionStart)
      assertProjectAssetRenders(project.sourceDirectoryUri, insertedDraft)

      composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
      composeRule.waitUntil(timeoutMillis = 10_000) {
        NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == insertedDraft
      }
      val reopened = ProjectDocumentOpener(testResolver).open(treeUri)
      val reopenedNotes = ProjectNoteStore(testResolver, reopened.notesUri).read()
      assertEquals(insertedDraft, NotePage.pageAt(reopenedNotes, 1))
      assertEquals(savedPages[1], NotePage.pageAt(reopenedNotes, 2))
      assertProjectAssetRenders(reopened.sourceDirectoryUri, NotePage.pageAt(reopenedNotes, 1))
    } finally {
      clipboard.clearPrimaryClip()
      testResolver.call(
        NoteAssetSafTestProvider.AUTHORITY,
        NoteAssetSafTestProvider.METHOD_SET_HIDE_IMAGE_DISPLAY_NAME,
        null,
        Bundle().apply {
          putBoolean(NoteAssetSafTestProvider.EXTRA_HIDE_IMAGE_DISPLAY_NAME, false)
        },
      )
    }
  }

  private fun imageCommand() = run {
    val tag = "reader_menu_command_image"
    if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_insert").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(tag).performScrollTo()
  }

  private fun pasteImageCommand() = run {
    val tag = "reader_menu_command_paste_image"
    if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_insert").performClick()
      } else if (
        composeRule.onAllNodesWithTag("reader_menu_group_insert").fetchSemanticsNodes().isNotEmpty()
      ) {
        composeRule.onNodeWithTag("reader_menu_group_insert").performScrollTo()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(tag).performScrollTo()
  }

  private fun launchPickerResult(success: Boolean, data: Intent?) {
    val resultCallback = AtomicReference<((Uri?) -> Unit)?>(null)
    composeRule.runOnUiThread {
      composeRule.activity.markdownImagePickerForTesting = { callback -> resultCallback.set(callback) }
    }
    imageCommand().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { resultCallback.get() != null }
    composeRule.runOnUiThread {
      composeRule.activity.markdownImagePickerForTesting = null
      resultCallback.getAndSet(null)?.invoke(if (success) data?.data else null)
    }
    composeRule.waitForIdle()
  }

  private fun imageResultIntent(): Intent = Intent()
    .setData(selectedImageUri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

  private fun selectWord(word: String) {
    val editor = requireNotNull(findNoteEditor())
    val start = editor.text.indexOf(word)
    check(start >= 0)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, start + word.length)
    }
    composeRule.waitForIdle()
  }

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    openFixtureProjectFromBookshelf(composeRule, treeUri, project.name)
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.replaceRenderer(null)
      session.markdownSource.value = "Source document text"
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = io.pagenote.core.project.PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    return session
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"d".repeat(64)}","name":"Markdown image fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalReaderTestPdf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }

  private fun assertProjectAssetRenders(sourceDirectoryUri: Uri, markdown: String) {
    val renderer = NoteMarkdownRenderer.createPreview(composeRule.activity, sourceDirectoryUri, Color.BLACK)
    val rendered = renderer.render(renderer.parse(NotePreviewRewrite.forPreview(markdown))) as Spanned
    val image = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable
    image.setCallback2(object : android.graphics.drawable.Drawable.Callback {
      override fun invalidateDrawable(who: android.graphics.drawable.Drawable) = Unit
      override fun scheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable, `when`: Long) = Unit
      override fun unscheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable) = Unit
    })
    renderer.configuration().asyncDrawableLoader().load(image)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = android.os.SystemClock.uptimeMillis() + 5_000
    while (!image.hasResult() && android.os.SystemClock.uptimeMillis() < deadline) {
      instrumentation.waitForIdleSync()
      Thread.sleep(10)
    }
    assertTrue("the project image must decode without network access", image.hasResult())
    val bitmap = (image.result as? BitmapDrawable)?.bitmap
    assertNotNull("the offline preview must produce a bitmap", bitmap)
    assertEquals(Color.MAGENTA, requireNotNull(bitmap).getPixel(0, 0))
  }

  private fun imageCount(): Int = testResolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_IMAGE_COUNT,
    null,
    null,
  )?.getInt(ExistingProjectSafTestProvider.EXTRA_COUNT) ?: -1

  private fun assetsDirectoryExists(): Boolean = testResolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_IMAGE_COUNT,
    null,
    null,
  )?.getBoolean("assetsDirectoryCreated") ?: false

  private fun findNoteEditor(): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val matches = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditTexts(matches)
      result.set(matches.firstOrNull { it.tag == "note_markdown_editor" })
    }
    return result.get()
  }

  private fun View.collectEditTexts(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
  private val selectedImageUri: Uri
    get() = DocumentsContract.buildDocumentUri(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.IMAGE_DOCUMENT_ID,
    )
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}
