package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlighterRenderingInstrumentedTest {
  @Test
  fun retracingOneHighlightNeverExceedsOneTranslucentCoat() = onUi {
    val view = createCanvas().apply {
      strokes = listOf(highlight(listOf(
        PenPoint(100f, 400f, 1f), PenPoint(700f, 400f, 1f),
        PenPoint(100f, 400f, 1f), PenPoint(700f, 400f, 1f),
      )))
    }
    withBitmap(view) { bitmap ->
      assertEquals(89, Color.alpha(bitmap.getPixel(200, 200)))
      assertSingleCoat(bitmap)
    }
  }

  @Test
  fun pressureVaryingSelfIntersectionKeepsTheSameOpacity() = onUi {
    val view = createCanvas().apply {
      strokes = listOf(highlight(listOf(
        PenPoint(100f, 100f, 1f), PenPoint(700f, 700f, 0.5f),
        PenPoint(100f, 700f, 1f), PenPoint(700f, 100f, 0.5f),
      )))
    }
    withBitmap(view) { bitmap ->
      assertEquals(89, Color.alpha(bitmap.getPixel(200, 200)))
      assertSingleCoat(bitmap)
    }
  }

  @Test
  fun liveRetracingAndReloadedStrokeUseTheSameTranslucentCoat() = onUi {
    val view = createCanvas().apply {
      selectedTool = PenCanvasTool.HIGHLIGHTER
      penColor = "#ffff00"
      penWidth = 40.0
    }
    touch(view, MotionEvent.ACTION_DOWN, 50f, 200f, 2L)
    touch(view, MotionEvent.ACTION_MOVE, 350f, 200f, 3L)
    touch(view, MotionEvent.ACTION_MOVE, 50f, 200f, 4L)
    touch(view, MotionEvent.ACTION_MOVE, 350f, 200f, 5L)
    withBitmap(view) { bitmap ->
      assertEquals(89, Color.alpha(bitmap.getPixel(200, 200)))
      assertSingleCoat(bitmap)
    }
    touch(view, MotionEvent.ACTION_UP, 350f, 200f, 6L)
    assertEquals(1, view.strokes.size)
    val reloaded = createCanvas().apply { strokes = view.strokes }
    withBitmap(reloaded) { bitmap ->
      assertEquals(89, Color.alpha(bitmap.getPixel(200, 200)))
      assertSingleCoat(bitmap)
    }
  }

  @Test
  fun separateHighlightsCanOverlapWithoutMakingOrdinaryInkTranslucent() = onUi {
    val stroke = highlight(listOf(PenPoint(100f, 400f, 1f), PenPoint(700f, 400f, 1f)))
    val view = createCanvas().apply {
      strokes = listOf(stroke, stroke, stroke.copy(
        type = "pen", color = "#000000",
        points = listOf(PenPoint(100f, 700f, 1f), PenPoint(700f, 700f, 1f)),
      ))
    }
    withBitmap(view) { bitmap ->
      assertTrue(Color.alpha(bitmap.getPixel(200, 200)) in 146..148)
      assertEquals(Color.BLACK, bitmap.getPixel(200, 350))
    }
  }

  @Test
  fun singleDotAndZoomedClippedHighlightRemainTranslucent() = onUi {
    val view = createCanvas().apply {
      strokes = listOf(highlight(listOf(PenPoint(400f, 400f, 1f))))
    }
    withBitmap(view) { bitmap ->
      assertEquals(89, Color.alpha(bitmap.getPixel(200, 200)))
      assertEquals(0, Color.alpha(bitmap.getPixel(220, 200)))
    }
    view.pageTransform = PenCanvasPageTransform(500f, 500f, scale = 2f)
    view.strokes = listOf(highlight(listOf(
      PenPoint(-1_000_000f, 400f, 1f), PenPoint(1_000_000f, 400f, 1f),
      PenPoint(-1_000_000f, 400f, 1f),
    )))
    withBitmap(view) { bitmap ->
      assertEquals(89, Color.alpha(bitmap.getPixel(0, 150)))
      assertEquals(89, Color.alpha(bitmap.getPixel(499, 150)))
      assertSingleCoat(bitmap)
    }
  }

  private fun highlight(points: List<PenPoint>) = PenStroke("highlight", "#ffff00", 40.0, points)

  private fun createCanvas() = PenCanvasView(
    InstrumentationRegistry.getInstrumentation().targetContext,
  ).apply {
    val spec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
    measure(spec, spec)
    layout(0, 0, 500, 500)
  }

  private fun withBitmap(view: PenCanvasView, check: (Bitmap) -> Unit) {
    val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
    try {
      view.draw(Canvas(bitmap))
      check(bitmap)
    } finally {
      bitmap.recycle()
    }
  }

  private fun assertSingleCoat(bitmap: Bitmap) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val maxAlpha = pixels.maxOf(Color::alpha)
    assertTrue("one stroke must not accumulate opacity, maximum alpha was $maxAlpha", maxAlpha <= 89)
  }

  private fun touch(view: PenCanvasView, action: Int, x: Float, y: Float, time: Long) {
    val pointer = MotionEvent.PointerProperties().apply {
      id = 0
      toolType = MotionEvent.TOOL_TYPE_STYLUS
    }
    val coordinates = MotionEvent.PointerCoords().apply {
      this.x = x
      this.y = y
      pressure = 1f
      size = 0.1f
    }
    val event = MotionEvent.obtain(
      1L, time, action, 1, arrayOf(pointer), arrayOf(coordinates),
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
    )
    try {
      view.dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }

  private fun onUi(action: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
  }
}
