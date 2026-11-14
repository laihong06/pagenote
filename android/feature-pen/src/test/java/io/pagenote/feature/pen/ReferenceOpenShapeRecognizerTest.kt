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

  @Test fun brokenLineThresholdUsesThePageToScreenScaleFromReference() {
    val geometry = PenCanvasGeometry(
      400f,
      600f,
      PenCanvasPageTransform(pageWidthPx = 400f, pageHeightPx = 600f),
    )
    val sampled = (0..10).map { 100f + it to 100f } +
      (1..30).map { 110f to 100f + it }

    val shape = ReferenceOpenShapeRecognizer.recognize(sampled, geometry, density = 2f)

    assertEquals("polyline", shape?.type)
    assertEquals(3, shape?.points?.size)
  }

  @Test fun referenceCurveUsesTheDetectedControlPoint() {
    val geometry = PenCanvasGeometry(1000f, 800f)
    val sampled = (0..40).map { step ->
      val t = step / 40f
      val u = 1f - t
      val x = 100f * u * u + 500f * 2f * u * t + 900f * t * t
      val y = 600f * u * u + 100f * 2f * u * t + 600f * t * t
      x to y
    }
    val shape = ReferenceOpenShapeRecognizer.recognize(sampled, geometry, 1f)
    assertEquals("curve", shape?.type)
    assertEquals(33, shape?.points?.size)
    assertEquals(350f, shape!!.points[16].y, 1f)
  }

  @Test fun referenceCurveFallbackFitsAnOpenCurveRejectedByTheBezierBranch() {
    val geometry = PenCanvasGeometry(2000f, 2000f)
    val sampled = (0..40).map { step ->
      val t = step / 40f
      val x = 100f + 100f * t
      val y = 100f + 105f * 4f * t * (1f - t)
      x to y
    }
    val classified = ReferenceLineClassifier.classify(sampled.map { (x, y) -> PenPoint(x, y, 1f) })
    assertTrue(classified.controlIndex in 1 until sampled.lastIndex)
    assertTrue(
      "reference fitter should accept the curve before integration",
      ReferenceCurveFitter.fit(sampled.map { (x, y) -> PenPoint(x, y, 1f) }, density = 2f) != null,
    )
    assertEquals(
      sampled.size,
      ReferenceCurveFitter.fit(sampled.map { (x, y) -> PenPoint(x, y, 1f) }, density = 2f)?.size,
    )

    val shape = ReferenceOpenShapeRecognizer.recognize(sampled, geometry, density = 2f)

    assertEquals("curve", shape?.type)
    assertTrue(shape!!.points.size > 20)
  }

  @Test fun curveFallbackAppendsReferenceArrowHeadForArrowClassifiedInput() {
    val shaft = (0..40).map { step ->
      val t = step / 40f
      100f + 100f * t to 100f + 105f * 4f * t * (1f - t)
    }
    val arrowHead = (1..12).map { step ->
      200f - step * 0.8f to 100f - step * 0.8f
    }
    val shaftPoints = shaft.map { (x, y) -> PenPoint(x, y, 1f) }
    val inputPoints = (shaft + arrowHead).map { (x, y) -> PenPoint(x, y, 1f) }
    val shaftFit = ReferenceCurveFitter.fit(shaftPoints, density = 2f, arrowPosition = shaft.size)
    val arrowFit = ReferenceCurveFitter.fit(inputPoints, density = 2f, arrowPosition = shaft.size)

    assertTrue("the reference app appends a generated arrow head after the fitted shaft", arrowFit!!.size > shaftFit!!.size)
  }

  @Test fun curveFallbackRetainsArrowHeadWhenOpenShapeRecognizerHandlesTheStroke() {
    val shaft = (0..40).map { step ->
      val t = step / 40f
      100f + 100f * t to 100f + 105f * 4f * t * (1f - t)
    }
    val tip = shaft.last()
    val upperWing = (1..12).map { step -> tip.first - step * (4f / 12f) to tip.second - step * (4f / 12f) }
    val returnToTip = (1..12).map { step -> tip.first - 4f + step * (4f / 12f) to tip.second - 4f + step * (4f / 12f) }
    val lowerWing = (1..12).map { step -> tip.first - step * (4f / 12f) to tip.second + step * (4f / 12f) }
    val sampled = shaft + upperWing + returnToTip + lowerWing
    val classification = ReferenceLineClassifier.classify(sampled.map { (x, y) -> PenPoint(x, y, 1f) })
    val geometry = PenCanvasGeometry(1000f, 800f)

    assertTrue(classification.arrow)
    assertTrue(classification.controlIndex != 0)
    assertTrue(ReferenceBrokenLine.find(sampled.map { (x, y) -> PenPoint(x, y, 1f) }, 15f * geometry.pixelsPerDocumentUnit).isEmpty())
    val shape = ReferenceOpenShapeRecognizer.recognize(sampled, geometry, density = 2f)

    assertEquals("curve", shape?.type)
    assertTrue(requireNotNull(shape).points.size > shaft.size)
  }

  @Test fun curveFallbackRejectsInputsTooShortForItsSixCoefficientFit() {
    val points = (0..4).map { PenPoint(it * 10f, it * 5f, 1f) }

    assertNull(ReferenceCurveFitter.fit(points, density = 2f))
  }

  @Test fun sampledFlatLineFollowsTheReferenceBezierBranch() {
    val points = (0..40).map { (40f + it * 5f) to 80f }
    val classified = ReferenceLineClassifier.classify(points.map { (x, y) -> PenPoint(x, y, 1f) })
    assertEquals(6, classified.controlIndex)
    val shape = ReferenceOpenShapeRecognizer.recognize(points, PenCanvasGeometry(500f, 500f), 3f)
    assertEquals("curve", shape?.type)
    assertTrue(shape!!.points.all { kotlin.math.abs(it.y - shape.points.first().y) < .01f })
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
