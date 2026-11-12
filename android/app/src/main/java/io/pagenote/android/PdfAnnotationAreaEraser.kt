package io.pagenote.android

import io.pagenote.feature.pen.PenAreaEraser
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Applies the pen module's swept eraser geometry to native vector annotations. */
internal object PdfAnnotationAreaEraser {
  private val nativeShapeTypes = setOf("line", "arrow", "rect", "ellipse")
  private val canonicalKeys = setOf("type", "color", "width", "points", "pressures", "text", "html", "url")

  fun erasePage(page: JsonArray, from: PenPoint, to: PenPoint, radius: Float): JsonArray {
    val result = ArrayList<JsonElement>(page.size)
    var changed = false
    page.forEach { element ->
      val item = element as? JsonObject
      val type = item?.get("type")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      val nativeShape = type in nativeShapeTypes
      val stroke = if (nativeShape) item?.toStroke() else null
      if (stroke == null) {
        result.add(element)
        return@forEach
      }

      val pieces = PenAreaEraser.erase(listOf(stroke), from, to, radius)
      if (pieces.size == 1 && pieces.single() == stroke) {
        result.add(element)
        return@forEach
      }

      changed = true
      pieces.forEach { piece ->
        piece.points.zipWithNext().forEach { (start, end) ->
          result.add(
            JsonObject(
              mapOf(
                "type" to JsonPrimitive("line"),
                "color" to JsonPrimitive(piece.color),
                "width" to JsonPrimitive(piece.width),
                "points" to JsonArray(
                  listOf(
                    JsonArray(listOf(JsonPrimitive(start.x), JsonPrimitive(start.y))),
                    JsonArray(listOf(JsonPrimitive(end.x), JsonPrimitive(end.y))),
                  ),
                ),
              ),
            ),
          )
        }
      }
    }
    return if (changed) JsonArray(result) else page
  }

  private fun JsonObject.toStroke(): PenStroke? {
    val type = (get("type") as? JsonPrimitive)?.content ?: return null
    val color = (get("color") as? JsonPrimitive)?.content ?: return null
    val width = (get("width") as? JsonPrimitive)?.doubleOrNull ?: return null
    val rawPoints = (get("points") as? JsonArray) ?: return null
    val points = rawPoints.mapNotNull { raw ->
      val pair = raw as? JsonArray ?: return@mapNotNull null
      val x = pair.getOrNull(0)?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: return@mapNotNull null
      val y = pair.getOrNull(1)?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: return@mapNotNull null
      PenPoint(x.toFloat(), y.toFloat(), 1f)
    }
    if (points.size < 2) return null
    val pressures = (get("pressures") as? JsonArray)?.mapNotNull {
      (it as? JsonPrimitive)?.doubleOrNull?.toFloat()
    }.orEmpty()
    val pressuredPoints = points.mapIndexed { index, point ->
      point.copy(pressure = pressures.getOrNull(index) ?: 1f)
    }
    val extensions = entries.asSequence().filter { (key, _) -> key !in canonicalKeys }
      .associate { (key, value) -> key to value.toNativeValue() }
    return PenStroke(
      type = type,
      color = color,
      width = width,
      points = pressuredPoints,
      text = (get("text") as? JsonPrimitive)?.content,
      html = (get("html") as? JsonPrimitive)?.content,
      url = (get("url") as? JsonPrimitive)?.content,
      extraFields = extensions,
    )
  }

  private fun JsonElement.toNativeValue(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> booleanOrNull ?: doubleOrNull ?: content
    is JsonArray -> map { it.toNativeValue() }
    is JsonObject -> entries.associate { (key, value) -> key to value.toNativeValue() }
  }
}
