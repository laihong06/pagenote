package io.pagenote.feature.annotations

import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Document-space rectangle hit testing over raw annotations.json items
 * (port batch 1). Pure JVM; mirrors the per-type semantics of feature-pen's
 * `PenRectHit` over the Json shape instead of `PenStroke`:
 *
 * - `pen`/`highlight` (and unknown future types): any point inside the rect,
 *   or any segment between consecutive points crossing it;
 * - `highlight` rect shorthand (no `points`): bounding-box overlap of the
 *   four rect numbers;
 * - `line`/`arrow`: the start→end segment (arrow head ignored);
 * - `rect`: any of the four border segments;
 * - `ellipse`/`image`/`html`: bounding-box overlap (approximation for the
 *   ellipse outline);
 * - `text`: the anchor point.
 *
 * Items whose geometry cannot be parsed never intersect.
 */
object AnnotationGeometry {

  /** Indexes (oldest first) of items in [items] that touch the rect. */
  fun itemsIntersectingRect(
    items: JsonArray,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
  ): List<Int> {
    val l = min(left, right)
    val r = max(left, right)
    val t = min(top, bottom)
    val b = max(top, bottom)
    return items.indices.filter { index ->
      (items[index] as? JsonObject)?.let { itemIntersects(it, l, t, r, b) } == true
    }
  }

  private fun itemIntersects(item: JsonObject, l: Double, t: Double, r: Double, b: Double): Boolean {
    val type = item.stringField("type")
    val points = item.pointsField()
    if (points.isEmpty()) {
      // Highlight rect shorthand: treat the four numbers as a bounding box.
      val rect = item.rectField() ?: return false
      return overlappingRanges(rect.first, rect.third, l, r) &&
        overlappingRanges(rect.second, rect.fourth, t, b)
    }
    return when (type) {
      "line", "arrow" -> segmentIntersects(points.first(), points.last(), l, t, r, b)
      "rect" -> {
        val start = points.first()
        val end = points.last()
        val x1 = min(start.first, end.first)
        val x2 = max(start.first, end.first)
        val y1 = min(start.second, end.second)
        val y2 = max(start.second, end.second)
        listOf(
          x1 to y1, x2 to y1,
          x2 to y2, x1 to y2,
        ).let { corners ->
          (corners + corners.first()).zipWithNext().any { (c1, c2) ->
            segmentIntersects(c1, c2, l, t, r, b)
          }
        }
      }
      "ellipse", "image", "html" -> {
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        overlappingRanges(xs.min(), xs.max(), l, r) && overlappingRanges(ys.min(), ys.max(), t, b)
      }
      "text" -> pointInside(points[0].first, points[0].second, l, t, r, b)
      else -> polylineIntersects(points, l, t, r, b)
    }
  }

  private fun overlappingRanges(a1: Double, a2: Double, b1: Double, b2: Double): Boolean =
    min(a1, a2) <= max(b1, b2) && max(a1, a2) >= min(b1, b2)

  private fun pointInside(x: Double, y: Double, l: Double, t: Double, r: Double, b: Double): Boolean =
    x >= l && x <= r && y >= t && y <= b

  private fun polylineIntersects(
    points: List<Pair<Double, Double>>,
    l: Double,
    t: Double,
    r: Double,
    b: Double,
  ): Boolean {
    if (points.any { pointInside(it.first, it.second, l, t, r, b) }) return true
    return points.zipWithNext().any { (a, c) -> segmentIntersects(a, c, l, t, r, b) }
  }

  private fun segmentIntersects(
    a: Pair<Double, Double>,
    c: Pair<Double, Double>,
    l: Double,
    t: Double,
    r: Double,
    b: Double,
  ): Boolean {
    if (pointInside(a.first, a.second, l, t, r, b)) return true
    if (pointInside(c.first, c.second, l, t, r, b)) return true
    val corners = listOf(l to t, r to t, r to b, l to b)
    return corners.zipWithNext().any { (e1, e2) ->
      segmentsIntersect(a.first, a.second, c.first, c.second, e1.first, e1.second, e2.first, e2.second)
    }
  }

  private fun segmentsIntersect(
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
    x3: Double,
    y3: Double,
    x4: Double,
    y4: Double,
  ): Boolean {
    fun orientation(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Int {
      val value = (by - ay) * (cx - bx) - (bx - ax) * (cy - by)
      if (value > 0.0) return 1
      if (value < 0.0) return -1
      return 0
    }

    fun onSegment(
      ax: Double,
      ay: Double,
      bx: Double,
      by: Double,
      px: Double,
      py: Double,
    ): Boolean = px in min(ax, bx)..max(ax, bx) && py in min(ay, by)..max(ay, by)

    val o1 = orientation(x1, y1, x2, y2, x3, y3)
    val o2 = orientation(x1, y1, x2, y2, x4, y4)
    val o3 = orientation(x3, y3, x4, y4, x1, y1)
    val o4 = orientation(x3, y3, x4, y4, x2, y2)
    if (o1 != o2 && o3 != o4) return true
    if (o1 == 0 && onSegment(x1, y1, x2, y2, x3, y3)) return true
    if (o2 == 0 && onSegment(x1, y1, x2, y2, x4, y4)) return true
    if (o3 == 0 && onSegment(x3, y3, x4, y4, x1, y1)) return true
    if (o4 == 0 && onSegment(x3, y3, x4, y4, x2, y2)) return true
    return false
  }

  private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

  private fun JsonObject.pointsField(): List<Pair<Double, Double>> = runCatching {
    (this["points"] as? JsonArray)?.mapNotNull { point ->
      val pair = point.jsonArray
      val x = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
      val y = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
      x to y
    }?.takeIf { it.isNotEmpty() }
  }.getOrNull() ?: emptyList()

  private fun JsonObject.rectField(): Quadruple? {
    val rect = (this["rect"] as? JsonArray) ?: return null
    if (rect.size != 4) return null
    val values = rect.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
    if (values.size != 4) return null
    return Quadruple(values[0], values[1], values[2], values[3])
  }

  private data class Quadruple(
    val first: Double,
    val second: Double,
    val third: Double,
    val fourth: Double,
  )
}
