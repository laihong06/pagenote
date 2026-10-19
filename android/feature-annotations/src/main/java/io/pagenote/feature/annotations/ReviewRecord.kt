package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReviewRecord(
  val id: String,
  val number: Int,
  val side: String,
  val kind: String,
  val page: Int,
  val scope: String,
  val instruction: String,
  val quote: String = "",
  val context: String = "",
  val revision: String? = null,
  val anchor: kotlinx.serialization.json.JsonElement? = null,
  val status: String,
  val created: String,
  val updated: String? = null,
  val boardPosition: Pair<Double, Double>? = null,
)

/** Typed view over the ReviewStore JsonObject records; unknown fields are ignored. */
object ReviewRecords {
  fun fromJson(record: JsonObject): ReviewRecord {
    fun text(key: String, default: String = ""): String =
      record[key]?.jsonPrimitive?.contentOrNull ?: default
    val board = record["boardPosition"] as? JsonObject
    return ReviewRecord(
      id = text("id"),
      number = record["number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
      side = text("side"),
      kind = text("kind"),
      page = record["page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
      scope = text("scope", "page"),
      instruction = text("instruction"),
      quote = text("quote"),
      context = text("context"),
      revision = (record["revision"] as? JsonPrimitive)?.contentOrNull,
      anchor = record["anchor"]?.takeUnless { it is JsonNull },
      status = text("status", "open"),
      created = text("created"),
      updated = (record["updated"] as? JsonPrimitive)?.contentOrNull,
      boardPosition = board?.let {
        val x = it["x"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val y = it["y"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (x != null && y != null) x to y else null
      },
    )
  }

  fun listFromJson(records: JsonArray): List<ReviewRecord> =
    records.mapNotNull { (it as? JsonObject)?.let(::fromJson) }

  fun toJson(record: ReviewRecord): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(record.id))
    put("number", JsonPrimitive(record.number))
    put("side", JsonPrimitive(record.side))
    put("kind", JsonPrimitive(record.kind))
    put("page", JsonPrimitive(record.page))
    put("scope", JsonPrimitive(record.scope))
    put("instruction", JsonPrimitive(record.instruction))
    put("quote", JsonPrimitive(record.quote))
    put("context", JsonPrimitive(record.context))
    if (record.revision != null) put("revision", JsonPrimitive(record.revision))
    else put("revision", JsonNull)
    if (record.anchor != null) put("anchor", record.anchor)
    else put("anchor", JsonNull)
    put("status", JsonPrimitive(record.status))
    put("created", JsonPrimitive(record.created))
    record.updated?.let { put("updated", JsonPrimitive(it)) }
    record.boardPosition?.let { (x, y) ->
      put("boardPosition", buildJsonObject {
        put("x", JsonPrimitive(x))
        put("y", JsonPrimitive(y))
      })
    }
  }
}

object ReviewValidation {
  fun validate(side: String, kind: String, page: Int, instruction: String) {
    require(side == "document" || side == "note") { "bad side" }
    require(kind == "overall" || kind == "selection" || kind == "region") { "bad kind" }
    require(page >= 1) { "bad page" }
    require(instruction.isNotBlank() && instruction.length <= 20000) { "bad instruction" }
  }

  fun canApply(status: String): Boolean = status == "open"
}
