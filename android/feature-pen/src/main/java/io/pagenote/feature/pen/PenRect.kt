package io.pagenote.feature.pen

import kotlin.math.max
import kotlin.math.min

/**
 * Axis-aligned rectangle in annotations.json document units (width-1000
 * coordinate space). Corners may be given in any order; [normalized] orders
 * them. Intersection tests treat edges as inclusive.
 */
data class PenRect(
  val left: Double,
  val top: Double,
  val right: Double,
  val bottom: Double,
) {
  init {
    require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
      "non-finite rect: $this"
    }
  }

  /** Same rectangle with left<=right and top<=bottom. */
  fun normalized(): PenRect = PenRect(
    left = min(left, right),
    top = min(top, bottom),
    right = max(left, right),
    bottom = max(top, bottom),
  )

  /** True when this rect and [other] share at least one point (edges inclusive). */
  fun overlaps(other: PenRect): Boolean {
    val a = normalized()
    val b = other.normalized()
    return a.left <= b.right && a.right >= b.left && a.top <= b.bottom && a.bottom >= b.top
  }

  companion object {
    /** Builds a rect from two arbitrary opposite corners. */
    fun ofCorners(x1: Double, y1: Double, x2: Double, y2: Double): PenRect = PenRect(
      left = min(x1, x2),
      top = min(y1, y2),
      right = max(x1, x2),
      bottom = max(y1, y2),
    )
  }
}

/**
 * Document-space rectangle hit testing for eraser-style batch deletion
 * (port batch 1). Pure JVM, no Android dependencies.
 *
 * Per-type semantics (two-point parameterization per canvas-schema-decision
 * §5, `android/docs/spec/canvas-schema-decision.md`):
 * - `pen` / `highlight` / future unknown types: polyline — any point inside
 *   the rect, or any segment between consecutive points crossing it;
 * - `line` / `arrow`: the start→end segment (arrow head ignored);
 * - `rect`: any of the four border segments;
 * - `ellipse` / `image` / `html`: area items — bounding-box overlap
 *   (approximation for the ellipse outline);
 * - `text`: the anchor point.
 */
object PenRectHit {
  /** True when any part of the stroke geometry touches [rect]. */
  fun strokeIntersects(stroke: PenStroke, rect: PenRect): Boolean {
    val points = stroke.points
    if (points.isEmpty()) return false
    return when (stroke.type) {
      "line", "arrow" -> segmentIntersects(
        points.first().x.toDouble(),
        points.first().y.toDouble(),
        points.last().x.toDouble(),
        points.last().y.toDouble(),
        rect,
      )
      "rect" -> {
        val a = points.first()
        val b = points.last()
        val left = min(a.x.toDouble(), b.x.toDouble())
        val right = max(a.x.toDouble(), b.x.toDouble())
        val top = min(a.y.toDouble(), b.y.toDouble())
        val bottom = max(a.y.toDouble(), b.y.toDouble())
        // The four border segments of the drawn rectangle outline.
        val corners = listOf(left to top, right to top, right to bottom, left to bottom)
        (corners + corners.first()).zipWithNext().any { (c1, c2) ->
          segmentIntersects(c1.first, c1.second, c2.first, c2.second, rect)
        }
      }
      "ellipse", "image", "html" -> bbox(points).overlaps(rect)
      "text" -> pointInside(points.first().x.toDouble(), points.first().y.toDouble(), rect)
      else -> polylineIntersects(points, rect)
    }
  }

  /** True when (x, y) lies inside [rect], edges inclusive. */
  fun pointInside(x: Double, y: Double, rect: PenRect): Boolean {
    val r = rect.normalized()
    return x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
  }

  /** Polyline hit: any point inside or any consecutive segment crossing the rect. */
  fun polylineIntersects(points: List<PenPoint>, rect: PenRect): Boolean {
    if (points.isEmpty()) return false
    if (points.any { pointInside(it.x.toDouble(), it.y.toDouble(), rect) }) return true
    return points.zipWithNext().any { (a, b) ->
      segmentIntersects(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(), rect)
    }
  }

  /** Segment vs rect: true when they share at least one point. */
  fun segmentIntersects(
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
    rect: PenRect,
  ): Boolean {
    val r = rect.normalized()
    if (pointInside(x1, y1, r) || pointInside(x2, y2, r)) return true
    // Both endpoints outside: the segment can still clip a corner, so test
    // it against each of the four rect edges.
    val corners = listOf(
      r.left to r.top,
      r.right to r.top,
      r.right to r.bottom,
      r.left to r.bottom,
    )
    return corners.zipWithNext().any { (c1, c2) ->
      segmentsIntersect(x1, y1, x2, y2, c1.first, c1.second, c2.first, c2.second)
    }
  }

  /** Bounding box of all points; null for empty input. */
  fun bbox(points: List<PenPoint>): PenRect {
    val xs = points.map { it.x.toDouble() }
    val ys = points.map { it.y.toDouble() }
    return PenRect(xs.min(), ys.min(), xs.max(), ys.max())
  }

  /** Standard orientation-based segment/segment intersection, endpoints inclusive. */
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
}
