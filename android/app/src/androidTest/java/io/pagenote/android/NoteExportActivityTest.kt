package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteExportActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun exportsNotesAcrossFormatsFailuresAndActivityRecreation() {
    val originalPages = listOf("第一页旧稿", "邻页保持不变")
    val originalNotes = PageNotes.join(originalPages)
    val project = installProject(originalNotes)
    val session = attachProjectToReader(project, originalNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    val requestCount = AtomicInteger()
    val pending = AtomicReference<PickerRequest?>()
    val pickerHook: (String, String, (Uri?) -> Unit) -> Unit = { mimeType, fileName, callback ->
      val request = PickerRequest(mimeType, fileName, callback)
      requestCount.incrementAndGet()
      pending.set(request)
    }
    val hookField = MainActivity::class.java.getDeclaredField("noteExportPickerForTesting")
      .apply { isAccessible = true }
    composeRule.runOnUiThread { hookField.set(composeRule.activity, pickerHook) }

    val editedDraft = "第一页新稿：向量与特征"
    val editor = requireNotNull(findNoteEditor())
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setText(editedDraft)
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == editedDraft }

    exportCommand().assertIsEnabled().performClick()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.reader_menu_export_json))
      .performClick()
    val jsonRequest = awaitPickerRequest(pending)
    assertEquals("application/json", jsonRequest.mimeType)
    assertEquals("模式识别学习项目-逐页笔记.json", jsonRequest.fileName)
    composeRule.activityRule.scenario.recreate()
    composeRule.waitForIdle()
    composeRule.runOnUiThread { hookField.set(composeRule.activity, pickerHook) }
    assertEquals(jsonRequest.fileName, session.pendingNoteExport.value?.fileName)
    jsonRequest.callback(NoteExportSafTestProvider.uri("json"))

    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_export_success) &&
        session.pendingNoteExport.value == null
    }
    val backup = Json.parseToJsonElement(readExport("json")).jsonObject
    val exportedPages = backup.getValue("notes").jsonObject
    assertEquals("模式识别学习项目", backup.getValue("document").jsonPrimitive.content)
    assertEquals(2, backup.getValue("pages").jsonPrimitive.int)
    assertEquals(editedDraft, exportedPages.getValue("1").jsonPrimitive.content)
    assertEquals(originalPages[1], exportedPages.getValue("2").jsonPrimitive.content)

    exportCommand().performClick()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.reader_menu_export_current_page))
      .performClick()
    val markdownRequest = awaitPickerRequest(pending)
    assertEquals("text/markdown", markdownRequest.mimeType)
    assertEquals("模式识别学习项目-第1页.md", markdownRequest.fileName)
    markdownRequest.callback(NoteExportSafTestProvider.uri("markdown"))
    composeRule.waitUntil(timeoutMillis = 10_000) {
      requestCount.get() == 2 && session.noteStatus.value ==
        composeRule.activity.getString(R.string.reader_menu_export_success) &&
        session.pendingNoteExport.value == null
    }
    assertEquals(editedDraft, readExport("markdown"))

    val recoveredDraft = "只读恢复草稿"
    composeRule.runOnUiThread {
      session.noteWritable.value = false
      session.noteDraft.value = recoveredDraft
    }
    exportCommand().performClick()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.reader_menu_export_json))
      .performClick()
    val readOnlyRequest = awaitPickerRequest(pending)
    readOnlyRequest.callback(NoteExportSafTestProvider.uri("json"))
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_export_success) &&
        session.pendingNoteExport.value == null
    }
    val readOnlyBackup = Json.parseToJsonElement(readExport("json")).jsonObject
    assertEquals(
      recoveredDraft,
      readOnlyBackup.getValue("notes").jsonObject.getValue("1").jsonPrimitive.content,
    )

    val persisted = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(editedDraft, NotePage.pageAt(persisted, 1))
    assertEquals(originalPages[1], NotePage.pageAt(persisted, 2))

    exportCommand().performClick()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.reader_menu_export_json))
      .performClick()
    awaitPickerRequest(pending).callback(NoteExportSafTestProvider.uri("failure"))
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value != null && session.pendingNoteExport.value == null
    }
    assertNotEquals(
      composeRule.activity.getString(R.string.reader_menu_export_success),
      session.noteStatus.value,
    )
    val beforeCancel = readExport("json")

    exportCommand().performClick()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.reader_menu_export_json))
      .performClick()
    awaitPickerRequest(pending).callback(null)
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.pendingNoteExport.value == null && session.noteStatus.value == null
    }
    assertEquals(beforeCancel, readExport("json"))
    val unchangedAfterFailures = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(editedDraft, NotePage.pageAt(unchangedAfterFailures, 1))
    assertEquals(originalPages[1], NotePage.pageAt(unchangedAfterFailures, 2))

    val externalNotes = PageNotes.join(listOf("外部并发修改", originalPages[1]))
    composeRule.runOnUiThread {
      session.noteWritable.value = true
      session.noteDraft.value = "本地冲突草稿"
    }
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_REPLACE_NOTES,
      null,
      Bundle().apply { putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, externalNotes.toByteArray(Charsets.UTF_8)) },
    )
    val pickerCountBeforeConflict = requestCount.get()
    exportCommand().performClick()
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.reader_menu_export_json))
      .performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value != null && !session.isSaving.value
    }
    assertEquals(pickerCountBeforeConflict, requestCount.get())
    assertEquals("本地冲突草稿", session.noteDraft.value)
    assertEquals(externalNotes, ProjectNoteStore(testResolver, project.notesUri).read())
    assertNotNull(session.project.value)

    val processContext = InstrumentationRegistry.getInstrumentation().targetContext
    val pendingState = SavedStateHandle()
    val snapshot = NoteExportFile("text/markdown", "恢复测试.md", "进程重建后的笔记")
    val exportStore = NoteExportSnapshotStore(java.io.File(processContext.filesDir, "reader-note-export"))
    try {
      val writer = ReaderSessionViewModel(pendingState).apply { initializeRecovery(processContext) }
      runBlocking { writer.storePendingNoteExport(snapshot) }
      val copiedState = SavedStateHandle(
        pendingState.keys().associateWith { key -> pendingState.get<Any?>(key) },
      )
      val restored = ReaderSessionViewModel(copiedState).apply { initializeRecovery(processContext) }
      assertTrue(restored.isRestoringPendingNoteExport.value)
      assertTrue(restored.isPendingNoteExportRecovered.value)
      composeRule.waitUntil(timeoutMillis = 10_000) {
        restored.pendingNoteExport.value != null && !restored.isRestoringPendingNoteExport.value
      }
      assertEquals(snapshot, restored.pendingNoteExport.value)
    } finally {
      exportStore.clear()
    }

    val retrySnapshot = NoteExportFile("text/markdown", "模式识别-恢复.md", "重试导出的原快照")
    composeRule.runOnUiThread {
      session.launchSessionTask {
        session.storePendingNoteExport(retrySnapshot)
        session.isPendingNoteExportRecovered.value = true
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.pendingNoteExport.value == retrySnapshot && session.isPendingNoteExportRecovered.value
    }
    exportCommand().performClick()
    composeRule.onNodeWithText(
      composeRule.activity.getString(R.string.reader_menu_export_recovery_discard),
    ).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.pendingNoteExport.value == null && !session.isPendingNoteExportRecovered.value &&
        !session.isHandlingNoteExportResult.value
    }

    composeRule.runOnUiThread {
      session.launchSessionTask {
        session.storePendingNoteExport(retrySnapshot)
        session.isPendingNoteExportRecovered.value = true
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.pendingNoteExport.value == retrySnapshot && session.isPendingNoteExportRecovered.value
    }
    exportCommand().performClick()
    composeRule.onNodeWithText(
      composeRule.activity.getString(R.string.reader_menu_export_recovery_retry),
    ).performClick()
    val retryRequest = awaitPickerRequest(pending)
    assertEquals("text/markdown", retryRequest.mimeType)
    assertEquals(retrySnapshot.fileName, retryRequest.fileName)
    retryRequest.callback(NoteExportSafTestProvider.uri("markdown"))
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteStatus.value == composeRule.activity.getString(R.string.reader_menu_export_success) &&
        session.pendingNoteExport.value == null && !session.isHandlingNoteExportResult.value
    }
    assertEquals(retrySnapshot.content, readExport("markdown"))
  }

  private fun exportCommand() = run {
    val tag = "reader_menu_command_export_notes"
    if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_file").performClick()
      } else if (
        composeRule.onAllNodesWithTag("reader_menu_group_file").fetchSemanticsNodes().isNotEmpty()
      ) {
        composeRule.onNodeWithTag("reader_menu_group_file").performScrollTo()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(tag).performScrollTo()
  }

  private fun awaitPickerRequest(pending: AtomicReference<PickerRequest?>): PickerRequest {
    composeRule.waitUntil(timeoutMillis = 10_000) { pending.get() != null }
    return requireNotNull(pending.getAndSet(null))
  }

  private fun readExport(name: String): String =
    requireNotNull(testResolver.openInputStream(NoteExportSafTestProvider.uri(name)))
      .bufferedReader(Charsets.UTF_8)
      .use { it.readText() }

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

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = "原文第一页\n\n${PageNotes.SEPARATOR}\n\n原文第二页"
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    return session
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"e".repeat(64)}","name":"模式识别学习项目","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(
      DocumentsContract.buildTreeDocumentUri(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.ROOT_ID,
      ),
    )
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

  private data class PickerRequest(
    val mimeType: String,
    val fileName: String,
    val callback: (Uri?) -> Unit,
  )
}
