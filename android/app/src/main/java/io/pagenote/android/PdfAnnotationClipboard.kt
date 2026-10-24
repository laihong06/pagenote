package io.pagenote.android

import io.pagenote.feature.annotations.AnnotationValidation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class PdfAnnotationBounds(
  val left: Double,
  val top: Double,
  val right: Double,
  val bottom: Double,
)

/** Desktop-compatible operations for the native overlay annotation types rendered by Android. */
internal object PdfAnnotationClipboard {
  const val MARKER = "PAGE_NOTES_ANNOTATION:\n"
  const val MAX_PAYLOAD_CHARS = 1_000_000
  private val supportedTypes = setOf("line", "arrow", "rect", "ellipse", "text", "image", "html")
  private val json = Json

  fun selectedIndexAfterDeletion(selectedIndex: Int?, deletedIndex: Int): Int? =
    selectedIndex?.let { selected ->
      when {
        selected == deletedIndex -> null
        selected > deletedIndex -> selected - 1
        else -> selected
      }
    }

  fun encode(item: JsonObject): String {
    require(isSupported(item)) { "unsupported annotation type" }
    val payload = MARKER + json.encodeToString(JsonObject.serializer(), item.withoutLayoutAnchor())
    require(payload.length <= MAX_PAYLOAD_CHARS) { "annotation clipboard payload is too large" }
    return payload
  }

  fun decode(payload: String): JsonObject? {
    if (payload.length > MAX_PAYLOAD_CHARS || !payload.startsWith(MARKER)) return null
    return runCatching {
      json.parseToJsonElement(payload.substring(MARKER.length)).jsonObjectOrNull()
        ?.takeIf(::isSupported)
    }.getOrNull()
  }

  fun pasteAt(item: JsonObject, x: Double = 80.0, y: Double = 90.0): JsonObject? {
    if (!x.isValidCoordinate() || !y.isValidCoordinate() || !isSupported(item)) return null
    val first = item.pagePoints()?.firstOrNull() ?: return null
    return translate(item, x - first.first, y - first.second)
      ?.withoutLayoutAnchor()
      ?.takeIf(::isValidItem)
  }

  fun translate(item: JsonObject, dx: Double, dy: Double): JsonObject? {
    if (!dx.isFinite() || !dy.isFinite() || !isSupported(item)) return null
    val points = item.pagePoints() ?: return null
    val translated = points.map { (x, y) -> (x + dx) to (y + dy) }
    if (translated.any { !it.first.isValidCoordinate() || !it.second.isValidCoordinate() }) return null
    return item.withPoints(translated).takeIf(::isValidItem)
  }

  fun resize(
    item: JsonObject,
    originalBounds: PdfAnnotationBounds,
    x: Double,
    y: Double,
  ): JsonObject? {
    if (!x.isValidCoordinate() || !y.isValidCoordinate() || !isSupported(item) || item.type() == "text") return null
    val width = (originalBounds.right - originalBounds.left).coerceAtLeast(0.1)
    val height = (originalBounds.bottom - originalBounds.top).coerceAtLeast(0.1)
    val scaleX = ((x - originalBounds.left) / width).coerceAtLeast(0.1)
    val scaleY = ((y - originalBounds.top) / height).coerceAtLeast(0.1)
    val resized = item.pagePoints()?.map { (pointX, pointY) ->
      (originalBounds.left + (pointX - originalBounds.left) * scaleX) to
        (originalBounds.top + (pointY - originalBounds.top) * scaleY)
    } ?: return null
    if (resized.any { !it.first.isValidCoordinate() || !it.second.isValidCoordinate() }) return null
    return item.withPoints(resized).takeIf(::isValidItem)
  }

  fun hitTest(page: JsonArray, x: Double, y: Double, tolerance: Double): Int? {
    if (!x.isFinite() || !y.isFinite() || !tolerance.isFinite() || tolerance < 0.0) return null
    return page.indices.reversed().firstOrNull { index ->
      val item = page[index] as? JsonObject ?: return@firstOrNull false
      if (!isSupported(item) || !isValidItem(item)) return@firstOrNull false
      val points = item.pagePoints() ?: return@firstOrNull false
      when (item.type()) {
        "line" -> distanceToSegment(x, y, points[0], points.getOrElse(1) { points[0] }) <=
          tolerance + item.width() / 2.0
        "arrow" -> {
          val end = points.getOrElse(1) { points[0] }
          val strokeTolerance = tolerance + item.width() / 2.0
          distanceToSegment(x, y, points[0], end) <= strokeTolerance ||
            arrowHeadPoints(item).any { wing -> distanceToSegment(x, y, end, wing) <= strokeTolerance }
        }
        "text" -> {
          val bounds = bounds(item) ?: return@firstOrNull false
          x >= bounds.left - tolerance && x <= bounds.right + tolerance &&
            y >= bounds.top - tolerance && y <= bounds.bottom + tolerance
        }
        "image" -> {
          val bounds = bounds(item) ?: return@firstOrNull false
          x in (bounds.left - tolerance)..(bounds.right + tolerance) &&
            y in (bounds.top - tolerance)..(bounds.bottom + tolerance)
        }
        "html" -> {
          val bounds = bounds(item) ?: return@firstOrNull false
          x in (bounds.left - tolerance)..(bounds.right + tolerance) &&
            y in (bounds.top - tolerance)..(bounds.bottom + tolerance)
        }
        "rect" -> rectangleBorderHit(item, x, y, tolerance)
        "ellipse" -> ellipseBorderHit(item, x, y, tolerance)
        else -> false
      }
    }
  }

