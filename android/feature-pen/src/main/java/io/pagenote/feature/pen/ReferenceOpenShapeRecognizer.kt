package io.pagenote.feature.pen

import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.min

/** Open straight/arrow branch of GraphUtils.recogniseShape and createLine. */
internal object ReferenceOpenShapeRecognizer {
  fun recognize(
    sampledScreenPoints: List<Pair<Float, Float>>,
    geometry: PenCanvasGeometry,
    density: Float,
    strokeWidth: Float = 4f,
  ): PenRecognizedShape? {
    if (!geometry.isValid || !density.isFinite() || density <= 0f || sampledScreenPoints.size < 4) return null
    val points = sampledScreenPoints.map { (x, y) -> PenPoint(x, y, 1f) }
    val first = points.first()
    val last = points.last()
    if (points.size > 10 && hypot(first.x - last.x, first.y - last.y) < 12f * density) return null
    // GraphUtils passes toScreenWidth(15), which scales the reference image
    // width by the page's current image-to-screen transform.
    val corners = ReferenceBrokenLine.find(points, 15f * geometry.pixelsPerDocumentUnit)
    if (corners.isNotEmpty()) {
      val result = mutableListOf<PenPoint>()
      val alignment = geometry.documentUnitsForPixels(20f)
      for (index in listOf(0) + corners + points.lastIndex) {
        val point = points[index]
        result.add(geometry.toDocumentPoint(point.x, point.y, 1f) ?: return null)
        if (result.size >= 3) {
          val a = result[result.lastIndex - 2]
          var b = result[result.lastIndex - 1]
          var c = result[result.lastIndex]
          if (abs(a.x - b.x) < alignment) b = b.copy(x = a.x)
          if (abs(a.y - b.y) < alignment) b = b.copy(y = a.y)
          if (abs(b.x - c.x) < alignment) c = c.copy(x = b.x)
          if (abs(b.y - c.y) < alignment) c = c.copy(y = b.y)
          result[result.lastIndex - 1] = b
          result[result.lastIndex] = c
        }
      }
      if (result.distinct().size < 2) return null
      return PenRecognizedShape("polyline", result.first(), result.last(), result)
    }
    val classification = ReferenceLineClassifier.classify(points)
    if (classification.controlIndex == 0) {
      val endExclusive = if (classification.arrow) classification.arrowPosition else points.size
      if (endExclusive <= 3 || endExclusive > points.size) return null
      val end = points[endExclusive - 1]
      if (hypot(first.x - end.x, first.y - end.y) < (6f * density).toInt()) return null
      val documentFirst = geometry.toDocumentPoint(first.x, first.y, 1f) ?: return null
      val documentLast = geometry.toDocumentPoint(end.x, end.y, 1f) ?: return null
      return PenRecognizedShape(if (classification.arrow) "arrow" else "line", documentFirst, documentLast)
    }

    if (!classification.arrow && classification.controlIndex in 1 until points.lastIndex) {
      val left = points.minOf { it.x }
      val right = points.maxOf { it.x }
      val top = points.minOf { it.y }
      val bottom = points.maxOf { it.y }
      val direct = hypot(last.x - first.x, last.y - first.y)
      if (direct > min(right - left, bottom - top)) {
        // GraphUtils creates BezierTool(start, detected point, end). BezierTool
        // turns that on-curve midpoint into the quadratic's control point.
        val detected = points[classification.controlIndex]
        val midpointX = (first.x + last.x) / 2f
        val midpointY = (first.y + last.y) / 2f
        val controlX = midpointX + 2f * (detected.x - midpointX)
        val controlY = midpointY + 2f * (detected.y - midpointY)
        val curvePoints = (0..32).map { step ->
          val t = step / 32f
          val inverse = 1f - t
          val x = inverse * inverse * first.x + 2f * inverse * t * controlX + t * t * last.x
          val y = inverse * inverse * first.y + 2f * inverse * t * controlY + t * t * last.y
          geometry.toDocumentPoint(x, y, 1f) ?: return null
        }
        return PenRecognizedShape("curve", curvePoints.first(), curvePoints.last(), curvePoints)
      }
    }

    // GraphUtils falls through to isCurveAndCreate when the detected Bezier
    // midpoint is unsuitable, and also when isLine did not classify a shape.
    val fittedCurve = ReferenceCurveFitter.fit(
      points = points,
      density = density,
      arrowPosition = classification.arrow.takeIf { it }?.let { classification.arrowPosition },
      strokeWidth = strokeWidth,
    )
    if (fittedCurve != null) {
      val curvePoints = fittedCurve.map { (x, y) -> geometry.toDocumentPoint(x, y, 1f) ?: return null }
      return PenRecognizedShape("curve", curvePoints.first(), curvePoints.last(), curvePoints)
    }

    return null
  }
}
