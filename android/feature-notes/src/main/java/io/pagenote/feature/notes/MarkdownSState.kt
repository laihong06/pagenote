package io.pagenote.feature.notes

import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class MarkdownSState(
  val version: Int,
  val id: String,
  val kind: String,
  val genes: List<Int>,
  val position: Double?,
  val generation: Int?,
  val paused: Boolean?,
  val angle: Double?,
  val cut: Int?,
  val selectedIndividual: Int?,
  val seed: Long?,
  val mutationRate: Double?,
  val wheelAngle: Double?,
  val extraFields: Map<String, JsonElement>,
) {
  fun toJsonElement(): JsonObject = buildJsonObject {
    extraFields.forEach { (key, value) -> put(key, value) }
    put("version", version)
    put("id", id)
    put("kind", kind)
    put("genes", JsonArray(genes.map(::JsonPrimitive)))
    position?.let { put("position", it) }
    generation?.let { put("generation", it) }
    paused?.let { put("paused", it) }
    angle?.let { put("angle", it) }
    cut?.let { put("cut", it) }
    selectedIndividual?.let { put("selectedIndividual", it) }
    seed?.let { put("seed", it) }
    mutationRate?.let { put("mutationRate", it) }
    wheelAngle?.let { put("wheelAngle", it) }
  }

  fun encode(): String = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), toJsonElement())

  companion object {
    const val MAX_SOURCE_BYTES = 200 * 1024
    private val knownFields = setOf(
      "version", "id", "kind", "genes", "position", "generation", "paused", "angle",
      "cut", "selectedIndividual", "seed", "mutationRate", "wheelAngle",
    )

    fun parse(source: String): MarkdownSState {
      if (source.toByteArray(StandardCharsets.UTF_8).size > MAX_SOURCE_BYTES) {
        throw MarkdownSInvalidStateException("原生数据块超过 200 KiB")
      }
      try {
        val fields = Json.parseToJsonElement(source).jsonObject
        fun number(key: String): Double? = fields[key]?.jsonPrimitive?.doubleOrNull
          ?: if (fields.containsKey(key)) invalid("$key 必须为有限数值") else null
        fun integer(key: String): Int? = fields[key]?.jsonPrimitive?.intOrNull
          ?: if (fields.containsKey(key)) invalid("$key 必须为整数") else null
        fun bool(key: String): Boolean? = fields[key]?.jsonPrimitive?.booleanOrNull
          ?: if (fields.containsKey(key)) invalid("$key 必须为布尔值") else null
        val version = integer("version") ?: invalid("需要 version:1")
        val idValue = fields["id"]?.jsonPrimitive
        val kindValue = fields["kind"]?.jsonPrimitive
        val id = idValue?.takeIf { it.isString }?.content ?: invalid("需要唯一 id")
        val kind = kindValue?.takeIf { it.isString }?.content ?: invalid("需要 kind:genetic-lab")
        val genes = fields["genes"]?.jsonArray?.map { it.jsonPrimitive.intOrNull ?: invalid("genes 仅允许 0 或 1") }
          ?: invalid("需要 12 个二进制 genes")
        val state = MarkdownSState(
          version, id, kind, genes,
          number("position"), integer("generation"), bool("paused"), number("angle"),
          integer("cut"), integer("selectedIndividual"), fields["seed"]?.jsonPrimitive?.longOrNull
            ?: if (fields.containsKey("seed")) invalid("seed 必须为无符号 32 位整数") else null,
          number("mutationRate"), number("wheelAngle"), fields.filterKeys { it !in knownFields },
        )
        state.validate()
        return state
      } catch (error: MarkdownSInvalidStateException) {
        throw error
      } catch (error: Exception) {
        throw MarkdownSInvalidStateException(error.message ?: "JSON 格式无效", error)
      }
    }

    private fun invalid(message: String): Nothing = throw MarkdownSInvalidStateException(message)
  }

  fun validate() {
    requireState(version == 1, "仅支持 version:1")
    requireState(id.isNotBlank() && id.length <= 80, "id 必须为 1–80 字符")
    requireState(kind == "genetic-lab", "仅支持 kind:genetic-lab")
    requireState(genes.size == 12 && genes.all { it == 0 || it == 1 }, "需要 12 个二进制 genes")
    position?.let { requireState(it.isFinite() && it in 0.0..100.0, "position 超出有效范围") }
    generation?.let { requireState(it in 0..40, "generation 超出有效范围") }
    angle?.let { requireState(it.isFinite(), "angle 必须为有限数值") }
    cut?.let { requireState(it in 1..11, "cut 超出有效范围") }
    selectedIndividual?.let { requireState(it in 0..35, "selectedIndividual 超出有效范围") }
    seed?.let { requireState(it in 0L..4_294_967_295L, "seed 超出有效范围") }
    mutationRate?.let { requireState(it.isFinite() && it in 0.0..0.3, "mutationRate 超出有效范围") }
    wheelAngle?.let { requireState(it.isFinite(), "wheelAngle 必须为有限数值") }
  }
}

data class MarkdownSParseResult(val state: MarkdownSState?, val error: String?)

class MarkdownSInvalidStateException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

private fun requireState(condition: Boolean, message: String) {
  if (!condition) throw MarkdownSInvalidStateException(message)
}
