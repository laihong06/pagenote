package io.pagenote.feature.pen

/**
 * Render-time midpoint quadratic smoothing for freehand ink: samples become
 * control points, segment boundaries sit on sample midpoints, and the chain
 * still starts and ends on real samples. Storage always keeps raw points.
 */
object PenStrokeSmoother {

  data class Segment(
    val startX: Float,
    val startY: Float,
    val controlX: Float,
    val controlY: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
  )

  fun segments(points: List<PenPoint>, baseWidth: Float): List<Segment> {
    if (points.size < 3) return emptyList()
    val result = ArrayList<Segment>(points.size - 1)
    var startX = points.first().x
    var startY = points.first().y
    for (index in 1 until points.size - 1) {
      val control = points[index]
      val next = points[index + 1]
      val endX = (control.x + next.x) / 2f
      val endY = (control.y + next.y) / 2f
      result.add(
        Segment(
          startX = startX,
          startY = startY,
          controlX = control.x,
          controlY = control.y,
          endX = endX,
          endY = endY,
          width = baseWidth * (control.pressure + next.pressure) / 2f,
        ),
      )
      startX = endX
      startY = endY
    }
    val previous = points[points.size - 2]
    val last = points.last()
    result.add(
      Segment(
        startX = startX,
        startY = startY,
        controlX = previous.x,
        controlY = previous.y,
        endX = last.x,
        endY = last.y,
        width = baseWidth * (previous.pressure + last.pressure) / 2f,
      ),
    )
    return result
  }
}