  fun bounds(item: JsonObject): PdfAnnotationBounds? {
    if (!isSupported(item) || !isValidItem(item)) return null
    val points = item.pagePoints() ?: return null
    if (item.type() == "text") {
      val fontSize = maxOf(20.0, item.width() * 5.0)
      val lines = item["text"]?.jsonPrimitive?.content.orEmpty().split('\n')
      val textWidth = (lines.maxOfOrNull(String::length) ?: 0) * fontSize * 0.62
      return PdfAnnotationBounds(
        left = points[0].first,
        top = points[0].second - fontSize,
        right = points[0].first + textWidth,
        bottom = points[0].second + (lines.size - 1) * fontSize * 1.4 + fontSize * 0.4,
      )
    }
    val geometryPoints = if (item.type() == "arrow") points.take(2) + arrowHeadPoints(item) else points.take(2)
    return PdfAnnotationBounds(
      left = geometryPoints.minOf { it.first },
      top = geometryPoints.minOf { it.second },
      right = geometryPoints.maxOf { it.first },
      bottom = geometryPoints.maxOf { it.second },
    )
  }

  fun updateSelectedStyle(
    page: JsonArray,
    index: Int,
    expected: JsonObject,
    color: String? = null,
    width: Double? = null,
  ): JsonArray? {
    val current = page.getOrNull(index) as? JsonObject ?: return null
    if (current != expected || !isSupported(current)) return null

    val changes = buildMap<String, JsonElement> {
      if (color != null) {
        val existingColor = (current["color"] as? JsonPrimitive)?.content
        if (existingColor?.equals(color, ignoreCase = true) != true) {
          put("color", JsonPrimitive(color.lowercase()))
        }
      }
      if (width != null) {
        val existingWidth = (current["width"] as? JsonPrimitive)?.doubleOrNull
        if (existingWidth != width) put("width", JsonPrimitive(width))
      }
    }
    if (changes.isEmpty()) return page

    val updated = JsonObject(current + changes)
    return JsonArray(page.mapIndexed { itemIndex, item ->
      if (itemIndex == index) updated else item
    })
  }

  private fun arrowHeadPoints(item: JsonObject): List<Pair<Double, Double>> {
    val points = item.pagePoints() ?: return emptyList()
    val start = points.firstOrNull() ?: return emptyList()
    val end = points.getOrElse(1) { start }
    val direction = atan2(end.second - start.second, end.first - start.first)
    val headLength = 14.0 + item.width() * 2.0
    return listOf(direction - 0.45, direction + 0.45).map { wingAngle ->
      (end.first - headLength * cos(wingAngle)) to
        (end.second - headLength * sin(wingAngle))
    }
  }

  private fun isSupported(item: JsonObject): Boolean {
    if (item.type() !in supportedTypes) return false
    if (item.type() == "html" && item.pagePoints()?.size != 2) return false
    return runCatching { AnnotationValidation.validateItems(JsonArray(listOf(item))) }.isSuccess
  }

  private fun isValidItem(item: JsonObject): Boolean =
    runCatching { AnnotationValidation.validateItems(JsonArray(listOf(item))) }.isSuccess

  private fun JsonObject.type(): String? = this["type"]?.let {
    runCatching { it.jsonPrimitive.content }.getOrNull()
  }

  private fun JsonObject.width(): Double = this["width"]?.let {
    runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull()
  } ?: 0.0

  private fun JsonObject.pagePoints(): List<Pair<Double, Double>>? = runCatching {
    this["points"]?.jsonArray?.map { point ->
      val coordinates = point.jsonArray
      coordinates[0].jsonPrimitive.doubleOrNull!! to coordinates[1].jsonPrimitive.doubleOrNull!!
    }?.takeIf { it.isNotEmpty() }
  }.getOrNull()

