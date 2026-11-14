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
  internal const val PREVIEW_FIELD = "pagenoteAreaEraserPreview"

  fun erasePage(page: JsonArray, from: PenPoint, to: PenPoint, radius: Float): JsonArray {
    val result = ArrayList<JsonElement>(page.size)
    var changed = false
    page.forEach { element ->
      val item = element as? JsonObject
      val type = item?.get("type")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      val nativeShape = type in nativeShapeTypes
      val isPreviewFragment = type == "pen" && item?.get(PREVIEW_FIELD)
        ?.let { (it as? JsonPrimitive)?.booleanOrNull } == true
      val stroke = if (nativeShape || isPreviewFragment) item?.toStroke() else null
      if (stroke == null) {
        result.add(element)
        return@forEach
      }

      val pieces = PenAreaEraser.eraseReferenceMaterial(stroke, from, to, radius)
      if (pieces.size == 1 && pieces.single() == stroke) {
        result.add(element)
        return@forEach
      }

      changed = true
      pieces.filter { it.points.size >= 2 }.forEach { piece ->
        val fields = piece.toAnnotationsItem().mapValues { (_, value) -> value.toJsonElement() }
          .toMutableMap()
        fields[PREVIEW_FIELD] = JsonPrimitive(true)
        result.add(JsonObject(fields))
      }
    }
    return if (changed) JsonArray(result) else page
  }

  /**
   * Commits transient native-shape fragments as ordinary multi-point Pen items,
   * combining them with the current freehand layer for one grouped history edit.
   */
  fun preparePageForCommit(page: JsonArray, currentPens: List<PenStroke>): JsonArray {
    val retained = mutableListOf<JsonElement>()
    val fragments = mutableListOf<JsonElement>()
    page.forEach { element ->
      val item = element as? JsonObject
      val type = item?.get("type")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      val isPreviewFragment = item?.get(PREVIEW_FIELD)
        ?.let { (it as? JsonPrimitive)?.booleanOrNull } == true
      when {
        isPreviewFragment -> fragments.add(
          JsonObject(item.filterKeys { it != PREVIEW_FIELD }),
        )
        type == "pen" || type == "highlight" -> Unit
        else -> retained.add(element)
      }
    }
    return JsonArray(
      retained + currentPens.map { stroke ->
        JsonObject(stroke.toAnnotationsItem().mapValues { (_, value) -> value.toJsonElement() })
      } + fragments,
    )
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

  private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Map<*, *> -> JsonObject(entries.mapNotNull { (key, value) ->
      (key as? String)?.let { it to value.toJsonElement() }
    }.toMap())
    else -> JsonPrimitive(toString())
  }
}
