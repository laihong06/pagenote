package io.pagenote.feature.pen

import kotlin.math.hypot

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
    if (ReferenceBrokenLine.find(points, 15f * geometry.scale).isNotEmpty()) return null
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
