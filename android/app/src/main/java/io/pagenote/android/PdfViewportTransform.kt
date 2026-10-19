package io.pagenote.android

import io.pagenote.core.reader.PdfTextPoint

data class PdfViewportBounds(
  val viewportWidthPx: Float,
  val viewportHeightPx: Float,
  val pageWidthPx: Float,
  val pageHeightPx: Float,
) {
  companion object {
    val ZERO = PdfViewportBounds(0f, 0f, 0f, 0f)
  }
}

data class PdfSelectionPoint(val x: Float, val y: Float)

data class NormalizedOcrRegion(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
)

fun mapPdfSelectionToOcrRegion(
  start: PdfSelectionPoint,
  end: PdfSelectionPoint,
  bounds: PdfViewportBounds,
  transform: PdfViewportTransform,
): NormalizedOcrRegion? {
  val measurements = listOf(
    start.x,
    start.y,
    end.x,
    end.y,
    bounds.viewportWidthPx,
    bounds.viewportHeightPx,
    bounds.pageWidthPx,
    bounds.pageHeightPx,
    transform.scale,
    transform.translationX,
    transform.translationY,
  )
  if (measurements.any { !it.isFinite() } ||
    bounds.viewportWidthPx <= 0f || bounds.viewportHeightPx <= 0f ||
    bounds.pageWidthPx <= 0f || bounds.pageHeightPx <= 0f || transform.scale <= 0f
  ) {
    return null
  }

  fun toPage(point: PdfSelectionPoint): PdfSelectionPoint = PdfSelectionPoint(
    x = (point.x - bounds.viewportWidthPx / 2f - transform.translationX) /
      transform.scale + bounds.pageWidthPx / 2f,
    y = (point.y - bounds.viewportHeightPx / 2f - transform.translationY) /
      transform.scale + bounds.pageHeightPx / 2f,
  )

  val pageStart = toPage(start)
  val pageEnd = toPage(end)
  if (!pageStart.x.isFinite() || !pageStart.y.isFinite() ||
    pageStart.x < 0f || pageStart.x > bounds.pageWidthPx ||
    pageStart.y < 0f || pageStart.y > bounds.pageHeightPx
  ) {
    return null
  }

  val endX = pageEnd.x.coerceIn(0f, bounds.pageWidthPx)
  val endY = pageEnd.y.coerceIn(0f, bounds.pageHeightPx)
  val left = minOf(pageStart.x, endX) / bounds.pageWidthPx * 1000f
  val top = minOf(pageStart.y, endY) / bounds.pageHeightPx * 1000f
  val right = maxOf(pageStart.x, endX) / bounds.pageWidthPx * 1000f
  val bottom = maxOf(pageStart.y, endY) / bounds.pageHeightPx * 1000f

  val region = NormalizedOcrRegion(
    left = kotlin.math.floor(left).toInt().coerceIn(0, 1000),
    top = kotlin.math.floor(top).toInt().coerceIn(0, 1000),
    right = kotlin.math.ceil(right).toInt().coerceIn(0, 1000),
    bottom = kotlin.math.ceil(bottom).toInt().coerceIn(0, 1000),
  )
  return region.takeIf { it.right > it.left && it.bottom > it.top }
}

fun mapPdfTextSelectionToPagePoints(
  start: PdfSelectionPoint,
  end: PdfSelectionPoint,
  bounds: PdfViewportBounds,
  transform: PdfViewportTransform,
  pageWidth: Double,
  pageHeight: Double,
): Pair<PdfTextPoint, PdfTextPoint>? {
  if (listOf(
      start.x, start.y, end.x, end.y,
      bounds.viewportWidthPx, bounds.viewportHeightPx,
      bounds.pageWidthPx, bounds.pageHeightPx,
      transform.scale, transform.translationX, transform.translationY,
    ).any { !it.isFinite() } ||
    !pageWidth.isFinite() || !pageHeight.isFinite() ||
    bounds.viewportWidthPx <= 0f || bounds.viewportHeightPx <= 0f ||
    bounds.pageWidthPx <= 0f || bounds.pageHeightPx <= 0f || transform.scale <= 0f ||
    pageWidth <= 0.0 || pageHeight <= 0.0
  ) {
    return null
  }

  fun toPage(point: PdfSelectionPoint) = PdfSelectionPoint(
    x = (point.x - bounds.viewportWidthPx / 2f - transform.translationX) /
      transform.scale + bounds.pageWidthPx / 2f,
    y = (point.y - bounds.viewportHeightPx / 2f - transform.translationY) /
      transform.scale + bounds.pageHeightPx / 2f,
  )

  val pageStart = toPage(start)
  val pageEnd = toPage(end)
  if (listOf(pageStart.x, pageStart.y, pageEnd.x, pageEnd.y).any { !it.isFinite() } ||
    pageStart.x !in 0f..bounds.pageWidthPx || pageStart.y !in 0f..bounds.pageHeightPx
  ) {
    return null
  }

  fun toPdfPoint(x: Float, y: Float) = PdfTextPoint(
    x = x / bounds.pageWidthPx * pageWidth,
    y = y / bounds.pageHeightPx * pageHeight,
  )

  return toPdfPoint(pageStart.x, pageStart.y) to toPdfPoint(
    pageEnd.x.coerceIn(0f, bounds.pageWidthPx),
    pageEnd.y.coerceIn(0f, bounds.pageHeightPx),
  )
}

