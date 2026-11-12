package io.pagenote.feature.pen

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Cuts freehand centerlines against the swept eraser circle in document coordinates. */
object PenAreaEraser {
  private const val EPS = 0.00001
  private data class Interval(val start: Double, val end: Double)

  fun erase(strokes: List<PenStroke>, from: PenPoint, to: PenPoint, radius: Float): List<PenStroke> {
    require(radius.isFinite() && radius >= 0f)
    if (!from.x.isFinite() || !from.y.isFinite() || !to.x.isFinite() || !to.y.isFinite()) return strokes
    val result = ArrayList<PenStroke>()
    var changed = false
    for (stroke in strokes) {
      if (centerline(stroke) == null) { result.add(stroke); continue }
      val cut = split(stroke, from, to, radius.toDouble() + stroke.width / 2.0)
      if (cut.size != 1 || cut.firstOrNull() != stroke) changed = true
      result.addAll(cut)
      if (result.size > PenValidation.MAX_ITEMS_PER_PAGE) return strokes
    }
    return if (changed) result else strokes
  }

  private fun split(stroke: PenStroke, from: PenPoint, to: PenPoint, radius: Double): List<PenStroke> {
    val points = centerline(stroke) ?: return listOf(stroke)
    if (points.isEmpty()) return listOf(stroke)
    val minX = min(from.x, to.x) - radius
    val maxX = max(from.x, to.x) + radius
    val minY = min(from.y, to.y) - radius
    val maxY = max(from.y, to.y) + radius
    var left = Double.POSITIVE_INFINITY; var right = Double.NEGATIVE_INFINITY
    var top = Double.POSITIVE_INFINITY; var bottom = Double.NEGATIVE_INFINITY
    for (point in points) {
      left = min(left, point.x.toDouble()); right = max(right, point.x.toDouble())
      top = min(top, point.y.toDouble()); bottom = max(bottom, point.y.toDouble())
    }
    if (right < minX || left > maxX || bottom < minY || top > maxY) return listOf(stroke)
    if (points.size == 1) {
      return if (intervals(points[0], points[0], from, to, radius).isNotEmpty()) emptyList() else listOf(stroke)
    }
    val pieces = mutableListOf<MutableList<PenPoint>>()
    var current: MutableList<PenPoint>? = null
    var changed = false
    fun append(point: PenPoint) {
      val piece = current ?: mutableListOf<PenPoint>().also { pieces.add(it); current = it }
      if (piece.lastOrNull() != point) piece.add(point)
    }
    for (i in 0 until points.lastIndex) {
      val a = points[i]; val b = points[i + 1]
      val cuts = intervals(a, b, from, to, radius)
      if (cuts.isEmpty()) { append(a); append(b); continue }
      changed = true
      var cursor = 0.0
      for (cut in cuts) {
        if (cut.start > cursor + EPS) {
          append(interpolate(a, b, cursor)); append(interpolate(a, b, cut.start))
        }
        current = null
        cursor = max(cursor, cut.end)
      }
      if (cursor < 1.0 - EPS) { append(interpolate(a, b, cursor)); append(b) }
    }
    if (!changed) return listOf(stroke)
    val segments = pieces.filter { it.isNotEmpty() }
    val isNativeShape = stroke.type != "pen" && stroke.type != "highlight"
    return segments.map { piece ->
      if (isNativeShape) {
        stroke.copy(
          type = "pen",
          points = piece.toList(),
          extraFields = stroke.extraFields + (PenStroke.POLYLINE_FIELD to true),
        )
      } else stroke.copy(points = piece.toList())
    }
  }

