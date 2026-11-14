package io.pagenote.feature.pen

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/** GraphUtils.isBrokenLine: points and minimum distance are in screen pixels. */
internal object ReferenceBrokenLine {
  fun find(points: List<PenPoint>, screenWidth: Float): List<Int> {
    val corners = mutableListOf<Int>()
    val size = points.size
    if (size <= 2) return corners
    val last = size - 1
    if (distance(points.first(), points.last()) < screenWidth.toDouble()) return corners
    var index = if (size > 30) 4 else if (size > 20) 3 else 2
    var anchor = points.first()
    var anchorIndex = 3
    var minAngle = 200f
    var previous: PenPoint? = null
    var falseCorners = 0
    var counter = 0
    var straight = true
    var straightSamples = 0
    var lastStraightIndex = 0
    var cornerIndex = 0
    var sharpTurn = false
    while (index < last) {
      val point = points[index]
      if (previous == null || distance(point, previous) >= 1.0) {
        if (previous != null && index >= 5) {
          val angle = angle(points[index - 3], points[index - 1], point).toFloat()
          if (angle < 150f) sharpTurn = true
          val nextStraight: Boolean
          if (kotlin.math.abs(angle - 180f) >= 8f || angle.isNaN()) {
            if (counter >= 1) {
              counter = 0
              nextStraight = false
            } else {
              counter++
              nextStraight = straight
            }
            if (angle < minAngle) {
              cornerIndex = index
              minAngle = angle
            }
          } else {
            straightSamples++
            counter = (counter - 1).coerceAtLeast(0)
            lastStraightIndex = index
            minAngle = 200f
            nextStraight = true
          }
          if (!straight && nextStraight) {
            val traveled = distance(anchor, point).toFloat()
            if (straightSamples >= 4) {
              val previousStraight = lastStraightIndex - 3
              angle(anchor, points[(previousStraight + anchorIndex) / 2], points[previousStraight])
              if (traveled > screenWidth) {
                corners.add(cornerIndex)
                minAngle = 200f
              }
            } else if (traveled > screenWidth && index < last) {
              falseCorners++
            }
            anchor = point
            anchorIndex = index
            straightSamples = 0
          }
          straight = nextStraight
        }
        previous = point
      }
      index++
    }
    if ((corners.size <= 2 && falseCorners >= 1) || corners.size / 2 < falseCorners || corners.size >= 5) corners.clear()
    if (corners.size == 1 && angle(points.first(), points[corners[0]], points.last()).toFloat() > 170f) corners.clear()
    if (corners.isNotEmpty() && distance(points[corners.last()], points.last()) < screenWidth.toDouble()) corners.removeAt(corners.lastIndex)
    if (!sharpTurn) corners.clear()
    return corners
  }

  private fun distance(a: PenPoint, b: PenPoint): Double = hypot(a.x.toDouble() - b.x, a.y.toDouble() - b.y)

  private fun angle(a: PenPoint, b: PenPoint, c: PenPoint): Double {
    val ax = a.x.toDouble() - b.x; val ay = a.y.toDouble() - b.y
    val cx = c.x.toDouble() - b.x; val cy = c.y.toDouble() - b.y
    return Math.toDegrees(acos((ax * cx + ay * cy) / (sqrt(ax * ax + ay * ay) * sqrt(cx * cx + cy * cy))))
  }
}
