package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PenCanvasGeometryTest {
  @Test
  fun mapsCoordinatesUsingDesktopWidthScaleAndClampsToCanvas() {
    val geometry = PenCanvasGeometry(widthPx = 200f, heightPx = 300f)

    assertEquals(PenPoint(0f, 0f, 0.5f), geometry.toDocumentPoint(0f, 0f, 0.5f))
    assertEquals(PenPoint(1000f, 1500f, 0.5f), geometry.toDocumentPoint(200f, 300f, 0.5f))
    assertEquals(PenPoint(0f, 750f, 1f), geometry.toDocumentPoint(-10f, 150f, null))
    assertEquals(PenPoint(1000f, 1500f, 1f), geometry.toDocumentPoint(500f, 500f, null))
  }

  @Test
  fun invalidCanvasDimensionsDoNotProducePoints() {
    assertNull(PenCanvasGeometry(0f, 100f).toDocumentPoint(0f, 0f, 1f))
    assertNull(PenCanvasGeometry(100f, Float.NaN).toDocumentPoint(0f, 0f, 1f))
    assertNull(PenCanvasGeometry(100f, 100f).toDocumentPoint(Float.NaN, 0f, 1f))
  }

  @Test
  fun pressureUsesDefaultWhenMissingAndClampsReportedValues() {
    val geometry = PenCanvasGeometry(widthPx = 100f, heightPx = 100f)

    assertEquals(1f, geometry.toDocumentPoint(10f, 10f, null)!!.pressure)
    assertEquals(1f, geometry.toDocumentPoint(10f, 10f, 0f)!!.pressure)
    assertEquals(1f, geometry.toDocumentPoint(10f, 10f, Float.NaN)!!.pressure)
    assertEquals(1f, geometry.toDocumentPoint(10f, 10f, 1.5f)!!.pressure)
    assertEquals(0.35f, geometry.toDocumentPoint(10f, 10f, 0.35f)!!.pressure)
  }

  @Test
  fun hitTestFindsTopmostFreehandStrokeWithinPixelRadius() {
    val geometry = PenCanvasGeometry(widthPx = 100f, heightPx = 100f)
    val strokes = listOf(
      stroke(listOf(PenPoint(100f, 500f, 1f), PenPoint(900f, 500f, 1f))),
      stroke(listOf(PenPoint(500f, 100f, 1f), PenPoint(500f, 900f, 1f))),
    )

    assertEquals(1, geometry.hitTest(strokes, xPx = 50f, yPx = 50f, radiusPx = 2f))
    assertNull(geometry.hitTest(strokes, xPx = 5f, yPx = 5f, radiusPx = 2f))
  }

  @Test
  fun hitTestFollowsRectangleAndEllipseOutlines() {
    val geometry = PenCanvasGeometry(widthPx = 100f, heightPx = 100f)
    val points = listOf(PenPoint(100f, 100f, 1f), PenPoint(900f, 900f, 1f))
    val rectangle = stroke(points).copy(type = "rect")
    val ellipse = stroke(points).copy(type = "ellipse")

    assertEquals(0, geometry.hitTest(listOf(rectangle), xPx = 50f, yPx = 10f, radiusPx = 1f))
    assertEquals(0, geometry.hitTest(listOf(ellipse), xPx = 50f, yPx = 10f, radiusPx = 1f))
    assertNull(geometry.hitTest(listOf(ellipse), xPx = 50f, yPx = 50f, radiusPx = 1f))
  }

  @Test
  fun hitTestIncludesArrowHeadButIgnoresNonCanvasAnnotations() {
    val geometry = PenCanvasGeometry(widthPx = 100f, heightPx = 100f)
    val arrow = stroke(listOf(PenPoint(100f, 500f, 1f), PenPoint(900f, 500f, 1f)))
      .copy(type = "arrow")
    val text = arrow.copy(type = "text")

    assertEquals(0, geometry.hitTest(listOf(arrow), xPx = 89f, yPx = 49.4f, radiusPx = 0.5f))
    assertNull(geometry.hitTest(listOf(text), xPx = 50f, yPx = 50f, radiusPx = 2f))
  }

  @Test
  fun mapsViewportPointsThroughZoomAndPanAndRendersBackToSameScreenLocation() {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        translationX = 30f,
        translationY = -40f,
      ),
    )

    val stored = geometry.toDocumentPoint(330f, 360f, 0.6f)

    assertEquals(PenPoint(500f, 750f, 0.6f), stored)
    assertEquals(330f, geometry.toViewX(stored!!), 0.01f)
    assertEquals(360f, geometry.toViewY(stored), 0.01f)
    assertEquals(0.8f, geometry.pixelsPerDocumentUnit, 0.001f)
    assertEquals(5f, geometry.documentUnitsForPixels(4f), 0.001f)
  }

  @Test
  fun extendedCanvasStoresVisibleMarginCoordinatesOnlyWhenEnabled() {
    val clipped = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(400f, 600f, allowOutsidePage = false),
    )
    val extended = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(400f, 600f, allowOutsidePage = true),
    )

    assertEquals(0f, clipped.toDocumentPoint(50f, 400f, 1f)!!.x, 0.01f)
    assertEquals(-125f, extended.toDocumentPoint(50f, 400f, 1f)!!.x, 0.01f)
    assertEquals(-125f, extended.toDocumentPoint(300f, 50f, 1f)!!.y, 0.01f)
  }

  @Test
  fun hitTestingUsesScreenSpaceAfterViewportTransform() {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        translationX = 30f,
        translationY = -40f,
      ),
    )
    val line = stroke(listOf(PenPoint(100f, 750f, 1f), PenPoint(900f, 750f, 1f)))

    assertEquals(0, geometry.hitTest(listOf(line), xPx = 330f, yPx = 360f, radiusPx = 2f))
    assertEquals(1.6f, geometry.strokeWidthPx(line), 0.01f)
  }

  @Test
  fun zeroRotationKeepsAxisProjectionsAndPairedMappingAligned() {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        translationX = 30f,
        translationY = -40f,
        rotationDegrees = 0f,
      ),
    )
    val point = PenPoint(500f, 750f, 1f)

    val (viewX, viewY) = geometry.toViewPoint(point)
    assertEquals(geometry.toViewX(point), viewX, 0.001f)
    assertEquals(geometry.toViewY(point), viewY, 0.001f)
    assertEquals(point.copy(pressure = 0.5f), geometry.toDocumentPoint(viewX, viewY, 0.5f))
  }

  @Test
  fun quarterTurnRotatesCornersAroundThePageCenter() {
    // pageOrigin = (-100, -200), page pixel size 800x1200, center (300, 400).
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        rotationDegrees = 90f,
      ),
    )

    // Document (0,0) sits at px (-100,-200); a 90° clockwise turn moves it
    // to the top-right corner of the rotated page.
    val (x, y) = geometry.toViewPoint(PenPoint(0f, 0f, 1f))
    assertEquals(900f, x, 0.01f)
    assertEquals(0f, y, 0.01f)
    assertEquals(PenPoint(0f, 0f, 1f), geometry.toDocumentPoint(x, y, 1f))

    // The page center never moves.
    val (centerX, centerY) = geometry.toViewPoint(PenPoint(500f, 750f, 1f))
    assertEquals(300f, centerX, 0.01f)
    assertEquals(400f, centerY, 0.01f)
    assertEquals(0f, geometry.rotationDegrees % 90f, 0.001f)
  }

  @Test
  fun halfTurnMirrorsPointsThroughThePageCenter() {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        rotationDegrees = 180f,
      ),
    )

    // Document (0,0) (px -100,-200) mirrors to px (700, 1000).
    val (x, y) = geometry.toViewPoint(PenPoint(0f, 0f, 1f))
    assertEquals(700f, x, 0.01f)
    assertEquals(1000f, y, 0.01f)
    assertEquals(PenPoint(0f, 0f, 1f), geometry.toDocumentPoint(x, y, 1f))
  }

  @Test
  fun arbitraryAnglesRoundTripAndNegativeAnglesNormalize() {
    listOf(37f, 200f, -90f, 450f).forEach { raw ->
      val geometry = PenCanvasGeometry(
        widthPx = 600f,
        heightPx = 800f,
        pageTransform = PenCanvasPageTransform(
          pageWidthPx = 400f,
          pageHeightPx = 600f,
          scale = 2f,
          translationX = 30f,
          translationY = -40f,
          rotationDegrees = raw,
        ),
      )
      assertEquals(((raw % 360f) + 360f) % 360f, geometry.rotationDegrees, 0.001f)

      val point = PenPoint(500f, 750f, 0.6f)
      val (viewX, viewY) = geometry.toViewPoint(point)
      assertEquals(point, geometry.toDocumentPoint(viewX, viewY, 0.6f))
    }
  }

  @Test
  fun rotationKeepsDocumentSpaceClamping() {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        rotationDegrees = 90f,
        allowOutsidePage = false,
      ),
    )

    // Far-off canvas taps still clamp to the document page box (0..1000 x
    // 0..1500), not to the rotated pixel box.
    val topRight = geometry.toDocumentPoint(5000f, 5000f, 1f)!!
    assertEquals(1000f, topRight.x, 0.01f)
    assertEquals(0f, topRight.y, 0.01f)
    val bottomRight = geometry.toDocumentPoint(-5000f, 2000f, 1f)!!
    assertEquals(1000f, bottomRight.x, 0.01f)
    assertEquals(1500f, bottomRight.y, 0.01f)
  }

  @Test
  fun nonFiniteRotationInvalidatesTheGeometry() {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 800f,
      pageTransform = PenCanvasPageTransform(
        pageWidthPx = 400f,
        pageHeightPx = 600f,
        scale = 2f,
        rotationDegrees = Float.NaN,
      ),
    )

    org.junit.Assert.assertFalse(geometry.isValid)
    org.junit.Assert.assertNull(geometry.toDocumentPoint(0f, 0f, 1f))
  }

  private fun stroke(points: List<PenPoint>) = PenStroke(
    type = "pen",
    color = "#123456",
    width = 2.0,
    points = points,
  )
}
