package io.pagenote.feature.pen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared stroke rasterizer used by the live view and the committed-stroke
 * cache. All functions draw in document units; the caller has already
 * applied the page translate/scale to [canvas].
 */
object PenStrokePainter {
  const val HIGHLIGHT_ALPHA = 89

  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  fun drawStroke(canvas: Canvas, stroke: PenStroke, geometry: PenCanvasGeometry) {
    val color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
    val points = stroke.points
    if (points.isEmpty()) return
    strokePaint.color = color
    strokePaint.alpha = Color.alpha(color)
    strokePaint.style = Paint.Style.STROKE
    strokePaint.strokeWidth = stroke.width.toFloat()

    when (stroke.type) {
      "rect" -> {
        if (points.size >= 2) {
          strokePaint.style = Paint.Style.STROKE
          canvas.drawRect(
            min(points.first().x, points.last().x),
            min(points.first().y, points.last().y),
            max(points.first().x, points.last().x),
            max(points.first().y, points.last().y),
            strokePaint,
          )
        }
      }
      "ellipse" -> {
        if (points.size >= 2) {
          canvas.drawOval(
            min(points.first().x, points.last().x),
            min(points.first().y, points.last().y),
            max(points.first().x, points.last().x),
            max(points.first().y, points.last().y),
            strokePaint,
          )
        }
      }
      "line", "arrow" -> {
        if (points.size >= 2) {
          val first = points.first()
          val last = points.last()
          canvas.drawLine(first.x, first.y, last.x, last.y, strokePaint)
          if (stroke.type == "arrow") drawArrowHead(canvas, first, last, strokePaint)
        }
      }
      "pen" -> drawFreehand(canvas, points, stroke.width.toFloat())
      "highlight" -> drawHighlight(canvas, points, stroke.width.toFloat(), geometry)
      else -> Unit
    }
  }

  fun drawHighlight(
    canvas: Canvas,
    points: List<PenPoint>,
    width: Float,
    geometry: PenCanvasGeometry,
  ) {
    val first = points.first()
    val bounds = RectF(first.x, first.y, first.x, first.y)
    points.forEach { bounds.union(it.x, it.y) }
    val padding = width / 2f + 1f / geometry.pixelsPerDocumentUnit
    bounds.inset(-padding, -padding)
    if (!bounds.intersect(RectF(canvas.clipBounds))) return

    // Composite once per gesture, never once per pressure segment or retrace.
    val save = canvas.saveLayerAlpha(bounds, HIGHLIGHT_ALPHA)
    try {
      drawFreehand(canvas, points, width)
    } finally {
      canvas.restoreToCount(save)
    }
  }

  private val scratchPath = Path()

  fun drawFreehand(canvas: Canvas, points: List<PenPoint>, width: Float) {
    strokePaint.style = Paint.Style.STROKE
    strokePaint.alpha = 255
    if (points.size == 1) {
      strokePaint.style = Paint.Style.FILL
      canvas.drawCircle(points[0].x, points[0].y, width * points[0].pressure / 2f, strokePaint)
      return
    }
    if (points.size == 2) {
      val previous = points[0]
      val current = points[1]
      strokePaint.strokeWidth = width * (previous.pressure + current.pressure) / 2f
      canvas.drawLine(previous.x, previous.y, current.x, current.y, strokePaint)
      return
    }
    PenStrokeSmoother.segments(points, width).forEach { segment ->
      strokePaint.strokeWidth = segment.width
      scratchPath.rewind()
      scratchPath.moveTo(segment.startX, segment.startY)
      scratchPath.quadTo(segment.controlX, segment.controlY, segment.endX, segment.endY)
      canvas.drawPath(scratchPath, strokePaint)
    }
  }

  fun drawSegment(canvas: Canvas, from: PenPoint, to: PenPoint, color: String, width: Float) {
    val parsed = runCatching { Color.parseColor(color) }.getOrDefault(Color.BLACK)
    strokePaint.color = parsed
    strokePaint.alpha = Color.alpha(parsed)
    strokePaint.style = Paint.Style.STROKE
    strokePaint.strokeWidth = width * (from.pressure + to.pressure) / 2f
    canvas.drawLine(from.x, from.y, to.x, to.y, strokePaint)
  }

  fun drawLaser(
    canvas: Canvas,
    points: List<PenPoint>,
    color: String,
    width: Double,
    geometry: PenCanvasGeometry,
  ) {
    val stroke = PenStroke("pen", color, width, points)
    drawStroke(canvas, stroke.copy(width = width * 2.5), geometry)
    drawStroke(canvas, stroke.copy(color = "#ffffff", width = width * 0.55), geometry)
  }

  private fun drawArrowHead(canvas: Canvas, start: PenPoint, end: PenPoint, paint: Paint) {
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()).toFloat()
    val headLength = (paint.strokeWidth * 4f).coerceIn(12f, 40f)
    val spread = 0.55f
    val path = Path().apply {
      moveTo(end.x, end.y)
      lineTo(end.x - headLength * cos(angle - spread), end.y - headLength * sin(angle - spread))
      moveTo(end.x, end.y)
      lineTo(end.x - headLength * cos(angle + spread), end.y - headLength * sin(angle + spread))
    }
    canvas.drawPath(path, paint)
  }
}
