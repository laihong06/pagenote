package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenStrokeCacheInstrumentedTest {
  private val size = 500

  @Test fun markedPolylineDrawsSharpCorner() {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val stroke = PenStroke("pen", "#000000", 8.0,
      listOf(PenPoint(100f, 100f, 1f), PenPoint(200f, 100f, 1f), PenPoint(200f, 200f, 1f)),
      extraFields = mapOf(PenStroke.POLYLINE_FIELD to true))
    PenStrokePainter.drawStroke(Canvas(bitmap), stroke, PenCanvasGeometry(500f, 500f))
    assertTrue(android.graphics.Color.alpha(bitmap.getPixel(200, 100)) > 0)
    assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(180, 120)))
    bitmap.recycle()
  }

  @Test
  fun pinchReusesInkRasterAndFinalFrameReturnsToExactRendering() {
    val list = strokes()
    val cache = PenStrokeCache().apply { setStrokes(list) }
    val first = PenCanvasGeometry(500f, 500f, PenCanvasPageTransform(400f, 400f, scale = 1.5f))
    val second = PenCanvasGeometry(500f, 500f, PenCanvasPageTransform(400f, 400f, scale = 2f, translationX = 30f))
    val animated = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    cache.drawInto(Canvas(animated), first, size, size, transformInProgress = true)
    val bitmapField = PenStrokeCache::class.java.getDeclaredField("bitmap").apply { isAccessible = true }
    val firstRaster = bitmapField.get(cache)
    animated.eraseColor(android.graphics.Color.TRANSPARENT)
    cache.drawInto(Canvas(animated), second, size, size, transformInProgress = true)
    assertSame("Pinch frames should reuse the ink bitmap", firstRaster, bitmapField.get(cache))
    val point = PenPoint(500f, 500f, 1f)
    val x = second.toViewX(point).toInt()
    val y = second.toViewY(point).toInt()
    assertTrue("Ink must remain visible at the transformed location", (y - 3..y + 3).any { py ->
      (x - 3..x + 3).any { px -> android.graphics.Color.alpha(animated.getPixel(px, py)) > 0 }
    })
    val finalFrame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    cache.drawInto(Canvas(finalFrame), second, size, size, transformInProgress = false)
    assertSamePixels(renderDirect(list, second), finalFrame)
    cache.clear()
    animated.recycle()
    finalFrame.recycle()
  }

  @Test
  fun pinchWithInkOutsideFitBoundsUsesExactRendering() {
    val list = listOf(PenStroke("pen", "#000000", 8.0, listOf(PenPoint(-30f, 200f, 1f), PenPoint(50f, 200f, 1f))))
    val geometry = PenCanvasGeometry(500f, 500f, PenCanvasPageTransform(500f, 500f, scale = 2f, translationX = 250f))
    val cache = PenStrokeCache().apply { setStrokes(list) }
    val actual = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    cache.drawInto(Canvas(actual), geometry, size, size, transformInProgress = true)
    assertSamePixels(renderDirect(list, geometry), actual)
    cache.clear()
    actual.recycle()
  }

  private fun strokes(): List<PenStroke> = listOf(
    PenStroke(
      "pen", "#dc3545", 8.0,
      listOf(PenPoint(100f, 100f, 1f), PenPoint(400f, 300f, 0.5f), PenPoint(700f, 100f, 1f)),
    ),
    PenStroke(
      "highlight", "#ffff00", 40.0,
      listOf(PenPoint(100f, 500f, 1f), PenPoint(700f, 500f, 1f), PenPoint(100f, 500f, 1f)),
    ),
    PenStroke("line", "#000000", 4.0, listOf(PenPoint(100f, 700f, 1f), PenPoint(700f, 900f, 1f))),
    PenStroke("arrow", "#000000", 4.0, listOf(PenPoint(700f, 700f, 1f), PenPoint(100f, 900f, 1f))),
    PenStroke("rect", "#000000", 4.0, listOf(PenPoint(800f, 100f, 1f), PenPoint(950f, 300f, 1f))),
    PenStroke(
      "ellipse", "#000000", 4.0, listOf(PenPoint(820f, 500f, 1f), PenPoint(950f, 700f, 1f)),
    ),
  )

  private fun renderDirect(strokes: List<PenStroke>, geometry: PenCanvasGeometry): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
      val canvas = Canvas(bitmap)
      val save = canvas.save()
      canvas.translate(geometry.pageOriginX, geometry.pageOriginY)
      canvas.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
      strokes.forEach { PenStrokePainter.drawStroke(canvas, it, geometry) }
      canvas.restoreToCount(save)
    }

  private fun renderCached(
    strokes: List<PenStroke>,
    geometry: PenCanvasGeometry,
    configure: (PenStrokeCache) -> Unit = { it.setStrokes(strokes) },
  ): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
    val cache = PenStrokeCache()
    configure(cache)
    cache.drawInto(Canvas(bitmap), geometry, size, size)
  }

  private fun assertSamePixels(expected: Bitmap, actual: Bitmap) {
    val expectedPixels = IntArray(size * size)
    val actualPixels = IntArray(size * size)
    expected.getPixels(expectedPixels, 0, size, 0, 0, size, size)
    actual.getPixels(actualPixels, 0, size, 0, 0, size, size)
    assertArrayEquals(expectedPixels, actualPixels)
  }

  @Test
  fun hardwareZoomReusesRecordedInkWithoutRescanningSamples() {
    var reads = 0
    val raw = (0..500).map { i -> PenPoint(i * 2f, 400f + kotlin.math.sin(i / 20f) * 20f, 1f) }
    val points = object : AbstractList<PenPoint>() {
      override val size: Int get() = raw.size
      override fun get(index: Int): PenPoint { reads++; return raw[index] }
    }
    val cache = PenStrokeCache()
    cache.setStrokes(listOf(PenStroke("pen", "#112233", 4.0, points)))
    val target = RenderNode("zoom-test").apply { setPosition(0, 0, size, size) }
    try {
      cache.drawInto(target.beginRecording(size, size), PenCanvasGeometry(500f, 500f), size, size)
      target.endRecording()
      reads = 0
      repeat(20) { step ->
        val geometry = PenCanvasGeometry(500f, 500f, PenCanvasPageTransform(500f, 500f,
          scale = 1f + step * 0.1f, translationX = step * 2f))
        cache.drawInto(target.beginRecording(size, size), geometry, size, size)
        target.endRecording()
      }
      assertEquals("zoom frames should reuse recorded strokes", 0, reads)
    } finally {
      cache.clear()
      target.discardDisplayList()
    }
  }

  @Test
  fun cacheBlitMatchesDirectRenderingForEveryStrokeType() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    assertSamePixels(renderDirect(strokes(), geometry), renderCached(strokes(), geometry))
  }

  @Test
  fun appendStrokeMatchesTheEquivalentFullReplace() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val list = strokes()
    val appended = renderCached(list, geometry) { cache ->
      cache.setStrokes(list.dropLast(1))
      cache.appendStroke(list.last(), geometry)
    }
    assertSamePixels(renderDirect(list, geometry), appended)
  }

  @Test
  fun transformChangeRebuildsWithTheNewGeometry() {
    val identity = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val zoomed = PenCanvasGeometry(
      size.toFloat(), size.toFloat(),
      PenCanvasPageTransform(pageWidthPx = 400f, pageHeightPx = 400f, scale = 1f),
    )
    val list = strokes()
    // Prime the raster under the identity geometry, then draw under a
    // different page transform: the key mismatch must force a full rebuild.
    val cached = renderCached(list, zoomed) { cache ->
      cache.setStrokes(list)
      cache.drawInto(
        Canvas(Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)),
        identity, size, size,
      )
    }
    assertSamePixels(renderDirect(list, zoomed), cached)
  }

  @Test
  fun removingAStrokeThroughSetStrokesRemovesItsInk() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val list = strokes()
    val cached = renderCached(list.dropLast(1), geometry) { cache ->
      cache.setStrokes(list)
      cache.setStrokes(list.dropLast(1))
    }
    assertSamePixels(renderDirect(list.dropLast(1), geometry), cached)
  }

  @Test
  fun strokesFullyOutsideTheViewportAreSkippedWithoutChangingPixels() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val inside = strokes()
    val withOutside = inside + PenStroke(
      "pen", "#000000", 8.0,
      listOf(PenPoint(-500f, -500f, 1f), PenPoint(-400f, -400f, 1f)),
    )
    assertSamePixels(
      renderDirect(inside, geometry),
      renderCached(withOutside, geometry),
    )
  }
}
