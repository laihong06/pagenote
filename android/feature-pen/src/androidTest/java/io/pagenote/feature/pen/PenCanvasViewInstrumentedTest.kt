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
  fun longStrokeDrawBenchmarkPreservesVisibleInk() {
    var cpuNanos = 0L
    var visible = false
    onUi {
      val bitmap = android.graphics.Bitmap.createBitmap(500, 500, android.graphics.Bitmap.Config.ARGB_8888)
      val target = android.graphics.Canvas(bitmap)
      val points = (0..2000).map { i -> PenPoint(i / 5f, 200f + kotlin.math.sin(i / 50f) * 50f, 0.8f) }
      val stroke = PenStroke("pen", "#112233", 4.0, points)
      val geometry = PenCanvasGeometry(500f, 500f)
      val start = android.os.Debug.threadCpuTimeNanos()
      repeat(20) { PenStrokePainter.drawStroke(target, stroke, geometry) }
      cpuNanos = android.os.Debug.threadCpuTimeNanos() - start
      visible = (0 until 500).any { x -> (100 until 300).any { y -> bitmap.getPixel(x, y) != 0 } }
      bitmap.recycle()
    }
    android.util.Log.i("PagenoteInkBenchmark", "20_long_draws_cpu_ms=${cpuNanos / 1_000_000.0}")
    assertTrue("ink must be visible", visible)
  }

  @Test
  fun longStrokeInputBenchmarkKeepsEverySample() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    var cpuNanos = 0L
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 30f, 80f, 0.5f, 2)
      val start = android.os.Debug.threadCpuTimeNanos()
      repeat(3000) { i ->
        val x = 30f + (i % 440)
        val y = 80f + (i / 440) * 8f
        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, x, y, 0.5f, (3 + i).toLong())
      }
      cpuNanos = android.os.Debug.threadCpuTimeNanos() - start
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 390f, 128f, 0.5f, 3004)
    }
    android.util.Log.i("PagenoteInkBenchmark", "3000_move_cpu_ms=${cpuNanos / 1_000_000.0}")
    assertTrue("long stroke should preserve input samples", added.single().points.size > 2500)
  }

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
  fun zoomedAndPannedStylusInputMapsBackToDocumentCoordinates() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    val transform = PenCanvasPageTransform(
      pageWidthPx = 300f,
      pageHeightPx = 400f,
      scale = 2f,
      translationX = 30f,
      translationY = -20f,
    )
    onUi {
      canvas = createCanvas().apply {
        pageTransform = transform
        onStrokeAdded = added::add
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 280f, 130f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 340f, 190f, 0.75f, 3)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 340f, 190f, 0.75f, 4)
    }

    assertEquals(1, added.size)
    val points = added.single().points
    assertPoint(points[0], 500f, 500f, 0.5f)
    assertPoint(points[1], 600f, 600f, 0.75f)
    val geometry = PenCanvasGeometry(CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat(), transform)
    assertEquals(280f, geometry.toViewX(points[0]), 0.01f)
    assertEquals(130f, geometry.toViewY(points[0]), 0.01f)
    assertEquals(340f, geometry.toViewX(points[1]), 0.01f)
    assertEquals(190f, geometry.toViewY(points[1]), 0.01f)
  }

  @Test
  fun extendedCanvasSettingControlsWhetherVisibleMarginInkIsClamped() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply {
        pageTransform = PenCanvasPageTransform(
          pageWidthPx = 300f,
          pageHeightPx = 400f,
          translationX = -100f,
        )
        onStrokeAdded = added::add
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 450f, 250f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 450f, 250f, 0.5f, 3)
      canvas.pageTransform = requireNotNull(canvas.pageTransform).copy(allowOutsidePage = true)
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 450f, 250f, 0.5f, 4)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 450f, 250f, 0.5f, 5)
    }

    assertEquals(2, added.size)
    assertEquals(1000f, added[0].points.single().x, 0.01f)
    assertEquals(1500f, added[1].points.single().x, 0.01f)
    val geometry = PenCanvasGeometry(
      CANVAS_SIZE.toFloat(),
      CANVAS_SIZE.toFloat(),
      requireNotNull(canvas.pageTransform),
    )
    assertEquals(450f, geometry.toViewX(added[1].points.single()), 0.01f)
  }

  @Test
  fun endpointDwellCommitsOneRecognizedShapeInsteadOfFreehandInk() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ ->
          recognized += shape
          true
        }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.6f, 4) }

    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
    assertTrue("recognized shape must not also be persisted as freehand", added.isEmpty())
    assertTrue(canvas.strokes.isEmpty())
  }

  @Test
  fun fingerDrawnLineAlsoRecognizesOnDwell() {
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    fun touch(action: Int, x: Float, time: Long) {
      withEvent(action, x, 80f, 0.5f, 0, time, toolType = MotionEvent.TOOL_TYPE_FINGER) {
        canvas.dispatchTouchEvent(it)
      }
    }
    onUi {
      canvas = createCanvas().apply { onShapeRecognized = { shape, _ -> recognized += shape; true } }
      touch(MotionEvent.ACTION_DOWN, 40f, 2)
      touch(MotionEvent.ACTION_MOVE, 240f, 3)
    }
    Thread.sleep(650)
    onUi { touch(MotionEvent.ACTION_UP, 240f, 4) }
    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
  }

  @Test
  fun ballpointDwellRecognizesLine() {
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    onUi {
      canvas = createCanvas().apply {
        selectedTool = PenCanvasTool.BALLPOINT
        onShapeRecognized = { shape, _ -> recognized += shape; true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.6f, 4) }
    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
  }

  @Test
  fun dwellRecognizesVisibleLineWhenPageIsZoomed() {
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    onUi {
      canvas = createCanvas().apply {
        pageTransform = PenCanvasPageTransform(pageWidthPx = 500f, pageHeightPx = 500f, scale = 4f)
        onShapeRecognized = { shape, _ -> recognized += shape; true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 100f, 250f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 190f, 250f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 190f, 250f, 0.6f, 4) }
    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
  }

  @Test
  fun movingAfterPreviewCanBeRecognizedAgainAtTheNewEndpoint() {
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    onUi {
      canvas = createCanvas().apply { onShapeRecognized = { shape, _ -> recognized += shape; true } }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 320f, 80f, 0.7f, 4) }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 320f, 80f, 0.7f, 5) }
    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
    assertEquals(640f, recognized.single().second.x, 0.01f)
  }

  @Test
  fun liftingBeforeDwellKeepsTheOriginalFreehandStroke() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ ->
          recognized += shape
          true
        }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.6f, 4)
    }

    assertTrue(recognized.isEmpty())
    assertEquals(1, added.size)
    assertEquals("pen", added.single().type)
  }

  @Test
  fun highlighterGestureNeverTriggersShapeRecognition() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        selectedTool = PenCanvasTool.HIGHLIGHTER
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ ->
          recognized += shape
          true
        }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.7f, 4) }

    assertTrue(recognized.isEmpty())
    assertEquals("highlight", added.single().type)
  }

  @Test
  fun movementAfterDwellPreviewCancelsRecognitionAndKeepsFreehand() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ ->
          recognized += shape
          true
        }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi {
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 180f, 0.7f, 4)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 180f, 0.7f, 5)
    }

    assertTrue(recognized.isEmpty())
    assertEquals(1, added.size)
    assertEquals("pen", added.single().type)
    assertEquals(3, added.single().points.size)
  }

  @Test
  fun cancelAndDisabledInputDoNotPersistStrokes() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ ->
          recognized += shape
          true
        }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 100f, 100f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 340f, 100f, 0.7f, 3)
      dispatchTouch(canvas, MotionEvent.ACTION_CANCEL, 340f, 100f, 0.7f, 4)
      canvas.inputEnabled = false
    }
    Thread.sleep(650)
    var disabledDownConsumed = false
    onUi {
      disabledDownConsumed = dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 200f, 200f, 0.6f, 5)
    }

    assertFalse(disabledDownConsumed)
    assertTrue(added.isEmpty())
    assertTrue(recognized.isEmpty())
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
    toolType: Int = MotionEvent.TOOL_TYPE_STYLUS,
    dispatch: (MotionEvent) -> Boolean,
  ): Boolean {
    val properties = MotionEvent.PointerProperties().apply {
      id = POINTER_ID
      this.toolType = toolType
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
