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
class PenPredictionInstrumentedTest {
  private val size = 500

  @Test
  fun liveTailExtendsBeyondTheLastRealSample() = onUi {
    val view = createCanvas().apply {
      selectedTool = PenCanvasTool.PEN
      penColor = "#000000"
      penWidth = 20.0
    }
    touch(view, MotionEvent.ACTION_DOWN, 60f, 200f, 2L)
    repeat(9) { index ->
      touch(view, MotionEvent.ACTION_MOVE, 60f + (index + 1) * 30f, 200f, 3L + index)
    }
    val lastRealViewX = 60f + 9 * 30f
    val halfStrokePx = 20f / 2f * 0.5f
    withBitmap(view) { bitmap ->
      val row = IntArray(size)
      bitmap.getPixels(row, 0, size, 0, 200, size, 1)
      val rightmost = row.indices.filter { Color.alpha(row[it]) > 0 }.max()
      assertTrue(
        "ink should extend beyond the round cap at ${lastRealViewX + halfStrokePx} but reached $rightmost",
        rightmost > lastRealViewX + halfStrokePx,
      )
    }
    touch(view, MotionEvent.ACTION_UP, lastRealViewX, 200f, 20L)
    val stroke = view.strokes.single()
    assertEquals(10, stroke.points.size)
    assertEquals(660f, stroke.points.last().x, 0.001f)
  }

  @Test
  fun shortGesturesDoNotDrawAPredictedTail() = onUi {
    val view = createCanvas().apply {
      selectedTool = PenCanvasTool.PEN
      penColor = "#000000"
      penWidth = 20.0
    }
    touch(view, MotionEvent.ACTION_DOWN, 60f, 200f, 2L)
    repeat(3) { index ->
      touch(view, MotionEvent.ACTION_MOVE, 60f + (index + 1) * 30f, 200f, 3L + index)
    }
    val lastRealViewX = 60f + 3 * 30f
    val halfStrokePx = 20f / 2f * 0.5f
    withBitmap(view) { bitmap ->
      val row = IntArray(size)
      bitmap.getPixels(row, 0, size, 0, 200, size, 1)
      val rightmost = row.indices.filter { Color.alpha(row[it]) > 0 }.max()
      assertTrue(
        "no predicted tail expected, ink reached $rightmost past $lastRealViewX",
        rightmost <= lastRealViewX + halfStrokePx + 1,
      )
    }
    touch(view, MotionEvent.ACTION_UP, lastRealViewX, 200f, 20L)
  }

  private fun createCanvas() = PenCanvasView(
    InstrumentationRegistry.getInstrumentation().targetContext,
  ).apply {
    val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    measure(spec, spec)
    layout(0, 0, size, size)
  }

  private fun withBitmap(view: PenCanvasView, check: (Bitmap) -> Unit) {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    try {
      view.draw(Canvas(bitmap))
      check(bitmap)
    } finally {
      bitmap.recycle()
    }
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
