package io.pagenote.android

import io.pagenote.core.project.PageNotes

internal data class AgentNoteReconciliation(
  val draft: String,
  val baseRevision: String,
  val conflict: Boolean,
)

/** Preserve unsaved edits and their revision when the agent changes the same page. */
internal fun reconcileAgentNote(
  savedPage: String,
  draft: String,
  latestPage: String,
  baseRevision: String,
): AgentNoteReconciliation = if (draft == savedPage) {
  AgentNoteReconciliation(latestPage, PageNotes.revision(latestPage), false)
} else {
  AgentNoteReconciliation(draft, baseRevision, savedPage != latestPage)
}
