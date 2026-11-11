package io.pagenote.feature.pen

import android.graphics.Rect

/** CircleTool.split geometry in reference pixel coordinates; not wired to file editing. */
internal object ReferenceCircleSplit {
  data class Result(
    val split: Boolean,
    val pieces: List<List<ReferencePenSplit.Position>>,
    val fragmentWidth: Float,
  )

  fun split(
    centerX: Float,
    centerY: Float,
    edgeX: Float,
    edgeY: Float,
    strokeWidth: Float,
    shapeBounds: Rect,
    previous: Rect,
    current: Rect,
    autoShapeLineFactor: Float = 0.8f,
  ): Result {
    val fragmentWidth = strokeWidth * autoShapeLineFactor * 1.2f
    val margin = (strokeWidth / 3f).toInt()
    val sweptBounds = Rect().apply {
      union(Rect(previous).apply { inset(-margin, -margin) })
      union(Rect(current).apply { inset(-margin, -margin) })
    }
    if (!shapeBounds.contains(sweptBounds) && !sweptBounds.contains(shapeBounds) &&
      !Rect.intersects(shapeBounds, sweptBounds)) {
      return Result(false, emptyList(), fragmentWidth)
    }
    val outline = ReferenceCircleOutline.sample(centerX, centerY, edgeX, edgeY)
    val positions = outline.map { ReferencePenSplit.Position(it.x, it.y) }
    val points = ReferencePenSplit.buildDefaultBounds(positions, fragmentWidth)
    val result = ReferencePenSplit.split(points, fragmentWidth, previous, current)
    return Result(result.split, result.pieces, fragmentWidth)
  }
}
