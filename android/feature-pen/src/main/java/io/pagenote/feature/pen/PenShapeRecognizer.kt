package io.pagenote.feature.pen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PenRecognizedShape(
  val type: String,
  val first: PenPoint,
  val second: PenPoint,
)

object PenShapeRecognizer {
  private const val MIN_PATH_LENGTH = 80f
  private const val MIN_DIAGONAL = 64f
  private const val CLOSED_RATIO = 0.20f
  private const val LINE_STRAIGHTNESS = 0.92f

  fun recognize(points: List<PenPoint>): PenRecognizedShape? {
    if (points.size < 2) return null
    val coordinates = points.map { Point(it.x, it.y) }
    val bounds = bounds(coordinates)
    val diagonal = hypot(bounds.width, bounds.height)
    val pathLength = coordinates.zipWithNext().sumOf { (first, second) -> distance(first, second).toDouble() }.toFloat()
    if (!diagonal.isFinite() || !pathLength.isFinite() || diagonal < MIN_DIAGONAL || pathLength < MIN_PATH_LENGTH) {
      return null
    }

    val closed = distance(coordinates.first(), coordinates.last()) <= diagonal * CLOSED_RATIO
    if (closed && coordinates.size >= 5) {
      if (fitsRectangle(coordinates, bounds, diagonal)) {
        return shape("rect", bounds.topLeft(), bounds.bottomRight())
      }
      if (fitsEllipse(coordinates, bounds, diagonal)) {
        return shape("ellipse", bounds.topLeft(), bounds.bottomRight())
      }
      return null
    }

    recognizeArrow(coordinates)?.let { return it }
    val direct = distance(coordinates.first(), coordinates.last())
    if (direct >= MIN_DIAGONAL && direct / pathLength >= LINE_STRAIGHTNESS) {
      return shape("line", coordinates.first(), coordinates.last())
    }
    return null
  }

  private fun fitsRectangle(points: List<Point>, bounds: Bounds, diagonal: Float): Boolean {
    val simplified = simplify(points, diagonal * 0.035f).toMutableList()
    if (simplified.size > 1 && distance(simplified.first(), simplified.last()) <= diagonal * CLOSED_RATIO) {
      simplified.removeAt(simplified.lastIndex)
    }
    if (simplified.size !in 4..6) return false
    val edgeTolerance = diagonal * 0.065f
    val onEdges = points.all { point ->
      minOf(
        abs(point.x - bounds.left), abs(point.x - bounds.right),
        abs(point.y - bounds.top), abs(point.y - bounds.bottom),
      ) <= edgeTolerance
    }
    if (!onEdges) return false
    val corners = listOf(
      Point(bounds.left, bounds.top),
      Point(bounds.right, bounds.top),
      Point(bounds.right, bounds.bottom),
      Point(bounds.left, bounds.bottom),
    )
    return corners.all { corner -> simplified.any { distance(it, corner) <= diagonal * 0.17f } }
  }

  private fun fitsEllipse(points: List<Point>, bounds: Bounds, diagonal: Float): Boolean {
    if (points.size < 10 || bounds.width < diagonal * 0.18f || bounds.height < diagonal * 0.18f) return false
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    val radiusX = bounds.width / 2f
    val radiusY = bounds.height / 2f
    val meanError = points.map { point ->
      val normalizedRadius = sqrt(
        ((point.x - centerX) / radiusX) * ((point.x - centerX) / radiusX) +
          ((point.y - centerY) / radiusY) * ((point.y - centerY) / radiusY),
      )
      abs(normalizedRadius - 1f)
    }.average().toFloat()
    return meanError <= 0.14f
  }

  private fun recognizeArrow(points: List<Point>): PenRecognizedShape? {
    if (points.size < 5) return null
    val start = points.first()
    val end = points.last()
    val direct = distance(start, end)
    if (direct < MIN_DIAGONAL) return null
    val axisX = (end.x - start.x) / direct
    val axisY = (end.y - start.y) / direct
    val projections = points.map { (it.x - start.x) * axisX + (it.y - start.y) * axisY }
    val tipIndex = projections.indices.maxByOrNull(projections::get) ?: return null
    if (tipIndex !in 1 until points.lastIndex) return null
    val tip = points[tipIndex]
    if (projections[tipIndex] < projections.last() + direct * 0.12f) return null
    if (distanceFromLine(tip, start, end) < direct * 0.12f) return null

    val repeatedTipIndex = (tipIndex + 2 until points.lastIndex)
      .firstOrNull { distance(points[it], tip) <= direct * 0.12f } ?: return null
    val firstWing = points.getOrNull(tipIndex + 1) ?: return null
    val secondWing = points.getOrNull(repeatedTipIndex + 1) ?: return null
    val sideX = -axisY
    val sideY = axisX
    val firstSide = (firstWing.x - tip.x) * sideX + (firstWing.y - tip.y) * sideY
    val secondSide = (secondWing.x - tip.x) * sideX + (secondWing.y - tip.y) * sideY
    if (firstSide * secondSide >= 0f || abs(firstSide) < direct * 0.025f || abs(secondSide) < direct * 0.025f) {
      return null
    }
    return shape("arrow", start, tip)
  }

  private fun simplify(points: List<Point>, tolerance: Float): List<Point> {
    if (points.size <= 2) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val ranges = ArrayDeque<Pair<Int, Int>>()
    ranges.addLast(0 to points.lastIndex)
    while (ranges.isNotEmpty()) {
      val (startIndex, endIndex) = ranges.removeLast()
      var farthestDistance = tolerance
      var farthestIndex = -1
      for (index in startIndex + 1 until endIndex) {
        val current = distanceFromLine(points[index], points[startIndex], points[endIndex])
        if (current > farthestDistance) {
          farthestDistance = current
          farthestIndex = index
        }
      }
      if (farthestIndex >= 0) {
        keep[farthestIndex] = true
        ranges.addLast(startIndex to farthestIndex)
        ranges.addLast(farthestIndex to endIndex)
      }
    }
    return points.indices.filter { keep[it] }.map(points::get)
  }

  private fun shape(type: String, first: Point, second: Point) = PenRecognizedShape(
    type = type,
    first = PenPoint(first.x, first.y, 1f),
    second = PenPoint(second.x, second.y, 1f),
  )

  private fun bounds(points: List<Point>): Bounds {
    val left = points.minOf(Point::x)
    val top = points.minOf(Point::y)
    val right = points.maxOf(Point::x)
    val bottom = points.maxOf(Point::y)
    return Bounds(left, top, right, bottom)
  }

  private fun distance(first: Point, second: Point) = hypot(first.x - second.x, first.y - second.y)

  private fun distanceFromLine(point: Point, start: Point, end: Point): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy)
    if (length == 0f) return distance(point, start)
    return abs(dy * point.x - dx * point.y + end.x * start.y - end.y * start.x) / length
  }

  private data class Point(val x: Float, val y: Float)

  private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun topLeft() = Point(left, top)
    fun bottomRight() = Point(right, bottom)
  }
}
