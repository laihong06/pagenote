package io.pagenote.feature.pen

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenCanvasViewInstrumentedTest {
  @Test
  fun stylusGesturePersistsNormalizedCoordinatesAndPressure() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 100f, 120f, 0.25f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 200f, 240f, 0.75f, 3)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 300f, 320f, 0.5f, 4)
    }

    assertEquals(1, added.size)
    val stroke = added.single()
    assertEquals("pen", stroke.type)
    assertEquals(3, stroke.points.size)
    assertPoint(stroke.points[0], 200f, 240f, 0.25f)
    assertPoint(stroke.points[1], 400f, 480f, 0.75f)
    assertPoint(stroke.points[2], 600f, 640f, 0.5f)
  }

  @Test
  fun cancelAndDisabledInputDoNotPersistStrokes() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 100f, 100f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 140f, 140f, 0.7f, 3)
      dispatchTouch(canvas, MotionEvent.ACTION_CANCEL, 140f, 140f, 0.7f, 4)
      canvas.inputEnabled = false
    }
    var disabledDownConsumed = false
    onUi {
      disabledDownConsumed = dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 200f, 200f, 0.6f, 5)
    }

    assertFalse(disabledDownConsumed)
    assertTrue(added.isEmpty())
    assertTrue(canvas.strokes.isEmpty())
  }

  @Test
  fun stylusSideButtonGenericMotionErasesTopmostStrokeForBothButtonMappings() {
    val buttonMappings = listOf(
      MotionEvent.BUTTON_SECONDARY,
      MotionEvent.BUTTON_STYLUS_SECONDARY,
    )
    buttonMappings.forEach { button ->
      lateinit var canvas: PenCanvasView
      val added = mutableListOf<PenStroke>()
      val erased = mutableListOf<Int>()
      var pressConsumed = false
      var releaseConsumed = false
      onUi {
        val existing = PenStroke(
          type = "pen",
          color = "#112233",
          width = 10.0,
          points = listOf(PenPoint(100f, 200f, 1f), PenPoint(200f, 200f, 1f)),
        )
        canvas = createCanvas().apply {
          strokes = listOf(existing, existing)
          onStrokeAdded = added::add
          onStrokeErased = erased::add
        }
        dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 15f, 15f, 0.4f, 2)
        pressConsumed = dispatchGeneric(
          canvas,
          MotionEvent.ACTION_BUTTON_PRESS,
          75f,
          100f,
          0.6f,
          button,
          3,
        )
        releaseConsumed = dispatchGeneric(
          canvas,
          MotionEvent.ACTION_BUTTON_RELEASE,
          75f,
          100f,
          0.6f,
          0,
          4,
        )
        dispatchTouch(canvas, MotionEvent.ACTION_CANCEL, 75f, 100f, 0.6f, 5)
      }

      assertTrue("button $button press should use generic-motion dispatch", pressConsumed)
      assertTrue("button $button release should use generic-motion dispatch", releaseConsumed)
      assertEquals("button $button removes only the topmost hit", listOf(1), erased)
      assertEquals(1, canvas.strokes.size)
      assertTrue("eraser gesture must not create an annotation", added.isEmpty())
    }
  }

  private fun createCanvas(): PenCanvasView {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return PenCanvasView(context).apply {
      measure(
        View.MeasureSpec.makeMeasureSpec(CANVAS_SIZE, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(CANVAS_SIZE, View.MeasureSpec.EXACTLY),
      )
      layout(0, 0, CANVAS_SIZE, CANVAS_SIZE)
    }
  }

  private fun dispatchTouch(
    canvas: PenCanvasView,
    action: Int,
    x: Float,
    y: Float,
    pressure: Float,
    eventTime: Long,
  ): Boolean = withEvent(action, x, y, pressure, 0, eventTime) { canvas.dispatchTouchEvent(it) }

  private fun dispatchGeneric(
    canvas: PenCanvasView,
    action: Int,
    x: Float,
    y: Float,
    pressure: Float,
    buttonState: Int,
    eventTime: Long,
  ): Boolean = withEvent(action, x, y, pressure, buttonState, eventTime) {
    canvas.dispatchGenericMotionEvent(it)
  }

  private fun withEvent(
    action: Int,
    x: Float,
    y: Float,
    pressure: Float,
    buttonState: Int,
    eventTime: Long,
    dispatch: (MotionEvent) -> Boolean,
  ): Boolean {
    val properties = MotionEvent.PointerProperties().apply {
      id = POINTER_ID
      toolType = MotionEvent.TOOL_TYPE_STYLUS
    }
    val coordinates = MotionEvent.PointerCoords().apply {
      this.x = x
      this.y = y
      this.pressure = pressure
      size = 0.1f
    }
    val event = MotionEvent.obtain(
      DOWN_TIME,
      eventTime,
      action,
      1,
      arrayOf(properties),
      arrayOf(coordinates),
      0,
      buttonState,
      1f,
      1f,
      0,
      0,
      InputDevice.SOURCE_STYLUS,
      0,
    )
    return try {
      dispatch(event)
    } finally {
      event.recycle()
    }
  }

  private fun assertPoint(point: PenPoint, x: Float, y: Float, pressure: Float) {
    assertEquals(x, point.x, 0.01f)
    assertEquals(y, point.y, 0.01f)
    assertEquals(pressure, point.pressure, 0.01f)
  }

  private fun onUi(action: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
  }

  private companion object {
    const val CANVAS_SIZE = 500
    const val POINTER_ID = 0
    const val DOWN_TIME = 1L
  }
}
