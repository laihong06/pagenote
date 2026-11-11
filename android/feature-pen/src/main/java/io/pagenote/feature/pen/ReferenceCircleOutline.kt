package io.pagenote.feature.pen

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/** CircleTool.split sampling stage, retained separately until mixed erasure is transactional. */
internal object ReferenceCircleOutline {
  fun sample(centerX: Float, centerY: Float, edgeX: Float, edgeY: Float): List<PenPoint> {
    require(listOf(centerX, centerY, edgeX, edgeY).all { it.isFinite() })
    val radius = hypot((edgeX - centerX).toDouble(), (edgeY - centerY).toDouble())
    val step = max((50.0 / sqrt(radius)).toInt(), 2)
    // Preserve the reference's Int addition, including the zero-radius overflow case.
    val end = step + 360
    val points = mutableListOf<PenPoint>()
    var angle = 0f
    while (angle <= end) {
      val radians = (angle - 90.0) * Math.PI / 180.0
      points.add(PenPoint(
        (centerX - sin(radians) * radius).toFloat(),
        (centerY - cos(radians) * radius).toFloat(),
        1f,
      ))
      angle += step
    }
    return points
  }
}
