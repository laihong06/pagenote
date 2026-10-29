package io.pagenote.android

import android.os.Bundle
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RecentProjectsActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun recentProjectReopensAfterDirtyNoteSaveAndRemovalKeepsProject() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val treeUri = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
    installFixture(resolver)
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    openFixtureProjectFromBookshelf(composeRule, treeUri, opened.name)
    val originalNotes = ProjectNoteStore(resolver, opened.notesUri).read()
    val target = ProjectOpenTarget(treeUri, ExistingProjectSafTestProvider.ROOT_ID, false)
    val entry = RecentProjectEntry(
      accessTreeUri = treeUri.toString(),
      rootDocumentId = ExistingProjectSafTestProvider.ROOT_ID,
      projectId = opened.manifest.id,
      name = opened.manifest.name,
      lastOpenedAtMillis = 1_800_000_000_000,
    )
    val store = recentProjectsStore(context)
    runBlocking {
      store.recentProjects.first().forEach { store.remove(it) }
    }

    revealReaderMenuCommand(composeRule, "file", "recent_documents")
    composeRule.onNodeWithTag("reader_menu_command_recent_documents").performScrollTo().performClick()
    composeRule.onNodeWithTag("recent_projects_empty").assertIsDisplayed()
    composeRule.onNodeWithTag("recent_projects_open_picker").assertIsDisplayed()
    runBlocking { store.record(entry) }
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("recent_project_open_0").fetchSemanticsNodes().isNotEmpty()
    }

    val renderer = runBlocking { PdfDocumentSession.open(context, opened.sourceUri, context.cacheDir) }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    val dirtyPage = "# Saved before switching"
    composeRule.runOnIdle {
      session.project.value = opened.copy(
        manifest = opened.manifest.copy(id = "b".repeat(64), name = "Current dirty project"),
      )
      session.replaceRenderer(renderer)
      session.markdownSource.value = null
      session.notesMarkdown.value = originalNotes
      session.noteDraft.value = dirtyPage
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(originalNotes, 1))
      session.noteWritable.value = true
    }
    assertEquals(dirtyPage, session.noteDraft.value)
    assertTrue(session.noteDraft.value != NotePage.pageAt(session.notesMarkdown.value, 1))

    composeRule.onNodeWithTag("recent_project_open_0").performClick()
    composeRule.waitUntil(20_000) {
      session.project.value?.manifest?.id == opened.manifest.id && !session.isOpening.value
    }
    val savedNotes = ProjectNoteStore(resolver, opened.notesUri).read()
    assertEquals(dirtyPage, NotePage.pageAt(savedNotes, 1))
    assertEquals(opened.manifest.name, session.project.value?.manifest?.name)

    revealReaderMenuCommand(composeRule, "file", "recent_documents")
    composeRule.onNodeWithTag("reader_menu_command_recent_documents").performScrollTo().performClick()
    composeRule.onNodeWithTag("recent_project_remove_0").performClick()
    composeRule.waitUntil(10_000) {
      composeRule.onAllNodesWithTag("recent_projects_empty").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(opened.manifest.id, ProjectDocumentOpener(resolver).open(target.accessTreeUri, target.rootDocumentId).manifest.id)
    assertTrue(ProjectNoteStore(resolver, opened.notesUri).read().contains(dirtyPage))
    composeRule.onNodeWithTag("recent_projects_cancel").performClick()
    composeRule.runOnIdle {
      session.replaceRenderer(null)
      session.project.value = null
    }
  }

  private fun installFixture(resolver: android.content.ContentResolver) {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"Recent project fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    val notes = "# Original note".toByteArray(Charsets.UTF_8)
    val reviews = "[]".toByteArray(Charsets.UTF_8)
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalPdf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, reviews)
      },
    )
  }

  private fun minimalPdf(): ByteArray {
    val output = ByteArrayOutputStream()
    val offsets = mutableListOf(0)
    output.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
    val objects = listOf(
      "<< /Type /Catalog /Pages 2 0 R >>",
      "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> /Contents 4 0 R >>",
      "<< /Length 0 >>\nstream\n\nendstream",
    )
    objects.forEachIndexed { index, body ->
      offsets += output.size()
      output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    val xrefOffset = output.size()
    output.write("xref\n0 ${offsets.size}\n0000000000 65535 f \n".toByteArray(Charsets.US_ASCII))
    offsets.drop(1).forEach { offset ->
      output.write("%010d 00000 n \n".format(offset).toByteArray(Charsets.US_ASCII))
    }
    output.write(
      "trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        .toByteArray(Charsets.US_ASCII),
    )
    return output.toByteArray()
  }
}
