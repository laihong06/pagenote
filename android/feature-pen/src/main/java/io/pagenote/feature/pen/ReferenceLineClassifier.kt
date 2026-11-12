package io.pagenote.feature.pen

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** GraphUtils.isLine. Input coordinates are reference screen pixels. */
internal object ReferenceLineClassifier {
  data class Result(val controlIndex: Int, val arrow: Boolean = false, val arrowPosition: Int = -1)

  fun classify(points: List<PenPoint>): Result {
    val size = points.size
    if (size <= 2) return Result(-1)
    val first = points.first()
    val last = points.last()
    val startX = first.x.toDouble()
    val startY = first.y.toDouble()
    var initialAngle = atan2(last.y.toDouble() - startY, last.x.toDouble() - startX).toFloat()
    if (!initialAngle.isFinite()) initialAngle = 0f
    val direct = distance(first, last)
    if (direct < 15.0) return Result(-1)
    val maxDeviationCount = (size / 7).coerceIn(3, 5)
    var index = if (size > 30) 4 else if (size > 20) 3 else 2
    val edgeCount = if (size > 20) 8 else 5
    var controlIndex = size / 2
    var minAngle = 200f
    var previous: PenPoint? = null
    var previousAngle = 0.0
    var turns = 0
    var arrowTurns = 0
    var deviations = 0
    var earlyDeviations = 0
    var arrowPosition = -1
    while (index < size - 1) {
      val point = points[index]
      if (previous == null || distance(point, previous) >= 1.0) {
        var rayAngle = atan2(point.y.toDouble() - startY, point.x.toDouble() - startX).toFloat()
        if (!rayAngle.isFinite()) rayAngle = previousAngle.toFloat()
        if (previous != null) {
          val direction = atan2(point.y.toDouble() - previous.y, point.x.toDouble() - previous.x)
          val turned = index > 8 && abs(abs(direction) - abs(previousAngle)) > 1.0
          if (turned) turns++
          if (index >= edgeCount && index < size - edgeCount) {
            val angle = angle(points[index - 4], points[index - 2], point).toFloat()
            if (angle < minAngle) { minAngle = angle; controlIndex = index }
          }
          if (turned) {
            if (arrowTurns == 0) arrowPosition = index
            arrowTurns++
          }
          previousAngle = direction
        }
        if (abs(abs(initialAngle) - abs(rayAngle)) > 0.3 && turns <= 1) {
          if (turns == 0) earlyDeviations++
          deviations++
        }
        previous = point
      }
      index++
    }
    val arrow = arrowPosition in points.indices && arrowTurns >= 2 && distance(last, points[arrowPosition]) > 5.0
    var straight = if (turns >= 2) deviations < maxDeviationCount else earlyDeviations < maxDeviationCount
    if (straight && !arrow) {
      val via = if (size > 10) {
        val quarter = size / 4
        distance(first, points[quarter]) + distance(points[quarter], points[controlIndex]) +
          distance(points[controlIndex], points[quarter * 3]) + distance(points[quarter * 3], last)
      } else {
        val third = size / 3
        distance(first, points[third]) + distance(points[third], points[third * 2]) + distance(points[third * 2], last)
      }
      if (via > direct * if (size > 10) 1.03 else 1.05) straight = false
    }
    return Result(if (straight) 0 else controlIndex - 2, arrow, arrowPosition)
  }

  private fun distance(a: PenPoint, b: PenPoint) = hypot(a.x.toDouble() - b.x, a.y.toDouble() - b.y)

  private fun angle(a: PenPoint, b: PenPoint, c: PenPoint): Double {
    val ax = a.x.toDouble() - b.x; val ay = a.y.toDouble() - b.y
    val cx = c.x.toDouble() - b.x; val cy = c.y.toDouble() - b.y
    return Math.toDegrees(acos((ax * cx + ay * cy) / (sqrt(ax * ax + ay * ay) * sqrt(cx * cx + cy * cy))))
  }
}
