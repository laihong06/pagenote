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

  private fun stroke(points: List<PenPoint>) = PenStroke(
    type = "pen",
    color = "#123456",
    width = 2.0,
    points = points,
  )
}
