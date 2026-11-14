package io.pagenote.feature.pen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Records completed pieces of the current stroke; the final segment stays editable. */
class PenLiveStrokeCache {
  private data class Chunk(val picture: Picture, val left: Int, val top: Int)
  private val chunks = mutableListOf<Chunk>()
  private val path = Path()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private var color: String? = null
  private var width: Double = Double.NaN
  private var lastPointCount = 0
  private var firstPoint: PenPoint? = null
  val recordedChunkCount: Int get() = chunks.size

  fun clear() {
    chunks.clear()
    color = null
    width = Double.NaN
    lastPointCount = 0
    firstPoint = null
  }

  fun draw(canvas: Canvas, points: List<PenPoint>, color: String, width: Double) {
    if (points.isEmpty()) return
    if (this.color != color || this.width != width || points.size < lastPointCount || firstPoint != points.first()) {
      clear()
      this.color = color
      this.width = width
      firstPoint = points.first()
    }
    lastPointCount = points.size
    paint.color = runCatching { Color.parseColor(color) }.getOrDefault(Color.BLACK)
    paint.alpha = 255
    if (points.size == 1) {
      paint.style = Paint.Style.FILL
      canvas.drawCircle(points[0].x, points[0].y, width.toFloat() * points[0].pressure / 2f, paint)
      return
    }
    paint.style = Paint.Style.STROKE
    if (points.size == 2) {
      paint.strokeWidth = width.toFloat() * (points[0].pressure + points[1].pressure) / 2f
      canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, paint)
      return
    }
    // All segments except the last endpoint-dependent one are stable.
    val stableCount = points.size - 2
    while ((chunks.size + 1) * CHUNK_SIZE <= stableCount) {
      val start = chunks.size * CHUNK_SIZE
      val chunk = recordChunk(points, start, start + CHUNK_SIZE, width.toFloat()) ?: break
      chunks.add(chunk)
    }
    chunks.forEach { chunk ->
      val saved = canvas.save()
      canvas.translate(chunk.left.toFloat(), chunk.top.toFloat())
      canvas.drawPicture(chunk.picture)
      canvas.restoreToCount(saved)
    }
    for (segment in chunks.size * CHUNK_SIZE until stableCount) drawStable(canvas, points, segment, width.toFloat())
    val previous = points[points.size - 2]
    val last = points.last()
    val startX = (previous.x + last.x) / 2f
    val startY = (previous.y + last.y) / 2f
    paint.strokeWidth = width.toFloat() * (previous.pressure + last.pressure) / 2f
    path.rewind(); path.moveTo(startX, startY); path.quadTo(previous.x, previous.y, last.x, last.y)
    canvas.drawPath(path, paint)
  }

  private fun recordChunk(points: List<PenPoint>, start: Int, end: Int, width: Float): Chunk? {
    var left = Float.POSITIVE_INFINITY; var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY; var bottom = Float.NEGATIVE_INFINITY
    for (index in start..end + 1) {
      val point = points[index]
      left = min(left, point.x); top = min(top, point.y)
      right = max(right, point.x); bottom = max(bottom, point.y)
    }
    val padding = max(2f, width / 2f + 2f)
    val x = floor(left - padding).toInt(); val y = floor(top - padding).toInt()
    val w = ceil(right + padding).toInt().toLong() - x
    val h = ceil(bottom + padding).toInt().toLong() - y
    if (w !in 1..MAX_SIDE || h !in 1..MAX_SIDE) return null
    val picture = Picture()
    val recording = picture.beginRecording(w.toInt(), h.toInt())
    recording.translate(-x.toFloat(), -y.toFloat())
    for (segment in start until end) drawStable(recording, points, segment, width)
    picture.endRecording()
    return Chunk(picture, x, y)
  }

  private fun drawStable(canvas: Canvas, points: List<PenPoint>, segment: Int, width: Float) {
    val i = segment + 1
    val current = points[i]; val next = points[i + 1]
    val sx = if (i == 1) points[0].x else (points[i - 1].x + current.x) / 2f
    val sy = if (i == 1) points[0].y else (points[i - 1].y + current.y) / 2f
    paint.strokeWidth = width * (current.pressure + next.pressure) / 2f
    path.rewind(); path.moveTo(sx, sy)
    path.quadTo(current.x, current.y, (current.x + next.x) / 2f, (current.y + next.y) / 2f)
    canvas.drawPath(path, paint)
  }

  private companion object {
    const val CHUNK_SIZE = 32
    const val MAX_SIDE = 32767L
  }
}
