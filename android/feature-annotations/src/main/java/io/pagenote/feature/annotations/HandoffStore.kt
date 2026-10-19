package io.pagenote.feature.annotations

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mirror of desktop ai-handoffs.json semantics (server.mjs GET/PUT handoffs/:page).
 * A handoff is stale when either stored revision disagrees with the live revision.
 */
data class HandoffRecord(
  val page: Int,
  val summary: String,
  val noteRevision: String,
  val sourceRevision: String,
  val updated: String?,
)

data class HandoffView(val record: HandoffRecord?, val stale: Boolean)

object HandoffStore {
  private val json = Json { ignoreUnknownKeys = true }
  private val HEX64 = Regex("^[a-f0-9]{64}$")

  fun readAll(file: File): Map<Int, HandoffRecord> {
    if (!file.exists()) return emptyMap()
    val root = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    val records = mutableMapOf<Int, HandoffRecord>()
    root.forEach { (key, value) ->
      val page = key.toIntOrNull() ?: return@forEach
      if (page < 1) return@forEach
      val entry = (value as? JsonObject) ?: return@forEach
      records[page] = HandoffRecord(
        page = page,
        summary = entry["summary"]?.jsonPrimitive?.content.orEmpty(),
        noteRevision = entry["noteRevision"]?.jsonPrimitive?.content.orEmpty(),
        sourceRevision = entry["sourceRevision"]?.jsonPrimitive?.content.orEmpty(),
        updated = entry["updated"]?.jsonPrimitive?.content,
      )
    }
    return records
  }

  /** GET semantics: missing handoff is not stale; otherwise any revision mismatch is stale. */
  fun view(file: File, page: Int, currentNoteRevision: String, currentSourceRevision: String): HandoffView {
    require(page >= 1) { "bad page" }
    val record = readAll(file)[page] ?: return HandoffView(null, false)
    val stale = record.noteRevision != currentNoteRevision || record.sourceRevision != currentSourceRevision
    return HandoffView(record, stale)
  }

  /**
   * PUT semantics: summary non-blank (<=4000), both revisions 64hex, and the caller's
   * expectedRevision must equal the live note revision, else reject as conflict.
   */
  fun write(
    srcDir: File,
    page: Int,
    summary: String,
    expectedRevision: String,
    sourceRevision: String,
    currentNoteRevision: String,
  ): HandoffRecord {
    require(page >= 1) { "bad page" }
    require(summary.isNotBlank() && summary.length <= 4000) { "bad summary" }
    require(HEX64.matches(expectedRevision)) { "bad expected revision" }
    require(HEX64.matches(sourceRevision)) { "bad source revision" }
    if (currentNoteRevision != expectedRevision) throw IllegalStateException("note changed, read again")
    val file = File(srcDir, "ai-handoffs.json")
    val records = readAll(file).toMutableMap()
    val record = HandoffRecord(
      page = page,
      summary = summary,
      noteRevision = currentNoteRevision,
      sourceRevision = sourceRevision,
      updated = java.time.Instant.now().toString(),
    )
    records[page] = record
    val root = buildJsonObject {
      records.toSortedMap().forEach { (key, value) ->
        put(key.toString(), buildJsonObject {
          put("summary", JsonPrimitive(value.summary))
          put("noteRevision", JsonPrimitive(value.noteRevision))
          put("sourceRevision", JsonPrimitive(value.sourceRevision))
          put("updated", JsonPrimitive(value.updated.orEmpty()))
        })
      }
    }
    atomicWrite(file, Json.encodeToString(JsonObject.serializer(), root))
    return record
  }

  /**
   * Delete only one page's handoff. Missing file or missing page is a no-op (false),
   * so cleanup never touches other pages.
   */
  fun deletePage(srcDir: File, page: Int): Boolean {
    require(page >= 1) { "bad page" }
    val file = File(srcDir, "ai-handoffs.json")
    if (!file.exists()) return false
    val records = readAll(file).toMutableMap()
    if (!records.containsKey(page)) return false
    records.remove(page)
    val root = buildJsonObject {
      records.toSortedMap().forEach { (key, value) ->
        put(key.toString(), buildJsonObject {
          put("summary", JsonPrimitive(value.summary))
          put("noteRevision", JsonPrimitive(value.noteRevision))
          put("sourceRevision", JsonPrimitive(value.sourceRevision))
          put("updated", JsonPrimitive(value.updated.orEmpty()))
        })
      }
    }
    atomicWrite(file, Json.encodeToString(JsonObject.serializer(), root))
    return true
  }

  private fun atomicWrite(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + "." + UUID.randomUUID() + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (!tmp.renameTo(file)) {
      file.writeText(text, Charsets.UTF_8)
      tmp.delete()
    }
  }
}
