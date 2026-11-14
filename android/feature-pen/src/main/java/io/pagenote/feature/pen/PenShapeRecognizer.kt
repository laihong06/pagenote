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
  val points: List<PenPoint> = listOf(first, second),
  /** Optional existing annotation catalog kind for native vector shapes. */
  val shapeKind: String? = null,
)

object PenShapeRecognizer {
  private const val MIN_PATH_LENGTH = 80f
  private const val MIN_DIAGONAL = 64f
  private const val CLOSED_RATIO = 0.20f
  private const val LINE_STRAIGHTNESS = 0.92f

  fun recognize(
    points: List<PenPoint>,
    minimumDiagonal: Float = MIN_DIAGONAL,
    minimumPathLength: Float = MIN_PATH_LENGTH,
  ): PenRecognizedShape? {
    if (points.size < 2) return null
    val coordinates = points.map { Point(it.x, it.y) }
    val bounds = bounds(coordinates)
    val diagonal = hypot(bounds.width, bounds.height)
    val pathLength = coordinates.zipWithNext().sumOf { (first, second) -> distance(first, second).toDouble() }.toFloat()
    if (!diagonal.isFinite() || !pathLength.isFinite() || diagonal < minimumDiagonal || pathLength < minimumPathLength) {
      return null
    }

    val closed = distance(coordinates.first(), coordinates.last()) <= diagonal * CLOSED_RATIO
    if (closed && coordinates.size >= 4) {
      if (fitsRectangle(coordinates, bounds, diagonal)) {
        return shape("rect", bounds.topLeft(), bounds.bottomRight())
      }
      if (fitsPolygon(coordinates, bounds, diagonal, triangle = true)) {
        return shape("rect", bounds.topLeft(), bounds.bottomRight(), "triangle")
      }
      if (fitsPolygon(coordinates, bounds, diagonal, triangle = false)) {
        return shape("rect", bounds.topLeft(), bounds.bottomRight(), "diamond")
      }
      if (fitsEllipse(coordinates, bounds, diagonal)) {
        return shape("ellipse", bounds.topLeft(), bounds.bottomRight())
      }
      return null
    }

    recognizeArrow(coordinates, diagonal)?.let { return it }
    val direct = distance(coordinates.first(), coordinates.last())
    if (direct >= minimumDiagonal &&
      (direct / pathLength >= LINE_STRAIGHTNESS || fitsJitteryLine(coordinates, direct))
    ) {
      return shape("line", coordinates.first(), coordinates.last())
    }
    return null
  }

  private fun fitsJitteryLine(points: List<Point>, direct: Float): Boolean {
    val start = points.first()
    val end = points.last()
    val axisX = (end.x - start.x) / direct
    val axisY = (end.y - start.y) / direct
    var backward = 0f
    var error = 0f
    for (index in points.indices) {
      val point = points[index]
      val deviation = distanceFromLine(point, start, end)
      if (deviation > direct * 0.06f) return false
      error += deviation
      if (index > 0) {
        val previous = points[index - 1]
        val progress = (point.x - previous.x) * axisX + (point.y - previous.y) * axisY
        if (progress < 0f) backward -= progress
      }
    }
    return error / points.size <= direct * 0.035f && backward <= direct * 0.2f
  }

  private fun fitsRectangle(points: List<Point>, bounds: Bounds, diagonal: Float): Boolean {
    val simplified = simplify(points, diagonal * 0.035f).toMutableList()
    if (simplified.size > 1 && distance(simplified.first(), simplified.last()) <= diagonal * CLOSED_RATIO) {
      simplified.removeAt(simplified.lastIndex)
    }
    if (simplified.size !in 4..9) return false
    val edgeTolerance = diagonal * 0.065f
    val onEdges = points.count { point ->
      minOf(
        abs(point.x - bounds.left), abs(point.x - bounds.right),
        abs(point.y - bounds.top), abs(point.y - bounds.bottom),
      ) <= edgeTolerance
    }
    if (onEdges < points.size * 0.85f) return false
    val corners = listOf(
      Point(bounds.left, bounds.top),
      Point(bounds.right, bounds.top),
      Point(bounds.right, bounds.bottom),
      Point(bounds.left, bounds.bottom),
    )
    return corners.all { corner -> simplified.any { distance(it, corner) <= diagonal * 0.17f } }
  }

