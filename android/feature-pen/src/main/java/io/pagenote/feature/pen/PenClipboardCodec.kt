package io.pagenote.feature.pen

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Desktop-compatible system clipboard payload for one native PDF ink item. */
object PenClipboardCodec {
  const val MARKER = "PAGE_NOTES_ANNOTATION:\n"
  const val MAX_PAYLOAD_CHARS = 1_000_000

  private val json = Json
  private val supportedTypes = setOf("pen", "highlight")

  fun encode(stroke: PenStroke): String {
    require(stroke.type in supportedTypes) { "unsupported clipboard annotation type" }
    PenValidation.validate(stroke, 1)
    val fields = stroke.toAnnotationsItem()
      .filterKeys { it != "layoutAnchor" }
      .mapValues { (_, value) -> value.toJsonElement() }
    val payload = MARKER + json.encodeToString(JsonObject.serializer(), JsonObject(fields))
    require(payload.length <= MAX_PAYLOAD_CHARS) { "clipboard annotation is too large" }
    return payload
  }

  /** Returns null for unmarked, unsupported, malformed, oversized, or invalid data. */
  fun decode(text: String, destination: PenPoint): PenStroke? {
    if (text.length > MAX_PAYLOAD_CHARS || !text.startsWith(MARKER)) return null
    if (!destination.x.isFinite() || !destination.y.isFinite() ||
      kotlin.math.abs(destination.x) > PenValidation.MAX_COORD_ABS ||
      kotlin.math.abs(destination.y) > PenValidation.MAX_COORD_ABS
    ) return null

    return runCatching {
      val item = json.parseToJsonElement(text.substring(MARKER.length)).jsonObject
      val type = item.requiredString("type")
      require(type in supportedTypes)
      val color = item.requiredString("color")
      val width = item.requiredDouble("width")
      val rawPoints = item.getValue("points").jsonArray
      require(rawPoints.isNotEmpty() && rawPoints.size <= PenValidation.MAX_POINTS)
      val originalPoints = rawPoints.mapIndexed { index, rawPoint ->
        val pair = rawPoint.jsonArray
        require(pair.size >= 2)
        val pressure = item["pressures"]?.jsonArray?.getOrNull(index)
          ?.jsonPrimitive?.doubleOrNull?.toFloat() ?: PenValidation.DEFAULT_PRESSURE
        PenPoint(pair[0].jsonPrimitive.doubleOrNull!!.toFloat(), pair[1].jsonPrimitive.doubleOrNull!!.toFloat(), pressure)
      }
      val first = originalPoints.first()
      val deltaX = destination.x - first.x
      val deltaY = destination.y - first.y
      val points = originalPoints.map { point ->
        point.copy(x = point.x + deltaX, y = point.y + deltaY)
      }
      val extraFields = item
        .filterKeys { it !in PenStroke.CANONICAL_ANNOTATION_FIELDS && it != "layoutAnchor" }
        .mapValues { (_, value) -> value.toKotlinValue() }
      val stroke = PenStroke(
        type = type,
        color = color,
        width = width,
        points = points,
        text = item["text"]?.jsonPrimitive?.content,
        html = item["html"]?.jsonPrimitive?.content,
        url = item["url"]?.jsonPrimitive?.content,
        extraFields = extraFields,
      )
      PenValidation.validate(stroke, 1)
      stroke
    }.getOrNull()
  }

  private fun JsonObject.requiredString(key: String): String =
    getValue(key).jsonPrimitive.content

  private fun JsonObject.requiredDouble(key: String): Double =
    getValue(key).jsonPrimitive.doubleOrNull ?: error("invalid number: $key")

  private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
      this@toJsonElement.forEach { (key, value) ->
        require(key is String)
        put(key, value.toJsonElement())
      }
    }
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> error("unsupported clipboard annotation metadata")
  }

  private fun JsonElement.toKotlinValue(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: content.toDoubleOrNull() ?: content
    is JsonArray -> map { it.toKotlinValue() }
    is JsonObject -> mapValues { (_, value) -> value.toKotlinValue() }
  }
}
