package io.pagenote.feature.pen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PenCanvasPageTransform(
  val pageWidthPx: Float,
  val pageHeightPx: Float,
  val scale: Float = 1f,
  val translationX: Float = 0f,
  val translationY: Float = 0f,
  val allowOutsidePage: Boolean = false,
)

class PenCanvasGeometry(
  val widthPx: Float,
  val heightPx: Float,
  pageTransform: PenCanvasPageTransform? = null,
) {
  private val transform = pageTransform ?: PenCanvasPageTransform(widthPx, heightPx)
  val pageWidthPx: Float = transform.pageWidthPx
  val pageHeightPx: Float = transform.pageHeightPx
  val scale: Float = transform.scale
  val allowOutsidePage: Boolean = transform.allowOutsidePage
  val isValid: Boolean = listOf(
    widthPx, heightPx, pageWidthPx, pageHeightPx, scale,
    transform.translationX, transform.translationY,
  ).all(Float::isFinite) && widthPx > 0f && heightPx > 0f &&
    pageWidthPx > 0f && pageHeightPx > 0f && scale > 0f

  val pixelsPerDocumentUnit: Float = if (isValid) pageWidthPx / 1000f * scale else Float.NaN
  val pageOriginX: Float = if (isValid) {
    widthPx / 2f + transform.translationX - pageWidthPx * scale / 2f
  } else {
    Float.NaN
  }
  val pageOriginY: Float = if (isValid) {
    heightPx / 2f + transform.translationY - pageHeightPx * scale / 2f
  } else {
    Float.NaN
  }

  fun toDocumentPoint(
    xPx: Float,
    yPx: Float,
    pressure: Float?,
    clampToPage: Boolean = !allowOutsidePage,
  ): PenPoint? {
    if (!isValid || !xPx.isFinite() || !yPx.isFinite()) return null
    val pressureValue = pressure?.takeIf { it.isFinite() && it > 0f }
      ?.coerceIn(0f, 1f)
      ?: PenValidation.DEFAULT_PRESSURE
    val x = (xPx - pageOriginX) / pixelsPerDocumentUnit
    val y = (yPx - pageOriginY) / pixelsPerDocumentUnit
    val pageMaxY = pageHeightPx * 1000f / pageWidthPx
    return PenPoint(
      x = if (clampToPage) x.coerceIn(0f, 1000f) else x,
      y = if (clampToPage) y.coerceIn(0f, pageMaxY) else y,
      pressure = pressureValue,
    )
  }

  fun documentUnitsForPixels(pixels: Float): Float =
    if (isValid && pixels.isFinite()) pixels / pixelsPerDocumentUnit else Float.NaN

  fun hitTest(
    strokes: List<PenStroke>,
    xPx: Float,
    yPx: Float,
    radiusPx: Float,
  ): Int? {
    if (!isValid || !xPx.isFinite() || !yPx.isFinite() || !radiusPx.isFinite() || radiusPx < 0f) {
      return null
    }
    for (index in strokes.indices.reversed()) {
      val stroke = strokes[index]
      if (stroke.type !in HIT_TEST_TYPES) continue
      if (distanceToStroke(stroke, xPx, yPx) <= radiusPx + strokeWidthPx(stroke) / 2f) {
        return index
      }
    }
    return null
  }

  fun toViewX(point: PenPoint): Float = pageOriginX + point.x * pixelsPerDocumentUnit

  fun toViewY(point: PenPoint): Float = pageOriginY + point.y * pixelsPerDocumentUnit

  fun strokeWidthPx(stroke: PenStroke): Float =
    stroke.width.toFloat() * pixelsPerDocumentUnit * (stroke.points.maxOfOrNull { it.pressure } ?: 1f)

  private fun distanceToStroke(stroke: PenStroke, x: Float, y: Float): Float {
    val points = stroke.points
    if (points.isEmpty()) return Float.POSITIVE_INFINITY
    val coords = points.map { toViewX(it) to toViewY(it) }
    if (coords.size == 1) return hypot(x - coords[0].first, y - coords[0].second)
    return when (stroke.type) {
      "rect" -> distanceToRect(coords, x, y)
      "ellipse" -> distanceToEllipse(coords, x, y)
      "arrow" -> distanceToArrow(coords, stroke, x, y)
      "pen", "highlight", "line" -> coords.zipWithNext().minOf { (a, b) ->
        distanceToSegment(x, y, a.first, a.second, b.first, b.second)
      }
      else -> Float.POSITIVE_INFINITY
    }
  }

  private fun distanceToArrow(
    points: List<Pair<Float, Float>>,
    stroke: PenStroke,
    x: Float,
    y: Float,
  ): Float {
    val start = points.first()
    val end = points.last()
    val angle = kotlin.math.atan2((end.second - start.second).toDouble(), (end.first - start.first).toDouble())
      .toFloat()
    val headLength = (stroke.width.toFloat() * 4f).coerceIn(12f, 40f) * pixelsPerDocumentUnit
    val spread = 0.55f
    val left = end.first - headLength * kotlin.math.cos(angle - spread) to
      end.second - headLength * kotlin.math.sin(angle - spread)
    val right = end.first - headLength * kotlin.math.cos(angle + spread) to
      end.second - headLength * kotlin.math.sin(angle + spread)
    return minOf(
      distanceToSegment(x, y, start.first, start.second, end.first, end.second),
      distanceToSegment(x, y, end.first, end.second, left.first, left.second),
      distanceToSegment(x, y, end.first, end.second, right.first, right.second),
    )
  }

  private fun distanceToRect(points: List<Pair<Float, Float>>, x: Float, y: Float): Float {
    val first = points.first()
    val last = points.last()
    val left = min(first.first, last.first)
    val right = max(first.first, last.first)
    val top = min(first.second, last.second)
    val bottom = max(first.second, last.second)
    return listOf(
      distanceToSegment(x, y, left, top, right, top),
      distanceToSegment(x, y, right, top, right, bottom),
      distanceToSegment(x, y, right, bottom, left, bottom),
      distanceToSegment(x, y, left, bottom, left, top),
    ).min()
  }

  private fun distanceToEllipse(points: List<Pair<Float, Float>>, x: Float, y: Float): Float {
    val first = points.first()
    val last = points.last()
    val centerX = (first.first + last.first) / 2f
    val centerY = (first.second + last.second) / 2f
    val radiusX = abs(last.first - first.first) / 2f
    val radiusY = abs(last.second - first.second) / 2f
    if (radiusX == 0f || radiusY == 0f) {
      return distanceToSegment(x, y, first.first, first.second, last.first, last.second)
    }
    val normalized = sqrt(
      ((x - centerX) / radiusX) * ((x - centerX) / radiusX) +
        ((y - centerY) / radiusY) * ((y - centerY) / radiusY),
    )
    return abs(normalized - 1f) * min(radiusX, radiusY)
  }

  private fun distanceToSegment(
    x: Float,
    y: Float,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
  ): Float {
    val dx = endX - startX
    val dy = endY - startY
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(x - startX, y - startY)
    val projection = (((x - startX) * dx + (y - startY) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(x - (startX + projection * dx), y - (startY + projection * dy))
  }

  private companion object {
    val HIT_TEST_TYPES = setOf("pen", "highlight", "line", "arrow", "rect", "ellipse")
  }
}
