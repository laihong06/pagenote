package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceOpenShapeRecognizerTest {
  @Test fun referenceBrokenLineProducesOnePolyline() {
    val geometry = PenCanvasGeometry(600f, 600f)
    val horizontal = (0..20).map { 50f + it * 5f to 100f }
    val vertical = (1..20).map { 150f to 100f + it * 5f }
    val shape = ReferenceOpenShapeRecognizer.recognize(horizontal + vertical, geometry, 1f)
    assertEquals("polyline", shape?.type)
  }
  @Test fun arrowLikePolylineKeepsReferenceBrokenLinePriority() {
    val geometry = PenCanvasGeometry(600f, 600f)
    val shaft = (0..30).map { 40f + it * 10f to 300f }
    val wing = (1..8).map { 340f - it * 5f to 300f - it * 5f }
    val returnToTip = (1..8).map { 300f + it * 5f to 260f + it * 5f }
    val otherWing = (1..8).map { 340f - it * 5f to 300f + it * 5f }
    val sampled = shaft + wing + returnToTip + otherWing
    val screenPoints = sampled.map { (x, y) -> PenPoint(x, y, 1f) }
    assertTrue(ReferenceBrokenLine.find(screenPoints, 15f).isNotEmpty())
    assertEquals("polyline", ReferenceOpenShapeRecognizer.recognize(sampled, geometry, 1f)?.type)
  }
  @Test fun convertsSampledScreenEndpointsThroughZoomPanAndRotation() {
    val geometry = PenCanvasGeometry(600f, 800f,
      PenCanvasPageTransform(400f, 600f, scale = 1.5f, translationX = 15f, rotationDegrees = 90f))
    val sampled = (0..8).map { 100f + it * 30f to 250f }
    val shape = ReferenceOpenShapeRecognizer.recognize(sampled, geometry, 2f)!!
    assertEquals("line", shape.type)
    assertEquals(geometry.toDocumentPoint(100f, 250f, 1f), shape.first)
    assertEquals(geometry.toDocumentPoint(340f, 250f, 1f), shape.second)
  }

  @Test fun closedAndTooShortPathsRemainForOtherBranches() {
    val geometry = PenCanvasGeometry(500f, 500f)
    val closed = (0..16).map { index ->
      val a = index * Math.PI * 2 / 16
      (250 + 100 * kotlin.math.cos(a)).toFloat() to (250 + 100 * kotlin.math.sin(a)).toFloat()
    }
    assertNull(ReferenceOpenShapeRecognizer.recognize(closed, geometry, 2f))
    assertNull(ReferenceOpenShapeRecognizer.recognize(listOf(0f to 0f, 5f to 0f, 10f to 0f), geometry, 2f))
  }
}