  private fun fitsPolygon(points: List<Point>, bounds: Bounds, diagonal: Float, triangle: Boolean): Boolean {
    val corners = simplify(points, diagonal * 0.04f).toMutableList()
    if (corners.size > 1 && distance(corners.first(), corners.last()) <= diagonal * CLOSED_RATIO) {
      corners.removeAt(corners.lastIndex)
    }
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    val expected = if (triangle) listOf(
      Point(centerX, bounds.top), Point(bounds.right, bounds.bottom), Point(bounds.left, bounds.bottom),
    ) else listOf(
      Point(centerX, bounds.top), Point(bounds.right, centerY),
      Point(centerX, bounds.bottom), Point(bounds.left, centerY),
    )
    if (corners.size !in expected.size..expected.size + 1) return false
    if (!expected.all { target -> corners.any { distance(it, target) <= diagonal * 0.16f } }) return false
    val onEdges = points.count { point ->
      expected.indices.minOf { index ->
        distanceFromSegment(point, expected[index], expected[(index + 1) % expected.size])
      } <= diagonal * 0.09f
    }
    return onEdges >= points.size * 0.9f
  }

  private fun distanceFromSegment(point: Point, start: Point, end: Point): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return distance(point, start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(point.x - (start.x + t * dx), point.y - (start.y + t * dy))
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

  private fun recognizeArrow(points: List<Point>, diagonal: Float): PenRecognizedShape? {
    if (points.size < 5) return null
    // Simplification preserves the tip and the two wings regardless of how
    // many touch samples the device reports between corners.
    val corners = simplify(points, diagonal * 0.025f)
    if (corners.size !in 5..8) return null
    val start = corners.first()
    val tipIndex = corners.indices.drop(1).maxByOrNull { distance(start, corners[it]) } ?: return null
    if (tipIndex < 1 || tipIndex + 3 > corners.lastIndex) return null
    val tip = corners[tipIndex]
    val length = distance(start, tip)
    if (length <= 0f) return null
    val shaftLength = corners.subList(0, tipIndex + 1).zipWithNext()
      .sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
    if (length / shaftLength < 0.88f) return null
    val axisX = (tip.x - start.x) / length
    val axisY = (tip.y - start.y) / length
    val returnIndex = (tipIndex + 2 until corners.lastIndex)
      .firstOrNull { distance(corners[it], tip) <= length * 0.14f } ?: return null
    val firstWing = corners.subList(tipIndex + 1, returnIndex).maxByOrNull { distance(it, tip) } ?: return null
    val secondWing = corners.subList(returnIndex + 1, corners.size).maxByOrNull { distance(it, tip) } ?: return null
    fun side(p: Point) = (p.x - tip.x) * -axisY + (p.y - tip.y) * axisX
    fun back(p: Point) = -((p.x - tip.x) * axisX + (p.y - tip.y) * axisY)
    val side1 = side(firstWing)
    val side2 = side(secondWing)
    if (side1 * side2 >= 0f || min(abs(side1), abs(side2)) < length * 0.06f) return null
    if (back(firstWing) !in length * 0.04f..length * 0.55f ||
      back(secondWing) !in length * 0.04f..length * 0.55f
    ) return null
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

  private fun shape(type: String, first: Point, second: Point, shapeKind: String? = null) = PenRecognizedShape(
    type = type,
    first = PenPoint(first.x, first.y, 1f),
    second = PenPoint(second.x, second.y, 1f),
    shapeKind = shapeKind,
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
