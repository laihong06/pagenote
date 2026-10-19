package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class HandoffSafView(val record: HandoffSafRecord?, val stale: Boolean)

class ProjectHandoffStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
  handoffsUri: Uri?,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private var handoffsUri: Uri? = handoffsUri
  private val HEX64 = Regex("^[a-f0-9]{64}$")

  fun view(page: Int, currentNoteRevision: String, currentSourceRevision: String): HandoffSafView {
    require(page >= 1) { "bad page" }
    val record = readAll()[page]
    return HandoffSafView(record, HandoffSafBoundary.isStale(record, currentNoteRevision, currentSourceRevision))
  }

  fun write(page: Int, summary: String, expectedRevision: String, sourceRevision: String, currentNoteRevision: String): HandoffSafRecord {
    require(page >= 1) { "bad page" }
    require(HandoffSafBoundary.validSummary(summary)) { "bad summary" }
    require(HEX64.matches(expectedRevision)) { "bad expected revision" }
    require(HEX64.matches(sourceRevision)) { "bad source revision" }
    if (currentNoteRevision != expectedRevision) throw IllegalStateException("note changed, read again")
    val uri = ensureFile()
    val records = readAll().toMutableMap()
    val record = HandoffSafRecord(page, summary, currentNoteRevision, sourceRevision)
    records[page] = record
    val root = buildJsonObject {
      records.toSortedMap().forEach { (key, value) ->
        put(key.toString(), buildJsonObject {
          put("summary", JsonPrimitive(value.summary))
          put("noteRevision", JsonPrimitive(value.noteRevision))
          put("sourceRevision", JsonPrimitive(value.sourceRevision))
          put("updated", JsonPrimitive(java.time.Instant.now().toString()))
        })
      }
    }
    resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write(Json.encodeToString(JsonObject.serializer(), root))
    } ?: throw IOException("无法写入项目 JSON 文件")
    return record
  }

  fun delete(page: Int): Boolean {
    require(page >= 1) { "bad page" }
    val uri = handoffsUri ?: return false
    val records = readAll().toMutableMap()
    if (!records.containsKey(page)) return false
    records.remove(page)
    val root = buildJsonObject {
      records.toSortedMap().forEach { (key, value) ->
        put(key.toString(), buildJsonObject {
          put("summary", JsonPrimitive(value.summary))
          put("noteRevision", JsonPrimitive(value.noteRevision))
          put("sourceRevision", JsonPrimitive(value.sourceRevision))
          put("updated", JsonPrimitive(java.time.Instant.now().toString()))
        })
      }
    }
    resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write(Json.encodeToString(JsonObject.serializer(), root))
    } ?: throw IOException("无法写入项目 JSON 文件")
    return true
  }

  private fun readAll(): Map<Int, HandoffSafRecord> {
    val uri = handoffsUri ?: return emptyMap()
    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("无法读取项目 JSON 文件")
    val root = try {
      json.parseToJsonElement(text).jsonObject
    } catch (error: IllegalArgumentException) {
      throw IOException("项目 JSON 文件格式无效", error)
    }
    val records = mutableMapOf<Int, HandoffSafRecord>()
    root.forEach { (key, value) ->
      val page = key.toIntOrNull() ?: return@forEach
      if (page < 1) return@forEach
      val entry = value as? JsonObject ?: return@forEach
      records[page] = HandoffSafRecord(
        page = page,
        summary = entry["summary"]?.jsonPrimitive?.content.orEmpty(),
        noteRevision = entry["noteRevision"]?.jsonPrimitive?.content.orEmpty(),
        sourceRevision = entry["sourceRevision"]?.jsonPrimitive?.content.orEmpty(),
      )
    }
    return records
  }

  private fun ensureFile(): Uri {
    handoffsUri?.let { return it }
    val created = DocumentsContract.createDocument(
      resolver, sourceDirectoryUri, "application/json", "ai-handoffs.json",
    ) ?: throw IOException("无法创建项目 JSON 文件")
    handoffsUri = created
    resolver.openOutputStream(created, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write("{}")
    } ?: throw IOException("无法写入项目 JSON 文件")
    return created
  }
}