  /** Geometry used for hit cutting; untouched shapes remain in their original native form. */
  private fun centerline(stroke: PenStroke): List<PenPoint>? {
    val points = stroke.points
    if (stroke.type == "pen" || stroke.type == "highlight") return points
    if (points.size < 2) return null
    val a = points.first()
    val b = points.last()
    return when (stroke.type) {
      "line" -> listOf(a, b)
      "arrow" -> {
        val angle = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        val length = (14.0 + stroke.width * 2.0).toFloat()
        val spread = .45
        val wingA = PenPoint(
          b.x - length * cos(angle - spread).toFloat(),
          b.y - length * sin(angle - spread).toFloat(), 1f,
        )
        val wingB = PenPoint(
          b.x - length * cos(angle + spread).toFloat(),
          b.y - length * sin(angle + spread).toFloat(), 1f,
        )
        listOf(a, b, wingA, b, wingB)
      }
      "rect" -> {
        val left = min(a.x, b.x); val right = max(a.x, b.x)
        val top = min(a.y, b.y); val bottom = max(a.y, b.y)
        listOf(
          PenPoint(left, top, 1f), PenPoint(right, top, 1f),
          PenPoint(right, bottom, 1f), PenPoint(left, bottom, 1f), PenPoint(left, top, 1f),
        )
      }
      "ellipse" -> {
        val centerX = (a.x + b.x) / 2f; val centerY = (a.y + b.y) / 2f
        val radiusX = kotlin.math.abs(b.x - a.x) / 2f
        val radiusY = kotlin.math.abs(b.y - a.y) / 2f
        (0..64).map { step ->
          val angle = step * 2.0 * Math.PI / 64.0
          PenPoint(
            centerX + radiusX * cos(angle).toFloat(),
            centerY + radiusY * sin(angle).toFloat(), 1f,
          )
        }
      }
      else -> null
    }
  }

  private fun interpolate(a: PenPoint, b: PenPoint, t: Double) = PenPoint(
    (a.x + (b.x - a.x) * t).toFloat(), (a.y + (b.y - a.y) * t).toFloat(),
    (a.pressure + (b.pressure - a.pressure) * t).toFloat().coerceIn(0f, 1f),
  )

  private fun intervals(a: PenPoint, b: PenPoint, from: PenPoint, to: PenPoint, r: Double): List<Interval> {
    val ax = a.x.toDouble(); val ay = a.y.toDouble()
    val dx = b.x.toDouble() - ax; val dy = b.y.toDouble() - ay
    val ex = to.x.toDouble() - from.x; val ey = to.y.toDouble() - from.y
    val length = sqrt(ex * ex + ey * ey)
    val cuts = mutableListOf<Interval>()
    fun circle(cx: Double, cy: Double) {
      val x = ax - cx; val y = ay - cy
      val aa = dx * dx + dy * dy; val bb = 2 * (x * dx + y * dy); val cc = x * x + y * y - r * r
      if (aa < EPS * EPS) { if (cc <= 0) cuts.add(Interval(0.0, 1.0)); return }
      val d = bb * bb - 4 * aa * cc
      if (d <= 0) return
      val root = sqrt(d); val start = max(0.0, (-bb - root) / (2 * aa)); val end = min(1.0, (-bb + root) / (2 * aa))
      if (end > start + EPS) cuts.add(Interval(start, end))
    }
    circle(from.x.toDouble(), from.y.toDouble())
    if (length > EPS) {
      circle(to.x.toDouble(), to.y.toDouble())
      val ux = ex / length; val uy = ey / length
      val px = ax - from.x; val py = ay - from.y
      var lo = 0.0; var hi = 1.0
      fun clip(value: Double, delta: Double, lower: Double, upper: Double): Boolean {
        if (kotlin.math.abs(delta) < EPS) return value >= lower && value <= upper
        val t1 = (lower - value) / delta; val t2 = (upper - value) / delta
        lo = max(lo, min(t1, t2)); hi = min(hi, max(t1, t2))
        return hi > lo + EPS
      }
      if (clip(px * ux + py * uy, dx * ux + dy * uy, 0.0, length) &&
        clip(px * -uy + py * ux, dx * -uy + dy * ux, -r, r) && hi > lo + EPS) cuts.add(Interval(lo, hi))
    }
    val merged = mutableListOf<Interval>()
    for (cut in cuts.sortedBy { it.start }) {
      val last = merged.lastOrNull()
      if (last != null && cut.start <= last.end + EPS) merged[merged.lastIndex] = Interval(last.start, max(last.end, cut.end))
      else merged.add(cut)
    }
    return merged
  }
}