/** Map a tap to desktop annotation coordinates; portrait-page Y may exceed 1000. */
fun mapPdfTapToAnnotationPoint(
  tap: PdfSelectionPoint,
  bounds: PdfViewportBounds,
  transform: PdfViewportTransform,
): Pair<Double, Double>? {
  if (listOf(
      tap.x, tap.y,
      bounds.viewportWidthPx, bounds.viewportHeightPx,
      bounds.pageWidthPx, bounds.pageHeightPx,
      transform.scale, transform.translationX, transform.translationY,
    ).any { !it.isFinite() } ||
    bounds.viewportWidthPx <= 0f || bounds.viewportHeightPx <= 0f ||
    bounds.pageWidthPx <= 0f || bounds.pageHeightPx <= 0f || transform.scale <= 0f
  ) {
    return null
  }
  val pageX = (tap.x - bounds.viewportWidthPx / 2f - transform.translationX) /
    transform.scale + bounds.pageWidthPx / 2f
  val pageY = (tap.y - bounds.viewportHeightPx / 2f - transform.translationY) /
    transform.scale + bounds.pageHeightPx / 2f
  if (!pageX.isFinite() || !pageY.isFinite() ||
    pageX < 0f || pageX > bounds.pageWidthPx || pageY < 0f || pageY > bounds.pageHeightPx
  ) {
    return null
  }
  return (pageX / bounds.pageWidthPx * 1000.0) to (pageY / bounds.pageWidthPx * 1000.0)
}

data class PdfAnnotationPagePoints(
  val first: Pair<Double, Double>,
  val second: Pair<Double, Double>,
)

/** Map a drag through the viewport transform using desktop's page-width coordinate scale. */
fun mapPdfAnnotationDragToPoints(
  start: PdfSelectionPoint,
  end: PdfSelectionPoint,
  bounds: PdfViewportBounds,
  transform: PdfViewportTransform,
): PdfAnnotationPagePoints? {
  if (listOf(
      start.x, start.y, end.x, end.y,
      bounds.viewportWidthPx, bounds.viewportHeightPx,
      bounds.pageWidthPx, bounds.pageHeightPx,
      transform.scale, transform.translationX, transform.translationY,
    ).any { !it.isFinite() } ||
    bounds.viewportWidthPx <= 0f || bounds.viewportHeightPx <= 0f ||
    bounds.pageWidthPx <= 0f || bounds.pageHeightPx <= 0f || transform.scale <= 0f
  ) {
    return null
  }

  fun toPage(point: PdfSelectionPoint) = PdfSelectionPoint(
    x = (point.x - bounds.viewportWidthPx / 2f - transform.translationX) /
      transform.scale + bounds.pageWidthPx / 2f,
    y = (point.y - bounds.viewportHeightPx / 2f - transform.translationY) /
      transform.scale + bounds.pageHeightPx / 2f,
  )

  val pageStart = toPage(start)
  val pageEnd = toPage(end)
  if (listOf(pageStart.x, pageStart.y, pageEnd.x, pageEnd.y).any { !it.isFinite() } ||
    pageStart.x !in 0f..bounds.pageWidthPx || pageStart.y !in 0f..bounds.pageHeightPx
  ) {
    return null
  }

  fun toAnnotationPoint(x: Float, y: Float) =
    (x / bounds.pageWidthPx * 1000.0) to (y / bounds.pageWidthPx * 1000.0)

  return PdfAnnotationPagePoints(
    first = toAnnotationPoint(pageStart.x, pageStart.y),
    second = toAnnotationPoint(
      pageEnd.x.coerceIn(0f, bounds.pageWidthPx),
      pageEnd.y.coerceIn(0f, bounds.pageHeightPx),
    ),
  )
}

data class PdfViewportPointer(val id: Long, val x: Float, val y: Float)

