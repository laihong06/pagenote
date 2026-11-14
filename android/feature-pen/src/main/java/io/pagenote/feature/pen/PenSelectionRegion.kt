package io.pagenote.feature.pen

/** Closed lasso region in document coordinates. */
class PenSelectionRegion private constructor(
  val points: List<PenPoint>,
  val bounds: PenRect,
) {
  /**
   * Mirrors Pen.isInRegion from the recovered app: its DEX returns immediately
   * for the first non-null sample, so later points do not affect selection.
   */
  fun selects(stroke: PenStroke): Boolean = stroke.points.firstOrNull()
    ?.let { contains(it.x.toDouble(), it.y.toDouble()) }
    ?: false

  /** Boundary points count as inside, matching Android Region containment with inclusive edges. */
  fun contains(x: Double, y: Double): Boolean {
    if (!x.isFinite() || !y.isFinite()) return false
    if (x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom) return false

    var inside = false
    for (index in points.indices) {
      val a = points[index]
      val b = points[(index + 1) % points.size]
      if (onSegment(x, y, a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble())) return true
      val crosses = (a.y > y) != (b.y > y) &&
        x < (b.x - a.x).toDouble() * (y - a.y) / (b.y - a.y) + a.x
      if (crosses) inside = !inside
    }
    return inside
  }

  /** The reference selector keeps a vector material when its selection box is enclosed by the lasso bounds. */
  fun encloses(rect: PenRect): Boolean {
    val candidate = rect.normalized()
    return candidate.left >= bounds.left && candidate.top >= bounds.top &&
      candidate.right <= bounds.right && candidate.bottom <= bounds.bottom
  }

  companion object {
    /**
     * Creates a reference-style lasso. The selector requires at least five
     * samples and a minimum 2dp bounding box; callers convert that minimum to
     * document units for the current viewport.
     */
    fun create(
      points: List<PenPoint>,
      minimumWidth: Double = 0.0,
      minimumHeight: Double = 0.0,
    ): PenSelectionRegion? {
      if (points.size < MINIMUM_SAMPLE_COUNT || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
      if (!minimumWidth.isFinite() || minimumWidth < 0.0 || !minimumHeight.isFinite() || minimumHeight < 0.0) return null
      val bounds = PenRect(
        left = points.minOf { it.x.toDouble() },
        top = points.minOf { it.y.toDouble() },
        right = points.maxOf { it.x.toDouble() },
        bottom = points.maxOf { it.y.toDouble() },
      )
      if (bounds.right - bounds.left < minimumWidth || bounds.bottom - bounds.top < minimumHeight) return null
      return PenSelectionRegion(points.toList(), bounds)
    }

    private fun onSegment(x: Double, y: Double, ax: Double, ay: Double, bx: Double, by: Double): Boolean {
      val cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax)
      val tolerance = 1e-7 * maxOf(1.0, kotlin.math.abs(bx - ax), kotlin.math.abs(by - ay))
      if (kotlin.math.abs(cross) > tolerance) return false
      return x >= minOf(ax, bx) - tolerance && x <= maxOf(ax, bx) + tolerance &&
        y >= minOf(ay, by) - tolerance && y <= maxOf(ay, by) + tolerance
    }

    private const val MINIMUM_SAMPLE_COUNT = 5
  }
}
