package io.pagenote.feature.pen

import kotlin.math.hypot
import kotlin.math.abs

/** Open straight/arrow branch of GraphUtils.recogniseShape and createLine. */
internal object ReferenceOpenShapeRecognizer {
  fun recognize(
    sampledScreenPoints: List<Pair<Float, Float>>,
    geometry: PenCanvasGeometry,
    density: Float,
  ): PenRecognizedShape? {
    if (!geometry.isValid || !density.isFinite() || density <= 0f || sampledScreenPoints.size < 4) return null
    val points = sampledScreenPoints.map { (x, y) -> PenPoint(x, y, 1f) }
    val first = points.first()
    val last = points.last()
    if (points.size > 10 && hypot(first.x - last.x, first.y - last.y) < 12f * density) return null
    // PageNote's viewport scale approximates the reference image-to-screen scale.
    val corners = ReferenceBrokenLine.find(points, 15f * geometry.scale)
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
    if (classification.controlIndex != 0) return null
    val endExclusive = if (classification.arrow) classification.arrowPosition else points.size
    if (endExclusive <= 3 || endExclusive > points.size) return null
    val end = points[endExclusive - 1]
    if (hypot(first.x - end.x, first.y - end.y) < (6f * density).toInt()) return null
    val documentFirst = geometry.toDocumentPoint(first.x, first.y, 1f) ?: return null
    val documentLast = geometry.toDocumentPoint(end.x, end.y, 1f) ?: return null
    return PenRecognizedShape(if (classification.arrow) "arrow" else "line", documentFirst, documentLast)
  }
}