  private fun JsonObject.withPoints(points: List<Pair<Double, Double>>): JsonObject =
    buildJsonObject {
      this@withPoints.forEach { (key, value) ->
        if (key != "points") put(key, value)
      }
      put("points", buildJsonArray {
        points.forEach { (x, y) -> add(buildJsonArray { add(JsonPrimitive(x)); add(JsonPrimitive(y)) }) }
      })
    }

  private fun JsonObject.withoutLayoutAnchor(): JsonObject = buildJsonObject {
    this@withoutLayoutAnchor.forEach { (key, value) -> if (key != "layoutAnchor") put(key, value) }
  }

  private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

  private fun Double.isValidCoordinate(): Boolean = isFinite() && abs(this) <= 1_000_000.0

  private fun distanceToSegment(
    x: Double,
    y: Double,
    start: Pair<Double, Double>,
    end: Pair<Double, Double>,
  ): Double {
    val dx = end.first - start.first
    val dy = end.second - start.second
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 1e-8) return hypot(x - start.first, y - start.second)
    val progress = (((x - start.first) * dx + (y - start.second) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(x - (start.first + progress * dx), y - (start.second + progress * dy))
  }

  private fun rectangleBorderHit(item: JsonObject, x: Double, y: Double, tolerance: Double): Boolean {
    val points = item.pagePoints() ?: return false
    val second = points.getOrElse(1) { points.first() }
    val left = minOf(points[0].first, second.first)
    val right = maxOf(points[0].first, second.first)
    val top = minOf(points[0].second, second.second)
    val bottom = maxOf(points[0].second, second.second)
    val inset = tolerance + item.width() / 2.0
    val insideOuter = x in (left - tolerance)..(right + tolerance) && y in (top - tolerance)..(bottom + tolerance)
    val insideInterior = x in (left + inset)..(right - inset) && y in (top + inset)..(bottom - inset)
    return insideOuter && !insideInterior
  }

  private fun ellipseBorderHit(item: JsonObject, x: Double, y: Double, tolerance: Double): Boolean {
    val points = item.pagePoints() ?: return false
    val second = points.getOrElse(1) { points.first() }
    val radiusX = abs(second.first - points[0].first) / 2.0
    val radiusY = abs(second.second - points[0].second) / 2.0
    if (radiusX <= 0.0 || radiusY <= 0.0) {
      return distanceToSegment(x, y, points[0], second) <= tolerance + item.width() / 2.0
    }
    val centerX = (points[0].first + second.first) / 2.0
    val centerY = (points[0].second + second.second) / 2.0
    val radial = hypot((x - centerX) / radiusX, (y - centerY) / radiusY)
    return abs(radial - 1.0) * minOf(radiusX, radiusY) <= tolerance + item.width() / 2.0
  }
}

/** In-memory undo/redo snapshots isolated by project identity and document page. */
internal class PdfAnnotationEditHistory(private val maxEntries: Int = 80) {
  private data class PageState(
    val undo: ArrayDeque<JsonArray> = ArrayDeque(),
    val redo: ArrayDeque<JsonArray> = ArrayDeque(),
    var current: JsonArray,
  )

  private val pages = mutableMapOf<Pair<String, Int>, PageState>()

  fun remapPages(projectId: String, mapping: Map<Int, Int>) {
    val existing = pages.filterKeys { it.first == projectId }
    pages.keys.removeAll(existing.keys)
    existing.forEach { (key, state) -> mapping[key.second]?.let { pages[projectId to it] = state } }
  }

  fun record(projectId: String, page: Int, before: JsonArray, after: JsonArray) {
    require(projectId.isNotBlank() && page >= 1)
    if (before == after) return
    val key = projectId to page
    val state = pages.getOrPut(key) { PageState(current = before) }
    if (state.current != before) {
      state.undo.clear()
      state.redo.clear()
      state.current = before
    }
    state.undo.addLast(before)
    while (state.undo.size > maxEntries) state.undo.removeFirst()
    state.redo.clear()
    state.current = after
  }

  fun undo(projectId: String, page: Int): JsonArray? {
    val state = pages[projectId to page] ?: return null
    if (state.undo.isEmpty()) return null
    state.redo.addLast(state.current)
    state.current = state.undo.removeLast()
    return state.current
  }

  fun redo(projectId: String, page: Int): JsonArray? {
    val state = pages[projectId to page] ?: return null
    if (state.redo.isEmpty()) return null
    state.undo.addLast(state.current)
    state.current = state.redo.removeLast()
    return state.current
  }

  fun canUndo(projectId: String, page: Int): Boolean = pages[projectId to page]?.undo?.isNotEmpty() == true
  fun canRedo(projectId: String, page: Int): Boolean = pages[projectId to page]?.redo?.isNotEmpty() == true
}
