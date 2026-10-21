package io.pagenote.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object ReviewSafBoundary {
  private val STATUSES = setOf("open", "applied", "dismissed")

  fun blankRecord(id: String, status: String): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("status", JsonPrimitive(status))
  }

  fun canTransition(record: JsonObject, to: String): Boolean {
    if (to !in STATUSES) return false
    val current = (record["status"] as? JsonPrimitive)?.content ?: return false
    if (current !in STATUSES) return false
    if (current == to) return false
    if (current != "open") return false
    return true
  }
}

data class HandoffSafRecord(val page: Int, val summary: String, val noteRevision: String, val sourceRevision: String)

object HandoffSafBoundary {
  fun record(page: Int, summary: String, noteRevision: String, sourceRevision: String) =
    HandoffSafRecord(page, summary, noteRevision, sourceRevision)

  fun isStale(record: HandoffSafRecord?, currentNoteRevision: String, currentSourceRevision: String): Boolean {
    if (record == null) return false
    return record.noteRevision != currentNoteRevision || record.sourceRevision != currentSourceRevision
  }

  fun validSummary(summary: String): Boolean =
    summary.isNotBlank() && summary.length <= 4000
}
