package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test
import io.pagenote.core.reader.PdfTextPoint

class PdfViewportTransformTest {
  @Test
  fun searchableTextSelection_mapsThroughZoomPanToPdfPagePoints() {
    val points = mapPdfTextSelectionToPagePoints(
      start = PdfSelectionPoint(550f, 300f),
      end = PdfSelectionPoint(650f, 500f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform(2f, 50f, -100f),
      pageWidth = 300.0,
      pageHeight = 400.0,
    )

    requireNotNull(points)
    assertEquals(150.0, points.first.x, 0.0001)
    assertEquals(200.0, points.first.y, 0.0001)
    assertEquals(175.0, points.second.x, 0.0001)
    assertEquals(250.0, points.second.y, 0.0001)
  }

  @Test
  fun searchableTextSelection_rejectsStartInLetterbox() {
    val points = mapPdfTextSelectionToPagePoints(
      start = PdfSelectionPoint(100f, 300f),
      end = PdfSelectionPoint(500f, 400f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
      pageWidth = 300.0,
      pageHeight = 400.0,
    )

    assertNull(points)
  }

  @Test
  fun fitWidthLetterbox_mapsToFullPageCoordinates() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(300f, 200f),
      end = PdfSelectionPoint(700f, 600f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertEquals(NormalizedOcrRegion(166, 250, 834, 750), region)
  }

  @Test
  fun annotationTapUsesPageWidthScaleForBothAxes() {
    val point = mapPdfTapToAnnotationPoint(
      tap = PdfSelectionPoint(500f, 400f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    requireNotNull(point)
    assertEquals(500.0, point.first, 0.0001)
    assertEquals(2000.0 / 3.0, point.second, 0.0001)
  }

  @Test
  fun annotationDragUsesInverseZoomPanAndClipsPortraitPageEnd() {
    val points = mapPdfAnnotationDragToPoints(
      start = PdfSelectionPoint(300f, 1200f),
      end = PdfSelectionPoint(2000f, 2000f),
      bounds = PdfViewportBounds(1000f, 1400f, 700f, 1050f),
      transform = PdfViewportTransform(2f, 40f, -50f),
    )

    requireNotNull(points)
    assertEquals(328.5714, points.first.first, 0.001)
    assertEquals(1142.8571, points.first.second, 0.001)
    assertEquals(1000.0, points.second.first, 0.001)
    assertEquals(1500.0, points.second.second, 0.001)
  }

  @Test
  fun annotationDragStartingInLetterboxIsRejected() {
    val points = mapPdfAnnotationDragToPoints(
      start = PdfSelectionPoint(100f, 300f),
      end = PdfSelectionPoint(300f, 500f),
      bounds = PdfViewportBounds(1000f, 1400f, 700f, 1050f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertNull(points)
  }

  @Test
  fun zoomPan_mapsSelectionThroughInverseTransform() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(350f, 200f),
      end = PdfSelectionPoint(750f, 600f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform(2f, 50f, -100f),
    )

    assertEquals(NormalizedOcrRegion(333, 437, 667, 688), region)
  }

  @Test
  fun startInLetterbox_isRejected() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(100f, 200f),
      end = PdfSelectionPoint(700f, 600f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertNull(region)
  }

  @Test
  fun dragEndOutsidePage_isClipped() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(300f, 200f),
      end = PdfSelectionPoint(900f, 700f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertEquals(NormalizedOcrRegion(166, 250, 1000, 875), region)
  }

  @Test
  fun reverseDrag_isNormalized() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(700f, 600f),
      end = PdfSelectionPoint(300f, 200f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertEquals(NormalizedOcrRegion(166, 250, 834, 750), region)
  }

  @Test
  fun invalidBounds_areRejected() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(300f, 200f),
      end = PdfSelectionPoint(700f, 600f),
      bounds = PdfViewportBounds(1000f, 800f, 0f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertNull(region)
  }

  @Test
  fun tapSelection_isRejectedWhenQuantizedToZeroArea() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(500f, 400f),
      end = PdfSelectionPoint(500f, 400f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertNull(region)
  }

  @Test
  fun horizontalSelection_isRejectedWhenQuantizedToZeroHeight() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(500f, 400f),
      end = PdfSelectionPoint(700f, 400f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertNull(region)
  }

  @Test
  fun verticalSelection_isRejectedWhenQuantizedToZeroWidth() {
    val region = mapPdfSelectionToOcrRegion(
      start = PdfSelectionPoint(500f, 400f),
      end = PdfSelectionPoint(500f, 600f),
      bounds = PdfViewportBounds(1000f, 800f, 600f, 800f),
      transform = PdfViewportTransform.DEFAULT,
    )

    assertNull(region)
  }

  @Test
  fun zoomKeepsGestureCentroidAnchoredAndClampsToMaximumScale() {
    val zoomed = PdfViewportTransform.applyGesture(
      current = PdfViewportTransform.DEFAULT,
      viewportWidthPx = 400f,
      viewportHeightPx = 800f,
      pageWidthPx = 300f,
      pageHeightPx = 600f,
      centroidX = 300f,
      centroidY = 400f,
      zoomChange = 8f,
      panX = 0f,
      panY = 0f,
    )

    assertEquals(6f, zoomed.scale, 0.0001f)
    assertEquals(-500f, zoomed.translationX, 0.0001f)
    assertEquals(0f, zoomed.translationY, 0.0001f)
  }

  @Test
  fun panIsClampedToScaledPageEdges() {
    val panned = PdfViewportTransform.applyGesture(
      current = PdfViewportTransform(scale = 2f, translationX = 90f, translationY = -180f),
      viewportWidthPx = 400f,
      viewportHeightPx = 800f,
      pageWidthPx = 300f,
      pageHeightPx = 600f,
      centroidX = 200f,
      centroidY = 400f,
      zoomChange = 1f,
      panX = 40f,
      panY = -80f,
    )

    assertEquals(100f, panned.translationX, 0.0001f)
    assertEquals(-200f, panned.translationY, 0.0001f)
  }

  @Test
  fun zoomingBackToFitRemovesTranslationAndFitWidthUsesAvailableWidth() {
    val fitted = PdfViewportTransform.applyGesture(
      current = PdfViewportTransform(scale = 2f, translationX = 80f, translationY = -120f),
      viewportWidthPx = 400f,
      viewportHeightPx = 800f,
      pageWidthPx = 300f,
      pageHeightPx = 600f,
      centroidX = 200f,
      centroidY = 400f,
      zoomChange = 0.5f,
      panX = 0f,
      panY = 0f,
    )

    assertEquals(PdfViewportTransform.DEFAULT, fitted)
    assertEquals(4f / 3f, PdfViewportTransform.fitWidthScale(400f, 300f), 0.0001f)
    assertEquals(1f, PdfViewportTransform.fitWidthScale(300f, 400f), 0.0001f)
  }

  @Test
  fun invalidMeasurementsLeaveTransformUnchanged() {
    val current = PdfViewportTransform(scale = 2f, translationX = 12f, translationY = -8f)

    val unchanged = PdfViewportTransform.applyGesture(
      current = current,
      viewportWidthPx = 0f,
      viewportHeightPx = 800f,
      pageWidthPx = 300f,
      pageHeightPx = 600f,
      centroidX = 200f,
      centroidY = 400f,
      zoomChange = 2f,
      panX = 0f,
      panY = 0f,
    )

    assertSame(current, unchanged)
  }

  @Test
  fun twoPointerGestureCalculatesCentroidPanAndScale() {
    val gesture = PdfViewportTransform.calculateTwoPointerGesture(
      previous = listOf(
        PdfViewportPointer(id = 1L, x = 100f, y = 100f),
        PdfViewportPointer(id = 2L, x = 200f, y = 100f),
      ),
      current = listOf(
        PdfViewportPointer(id = 1L, x = 110f, y = 120f),
        PdfViewportPointer(id = 2L, x = 230f, y = 120f),
      ),
    )

    requireNotNull(gesture)
    assertEquals(170f, gesture.centroidX, 0.0001f)
    assertEquals(120f, gesture.centroidY, 0.0001f)
    assertEquals(1.2f, gesture.zoomChange, 0.0001f)
    assertEquals(20f, gesture.panX, 0.0001f)
    assertEquals(20f, gesture.panY, 0.0001f)
  }

  @Test
  fun onePointerGestureDoesNotProduceViewportTransform() {
    val gesture = PdfViewportTransform.calculateTwoPointerGesture(
      previous = listOf(PdfViewportPointer(id = 1L, x = 100f, y = 100f)),
      current = listOf(PdfViewportPointer(id = 1L, x = 140f, y = 160f)),
    )

    assertEquals(null, gesture)
  }

  @Test
  fun fitWidthCentersPageAtComputedScale() {
    val fitted = PdfViewportTransform.fitWidth(
      viewportWidthPx = 400f,
      pageWidthPx = 300f,
    )

    assertEquals(4f / 3f, fitted.scale, 0.0001f)
    assertEquals(0f, fitted.translationX, 0f)
    assertEquals(0f, fitted.translationY, 0f)
  }
}
