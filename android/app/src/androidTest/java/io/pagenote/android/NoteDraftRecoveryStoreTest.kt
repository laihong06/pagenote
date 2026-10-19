package io.pagenote.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NoteDraftRecoveryStoreTest {
  @Test
  fun atomicJournalSurvivesStoreRecreationAndClearsOnlyItsIdentity() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.cacheDir, "draft-recovery-${System.nanoTime()}")
    val first = record(page = 1)
    val second = record(page = 2)
    try {
      AtomicFileNoteDraftRecoveryStore(directory).write(first)
      AtomicFileNoteDraftRecoveryStore(directory).write(second)

      val reopened = AtomicFileNoteDraftRecoveryStore(directory)
      assertEquals(first, reopened.read(first.key))
      assertEquals(second, reopened.read(second.key))
      assertEquals(setOf(1, 2), reopened.findForProject(
        first.accessTreeUri, first.rootDocumentId, first.projectId,
      ).map(NoteDraftRecoveryRecord::page).toSet())
      assertEquals(emptyList<NoteDraftRecoveryRecord>(), reopened.findForProject(
        first.accessTreeUri, first.rootDocumentId, "another-project",
      ))

      reopened.clear(first.key)

      assertNull(reopened.read(first.key))
      assertEquals(second, reopened.read(second.key))
    } finally {
      directory.deleteRecursively()
    }
  }

  private fun record(page: Int) = NoteDraftRecoveryRecord(
    accessTreeUri = "content://provider/tree/session",
    rootDocumentId = "root-id",
    projectId = "project-id",
    page = page,
    draft = "unsaved page $page",
    baseRevision = PageNotes.revision("saved page $page"),
  )
}
