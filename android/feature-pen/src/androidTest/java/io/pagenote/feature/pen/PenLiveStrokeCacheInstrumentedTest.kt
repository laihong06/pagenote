package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.media.ImageReader
import android.os.SystemClock
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenLiveStrokeCacheInstrumentedTest {
  @Test fun liveDrawingMatchesStoredInkAtBoundariesAndAfterReset() {
    val points = (0..100).map { i -> PenPoint(10f + i * 2f, 50f + (i % 8) * 5f, (i % 6) / 5f) }
    val cache = PenLiveStrokeCache()
    for (length in listOf(1, 2, 3, 33, 34, 35, 66, 67, 101)) {
      val actual = Bitmap.createBitmap(350, 250, Bitmap.Config.ARGB_8888)
      val expected = Bitmap.createBitmap(350, 250, Bitmap.Config.ARGB_8888)
      val actualCanvas = Canvas(actual); val expectedCanvas = Canvas(expected)
      actualCanvas.translate(20f, 10f); actualCanvas.scale(1.2f, 1.2f)
      expectedCanvas.translate(20f, 10f); expectedCanvas.scale(1.2f, 1.2f)
      cache.draw(actualCanvas, points.take(length), "#dc3545", 7.5)
      PenStrokePainter.drawStroke(expectedCanvas, PenStroke("pen", "#dc3545", 7.5, points.take(length)),
        PenCanvasGeometry(350f, 250f, PenCanvasPageTransform(350f, 250f)))
      val a = IntArray(350 * 250); val b = IntArray(a.size)
      actual.getPixels(a, 0, 350, 0, 0, 350, 250); expected.getPixels(b, 0, 350, 0, 0, 350, 250)
      assertArrayEquals("Mismatch at $length points", b, a)
      actual.recycle(); expected.recycle()
    }
    cache.clear()
    assertEquals(0, cache.recordedChunkCount)
  }

  @Test fun hardwareRecordedStrokeMatchesNormalHardwareDrawing() {
    val points = (0..130).map { i -> PenPoint(20f + i * 2f, 100f + (i % 9) * 3f, 0.3f + (i % 5) * 0.14f) }
    val reader = ImageReader.newInstance(700, 250, PixelFormat.RGBA_8888, 2)
    val renderer = HardwareRenderer()
    val root = RenderNode("live-ink-test").apply { setPosition(0, 0, 700, 250) }
    try {
      val canvas = root.beginRecording()
      canvas.drawColor(Color.WHITE)
      PenLiveStrokeCache().draw(canvas, points, "#185abd", 8.0)
      canvas.save(); canvas.translate(350f, 0f)
      PenStrokePainter.drawStroke(canvas, PenStroke("pen", "#185abd", 8.0, points),
        PenCanvasGeometry(350f, 250f, PenCanvasPageTransform(350f, 250f)))
      canvas.restore()
      root.endRecording()
      renderer.setSurface(reader.surface); renderer.setContentRoot(root)
      renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
      var result: android.media.Image? = null
      val deadline = SystemClock.uptimeMillis() + 5000
      while (result == null && SystemClock.uptimeMillis() < deadline) {
        result = reader.acquireLatestImage()
        if (result == null) SystemClock.sleep(20)
      }
      val image = requireNotNull(result) { "Hardware renderer did not produce a frame" }
      try {
        val plane = image.planes[0]; val buffer = plane.buffer
        var different = 0; var ink = 0
        for (y in 0 until 250) for (x in 0 until 350) {
          val left = y * plane.rowStride + x * plane.pixelStride
          val right = y * plane.rowStride + (x + 350) * plane.pixelStride
          if ((0..3).any { buffer.get(left + it) != buffer.get(right + it) }) different++
          if ((0..2).any { (buffer.get(left + it).toInt() and 255) < 245 }) ink++
        }
        assertTrue("Hardware frame must contain ink", ink > 100)
        assertEquals("Hardware cached and direct strokes must match", 0, different)
      } finally { image.close() }
    } finally { renderer.destroy(); reader.close() }
  }

  @Test fun longStrokeReusesStableSegmentsAndMatchesExistingRenderer() {
    val cache = PenLiveStrokeCache()
    val points = (0..130).map { i -> PenPoint(20f + i * 2f, 120f + (i % 7) * 3f, 0.3f + (i % 5) * 0.14f) }
    fun render(n: Int): Bitmap {
      val image = Bitmap.createBitmap(350, 250, Bitmap.Config.ARGB_8888)
      cache.draw(Canvas(image), points.take(n), "#185abd", 8.0)
      return image
    }
    val initial = render(100)
    val first = cache.recordedChunkCount
    assertTrue("A long stroke should record stable segments", first > 0)
    val updated = render(101)
    assertEquals("Appending one point must reuse recorded chunks", first, cache.recordedChunkCount)
    val expected = Bitmap.createBitmap(350, 250, Bitmap.Config.ARGB_8888)
    PenStrokePainter.drawStroke(Canvas(expected), PenStroke("pen", "#185abd", 8.0, points.take(101)),
      PenCanvasGeometry(350f, 250f, PenCanvasPageTransform(350f, 250f)))
    val a = IntArray(350 * 250); val b = IntArray(a.size)
    updated.getPixels(a, 0, 350, 0, 0, 350, 250)
    expected.getPixels(b, 0, 350, 0, 0, 350, 250)
    assertArrayEquals("Cached live ink must match normal rendering", b, a)
    initial.recycle(); updated.recycle(); expected.recycle()
  }
}
