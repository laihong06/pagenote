package io.pagenote.android

import io.pagenote.core.project.PageNotes
import java.security.MessageDigest

data class NoteDraftRecoveryKey(
  val accessTreeUri: String,
  val rootDocumentId: String,
  val projectId: String,
  val page: Int,
) {
  init {
    require(page >= 1)
  }

  internal fun fileName(): String {
    val identity = listOf(accessTreeUri, rootDocumentId, projectId, page.toString()).joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) } + ".json"
  }
}

data class NoteDraftRecoveryRecord(
  val accessTreeUri: String,
  val rootDocumentId: String,
  val projectId: String,
  val page: Int,
  val draft: String,
  val baseRevision: String,
  val updatedAtMillis: Long = System.currentTimeMillis(),
) {
  val key: NoteDraftRecoveryKey
    get() = NoteDraftRecoveryKey(accessTreeUri, rootDocumentId, projectId, page)

  fun assess(savedPage: String): NoteDraftRecoveryDecision = when {
    draft == savedPage -> NoteDraftRecoveryDecision.ALREADY_SAVED
    PageNotes.revision(savedPage) == baseRevision -> NoteDraftRecoveryDecision.RESTORE_DRAFT
    else -> NoteDraftRecoveryDecision.RESTORE_CONFLICT
  }
}

enum class NoteDraftRecoveryDecision {
  ALREADY_SAVED,
  RESTORE_DRAFT,
  RESTORE_CONFLICT,
}

interface NoteDraftRecoveryStore {
  fun read(key: NoteDraftRecoveryKey): NoteDraftRecoveryRecord?
  fun findForProject(accessTreeUri: String, rootDocumentId: String, projectId: String): List<NoteDraftRecoveryRecord>
  fun write(record: NoteDraftRecoveryRecord)
  fun clear(key: NoteDraftRecoveryKey)
}
