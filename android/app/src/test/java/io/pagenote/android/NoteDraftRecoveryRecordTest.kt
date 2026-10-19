package io.pagenote.android

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDraftRecoveryRecordTest {
  @Test
  fun recoveryIdentitySeparatesProjectsAndPages() {
    val base = record(page = 2)

    assertEquals(base.key, record(page = 2).key)
    assertEquals(false, base.key == record(page = 3).key)
    assertEquals(false, base.key == record(accessTreeUri = "content://other/tree").key)
    assertEquals(false, base.key == record(projectId = "other-project").key)
  }

  @Test
  fun savedDraftIsClassifiedAsAlreadyCommitted() {
    val savedPage = "already saved"
    val record = record(draft = savedPage, baseRevision = PageNotes.revision(savedPage))

    assertEquals(NoteDraftRecoveryDecision.ALREADY_SAVED, record.assess(savedPage))
  }

  @Test
  fun unchangedBaseRevisionRestoresDirtyDraft() {
    val savedPage = "saved page"
    val record = record(baseRevision = PageNotes.revision(savedPage))

    assertEquals(NoteDraftRecoveryDecision.RESTORE_DRAFT, record.assess(savedPage))
  }

  @Test
  fun changedBaseRevisionRetainsDraftAsConflict() {
    val record = record(baseRevision = PageNotes.revision("older saved page"))

    assertEquals(NoteDraftRecoveryDecision.RESTORE_CONFLICT, record.assess("newer saved page"))
  }

  private fun record(
    accessTreeUri: String = "content://provider/tree/project",
    projectId: String = "project-one",
    page: Int = 2,
    draft: String = "edited page",
    baseRevision: String = PageNotes.revision("saved page"),
  ) = NoteDraftRecoveryRecord(
    accessTreeUri = accessTreeUri,
    rootDocumentId = "project-root",
    projectId = projectId,
    page = page,
    draft = draft,
    baseRevision = baseRevision,
  )
}
