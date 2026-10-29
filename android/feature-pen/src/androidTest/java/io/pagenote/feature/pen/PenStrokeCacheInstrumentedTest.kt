package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenStrokeCacheInstrumentedTest {
  private val size = 500

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