data class PdfViewportGesture(
  val centroidX: Float,
  val centroidY: Float,
  val zoomChange: Float,
  val panX: Float,
  val panY: Float,
)

data class PdfViewportTransform(
  val scale: Float,
  val translationX: Float,
  val translationY: Float,
) {
  companion object {
    val DEFAULT = PdfViewportTransform(scale = 1f, translationX = 0f, translationY = 0f)
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 6f

    fun calculateTwoPointerGesture(
      previous: List<PdfViewportPointer>,
      current: List<PdfViewportPointer>,
    ): PdfViewportGesture? {
      val previousById = previous.associateBy(PdfViewportPointer::id)
      val matching = current.mapNotNull { pointer ->
        previousById[pointer.id]?.let { it to pointer }
      }
      if (matching.size < 2 || matching.any { (before, after) ->
          listOf(before.x, before.y, after.x, after.y).any { !it.isFinite() }
        }
      ) {
        return null
      }

      val previousCentroidX = matching.sumOf { it.first.x.toDouble() }.toFloat() / matching.size
      val previousCentroidY = matching.sumOf { it.first.y.toDouble() }.toFloat() / matching.size
      val centroidX = matching.sumOf { it.second.x.toDouble() }.toFloat() / matching.size
      val centroidY = matching.sumOf { it.second.y.toDouble() }.toFloat() / matching.size
      val previousRadius = matching.sumOf { (before, _) ->
        distance(before.x, before.y, previousCentroidX, previousCentroidY).toDouble()
      }.toFloat() / matching.size
      val currentRadius = matching.sumOf { (_, after) ->
        distance(after.x, after.y, centroidX, centroidY).toDouble()
      }.toFloat() / matching.size

      return PdfViewportGesture(
        centroidX = centroidX,
        centroidY = centroidY,
        zoomChange = if (previousRadius > 0f) currentRadius / previousRadius else 1f,
        panX = centroidX - previousCentroidX,
        panY = centroidY - previousCentroidY,
      )
    }

    fun applyGesture(
      current: PdfViewportTransform,
      viewportWidthPx: Float,
      viewportHeightPx: Float,
      pageWidthPx: Float,
      pageHeightPx: Float,
      centroidX: Float,
      centroidY: Float,
      zoomChange: Float,
      panX: Float,
      panY: Float,
    ): PdfViewportTransform {
      val measurements = listOf(
        viewportWidthPx,
        viewportHeightPx,
        pageWidthPx,
        pageHeightPx,
        centroidX,
        centroidY,
        zoomChange,
        panX,
        panY,
        current.scale,
        current.translationX,
        current.translationY,
      )
      if (measurements.any { !it.isFinite() } ||
        viewportWidthPx <= 0f || viewportHeightPx <= 0f ||
        pageWidthPx <= 0f || pageHeightPx <= 0f ||
        zoomChange <= 0f || current.scale <= 0f
      ) {
        return current
      }

      val nextScale = (current.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
      val scaleRatio = nextScale / current.scale
      val nextX = current.translationX * scaleRatio +
        (centroidX - viewportWidthPx / 2f) * (1f - scaleRatio) + panX
      val nextY = current.translationY * scaleRatio +
        (centroidY - viewportHeightPx / 2f) * (1f - scaleRatio) + panY
      val maxTranslationX = maxOf(0f, (pageWidthPx * nextScale - viewportWidthPx) / 2f)
      val maxTranslationY = maxOf(0f, (pageHeightPx * nextScale - viewportHeightPx) / 2f)
      val clampedX = nextX.coerceIn(-maxTranslationX, maxTranslationX)
      val clampedY = nextY.coerceIn(-maxTranslationY, maxTranslationY)
      return PdfViewportTransform(
        scale = nextScale,
        translationX = if (clampedX == 0f) 0f else clampedX,
        translationY = if (clampedY == 0f) 0f else clampedY,
      )
    }

    fun fitWidthScale(viewportWidthPx: Float, pageWidthPx: Float): Float {
      if (!viewportWidthPx.isFinite() || !pageWidthPx.isFinite() ||
        viewportWidthPx <= 0f || pageWidthPx <= 0f
      ) {
        return MIN_SCALE
      }
      return (viewportWidthPx / pageWidthPx).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun fitWidth(viewportWidthPx: Float, pageWidthPx: Float): PdfViewportTransform =
      PdfViewportTransform(
        scale = fitWidthScale(viewportWidthPx, pageWidthPx),
        translationX = 0f,
        translationY = 0f,
      )

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
      kotlin.math.hypot(x1 - x2, y1 - y2)
  }
}
