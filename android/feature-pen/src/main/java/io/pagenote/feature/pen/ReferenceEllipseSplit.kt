package io.pagenote.feature.pen

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs

/** Reader port of EllipseTool.split in reference material coordinates. */
internal object ReferenceEllipseSplit {
  const val AUTO_SHAPE_LINE_FACTOR = 0.8f

  data class Result(
    val split: Boolean,
    val pieces: List<List<ReferencePenSplit.Position>>,
    val fragmentWidth: Float,
  )

  fun split(
    centerX: Float,
    centerY: Float,
    horizontalX: Float,
    verticalY: Float,
    transform: Matrix,
    strokeWidth: Float,
    shapeBounds: Rect,
    previous: Rect,
    current: Rect,
    autoShapeLineFactor: Float = AUTO_SHAPE_LINE_FACTOR,
  ): Result {
    val fragmentWidth = strokeWidth * autoShapeLineFactor
    val margin = (strokeWidth / 3f).toInt()
    val swept = Rect().apply {
      union(Rect(previous).apply { inset(-margin, -margin) })
      union(Rect(current).apply { inset(-margin, -margin) })
    }
    if (!shapeBounds.contains(swept) && !swept.contains(shapeBounds) &&
      !Rect.intersects(shapeBounds, swept)) return Result(false, emptyList(), fragmentWidth)

    val rx = abs(horizontalX - centerX)
    val ry = abs(verticalY - centerY)
    val path = Path().apply {
      addOval(RectF(centerX - rx, centerY - ry, centerX + rx, centerY + ry), Path.Direction.CCW)
      transform(transform)
    }
    val measure = PathMeasure(path, false)
    val last = (measure.length - 1f).toInt()
    val point = FloatArray(2)
    val positions = mutableListOf<ReferencePenSplit.Position>()
    for (distance in 0..last step 2) {
      measure.getPosTan(distance.toFloat(), point, null)
      positions.add(ReferencePenSplit.Position(point[0], point[1]))
    }
    val bounds = ReferencePenSplit.buildDefaultBounds(positions, fragmentWidth)
    val result = ReferencePenSplit.split(bounds, fragmentWidth, previous, current)
    return Result(result.split, result.pieces, fragmentWidth)
  }
}
