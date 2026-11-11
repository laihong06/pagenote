package io.pagenote.feature.pen

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.Region

/**
 * Geometry stage of reference Pen.split_v2, using its integer region and one-pixel
 * PathMeasure sampling. Input bounds are the reference pen's pre-built segment
 * bounds; creating those bounds and mapping its material model are separate work.
 * Not connected to editing or persistence.
 */
internal object ReferencePenSplit {
  data class Point(val x: Float, val y: Float, val bounds: Rect)
  data class Position(val x: Float, val y: Float)
  data class Result(val split: Boolean, val pieces: List<List<Position>>)

  fun split(points: List<Point>, width: Float, previous: Rect, current: Rect): Result {
    if (points.isEmpty()) return Result(false, emptyList())
    val margin = (width / 3f).toInt()
    val first = Rect(previous).apply { inset(-margin, -margin) }
    val second = Rect(current).apply { inset(-margin, -margin) }
    val union = Rect().apply { union(first); union(second) }
    val region = regionBetween(first, second)
    val pieces = mutableListOf<List<Position>>()
    var piece = mutableListOf(Position(points[0].x, points[0].y))
    var wasSplit = false
    val position = FloatArray(2)
    val endpoint = FloatArray(2)
    val measure = PathMeasure()

    for (index in 1 until points.size) {
      val point = points[index]
      val bounds = Rect(point.bounds)
      val intersectsRegion = region.contains(bounds.left, bounds.top) ||
        region.contains(bounds.right, bounds.top) || region.contains(bounds.right, bounds.bottom) ||
        region.contains(bounds.left, bounds.bottom)
      if (intersectsRegion || bounds.intersect(union)) {
        val prior = points[index - 1]
        val path = Path().apply { moveTo(prior.x, prior.y); lineTo(point.x, point.y) }
        measure.setPath(path, false)
        val length = measure.length.toInt()
        var justErased = false
        for (distance in 0 until length) {
          measure.getPosTan(distance.toFloat(), position, null)
          if (region.contains(position[0].toInt(), position[1].toInt()) ||
            union.contains(position[0].toInt(), position[1].toInt())) {
            if (piece.isEmpty()) continue
            if (piece.size == 1 && distance == 0 && index == 1) {
              piece = mutableListOf()
              justErased = true
            } else {
              piece.add(Position(position[0], position[1]))
              pieces.add(piece.toList())
              piece = mutableListOf()
              justErased = true
            }
          } else if (distance >= length - 1) {
            piece.add(Position(point.x, point.y))
          } else if (justErased || piece.isEmpty()) {
            piece.add(Position(position[0], position[1]))
            val offset = prior.x - position[0]
            measure.getPosTan(measure.length, endpoint, null)
            val direction = endpoint[0] - prior.x
            if ((offset > 0f && direction > 0f) || (offset < 0f && direction < 0f)) {
              piece.add(Position(point.x, point.y))
            }
            justErased = false
          }
        }
        wasSplit = true
      } else {
        piece.add(Position(point.x, point.y))
      }
    }
    if (piece.size > 1) pieces.add(piece.toList())
    return Result(wasSplit, pieces)
  }

  // Direct translation of PanelUtils.rectToRegion, including its coordinate order.
  private fun regionBetween(rect1: Rect, rect2: Rect): Region {
    val a = Rect(rect1)
    val b = Rect(rect2)
    fun exchange() { val tmp = Rect(a); a.set(b); b.set(tmp) }
    if (a.left > b.left) exchange()
    val path = Path()
    if (a.left == b.left) {
      if (a.top > b.top) exchange()
      path.moveTo(a.left.toFloat(), a.top.toFloat())
      path.lineTo(a.right.toFloat(), a.top.toFloat())
      path.lineTo(b.right.toFloat(), b.bottom.toFloat())
      path.lineTo(a.bottom.toFloat(), a.left.toFloat())
    } else if (a.top == b.top) {
      path.moveTo(a.left.toFloat(), a.top.toFloat())
      path.lineTo(b.right.toFloat(), b.top.toFloat())
      path.lineTo(b.right.toFloat(), b.bottom.toFloat())
      path.lineTo(a.bottom.toFloat(), a.left.toFloat())
    } else if (a.top > b.top) {
      path.moveTo(a.left.toFloat(), a.bottom.toFloat())
      path.lineTo(a.left.toFloat(), a.top.toFloat())
      path.lineTo(b.left.toFloat(), b.top.toFloat())
      path.lineTo(b.right.toFloat(), b.top.toFloat())
      path.lineTo(b.right.toFloat(), b.bottom.toFloat())
      path.lineTo(a.right.toFloat(), a.bottom.toFloat())
    } else {
      path.moveTo(a.left.toFloat(), a.bottom.toFloat())
      path.lineTo(a.left.toFloat(), a.top.toFloat())
      path.lineTo(a.right.toFloat(), a.top.toFloat())
      path.lineTo(b.right.toFloat(), b.top.toFloat())
      path.lineTo(b.right.toFloat(), b.bottom.toFloat())
      path.lineTo(b.left.toFloat(), b.bottom.toFloat())
    }
    path.close()
    val clip = Rect().apply { union(a); union(b) }
    return Region().apply { setPath(path, Region(clip)) }
  }
}
