package io.pagenote.feature.pen

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PenCanvasViewInstrumentedTest {
  @Test fun referencePolylineCommitsAsOneStraightSegmentStroke() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 100f, .5f, 1)
      for (i in 1..20) dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 40f + i * 5f, 100f, .5f, (i + 1).toLong())
      for (i in 1..20) dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 140f, 100f + i * 5f, .5f, (i + 21).toLong())
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 140f, 200f, .5f, 42) }
    assertEquals(1, added.size)
    assertEquals("pen", added.single().type)
    assertEquals(true, added.single().extraFields["pagenotePolyline"])
    assertEquals(3, added.single().points.size)
  }
  @Test fun referenceCurveCommitsTheBezierAsOneMarkedStroke() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 50f, 300f, .5f, 1)
      for (step in 1..40) {
        val t = step / 40f
        val inverse = 1f - t
        val x = 50f * inverse * inverse + 250f * 2f * inverse * t + 450f * t * t
        val y = 300f * inverse * inverse + 50f * 2f * inverse * t + 300f * t * t
        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, x, y, .5f, (step + 1).toLong())
      }
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 450f, 300f, .5f, 42) }
    assertEquals(1, added.size)
    assertEquals("pen", added.single().type)
    assertEquals(true, added.single().extraFields[PenStroke.CURVE_FIELD])
    assertEquals(33, added.single().points.size)
    assertEquals(350f, added.single().points[16].y, 2f)
  }
  @Test fun referenceEllipseDwellCommitsOneMarkedClosedStroke() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 410f, 250f, .5f, 1)
      for (step in 1..64) {
        val angle = step / 64f * (2f * Math.PI.toFloat())
        dispatchTouch(
          canvas,
          MotionEvent.ACTION_MOVE,
          250f + kotlin.math.cos(angle) * 160f,
          250f + kotlin.math.sin(angle) * 75f,
          .5f,
          (step + 1).toLong(),
        )
      }
    }
    Thread.sleep(700)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 410f, 250f, .5f, 66) }

    assertEquals(1, added.size)
    assertEquals("pen", added.single().type)
    assertEquals(true, added.single().extraFields[PenStroke.POLYLINE_FIELD])
    assertEquals(added.single().points.first(), added.single().points.last())
    assertTrue(added.single().points.size >= 20)
  }
  @Test fun referenceLineUsesSampledScreenEndpoints() {
    lateinit var canvas: PenCanvasView
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        onShapeRecognized = { shape, _ -> recognized.add(shape); true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, .5f, 1)
      for (i in 1..5) dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 40f + i * 40f, 80f, .5f, (i + 1).toLong())
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, .5f, 7) }
    assertEquals(1, recognized.size)
    assertEquals("line", recognized.single().type)
    assertEquals(160f, recognized.single().first.x, .01f)
    assertEquals(480f, recognized.single().second.x, .01f)
  }
  @Test fun referenceMinimumLengthLineIsNotRejectedByDocumentSpaceGate() {
    lateinit var canvas: PenCanvasView
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        onShapeRecognized = { shape, _ -> recognized.add(shape); true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 100f, 120f, .5f, 1)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 104f, 120f, .5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 108f, 120f, .5f, 3)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 112f, 120f, .5f, 4)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 120f, 120f, .5f, 5)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 120f, 120f, .5f, 6) }

    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
  }
  @Test fun sampledFlatLineUsesTheReferenceBezierBranch() {
    lateinit var canvas: PenCanvasView
    val added = mutableListOf<PenStroke>()
    onUi {
      canvas = createCanvas().apply { onStrokeAdded = added::add }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, .5f, 1)
      for (i in 1..40) dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 40f + i * 5f, 80f, .5f, (i + 1).toLong())
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, .5f, 50) }
    assertEquals(1, added.size)
    assertEquals(true, added.single().extraFields[PenStroke.CURVE_FIELD])
    assertTrue(added.single().points.all { kotlin.math.abs(it.y - added.single().points.first().y) < .01f })
  }
  @Test fun slowCancelledRecognitionDoesNotDelayNextCanvas() {
    val firstStarted = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    lateinit var first: PenCanvasView
    var second: PenCanvasView? = null
    try {
      onUi {
        first = createCanvas().apply {
          shapeRecognizer = { firstStarted.countDown(); releaseFirst.await(5, TimeUnit.SECONDS); null }
        }
        dispatchTouch(first, MotionEvent.ACTION_DOWN, 100f, 100f, .5f, 2)
        dispatchTouch(first, MotionEvent.ACTION_MOVE, 340f, 100f, .5f, 3)
      }
      assertTrue(firstStarted.await(3, TimeUnit.SECONDS))
      onUi {
        first.cancelActiveGesture()
        val next = createCanvas().apply { shapeRecognizer = { secondStarted.countDown(); null } }
        second = next
        dispatchTouch(next, MotionEvent.ACTION_DOWN, 100f, 200f, .5f, 4)
        dispatchTouch(next, MotionEvent.ACTION_MOVE, 340f, 200f, .5f, 5)
      }
      assertTrue("a stale recognition must not block a new canvas", secondStarted.await(2, TimeUnit.SECONDS))
    } finally {
      releaseFirst.countDown()
      second?.let { view -> onUi { view.cancelActiveGesture() } }
    }
  }
  @Test fun eraserShowsItsActualBoundaryWhileTouching() {
    onUi {
      val view = createCanvas().apply { selectedTool = PenCanvasTool.ERASER; eraserRadiusPx = 30f }
      val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
      dispatchTouch(view, MotionEvent.ACTION_DOWN, 250f, 250f, 0.5f, 2)
      view.draw(Canvas(bitmap))
      assertTrue("eraser outline should be visible at its actual radius", Color.alpha(bitmap.getPixel(280, 250)) > 0)
      assertEquals("outline should not fill the center", 0, Color.alpha(bitmap.getPixel(250, 250)))
      dispatchTouch(view, MotionEvent.ACTION_UP, 250f, 250f, 0.5f, 3)
      bitmap.eraseColor(Color.TRANSPARENT)
      view.draw(Canvas(bitmap))
      assertEquals("touch outline should disappear after lift", 0, Color.alpha(bitmap.getPixel(280, 250)))
      bitmap.recycle()
    }
  }
  @Test fun sweptEraserCommitsSplitNativeOutlineAsStraightPenFragments() {
    lateinit var canvas: PenCanvasView
    val committed = mutableListOf<List<PenStroke>>()
    onUi {
      val rectangle = PenStroke("rect", "#000000", 4.0,
        listOf(PenPoint(200f, 200f, 1f), PenPoint(800f, 800f, 1f)))
      canvas = createCanvas().apply {
        strokes = listOf(rectangle)
        selectedTool = PenCanvasTool.ERASER
        eraserRadiusPx = 24f
        onAreaEraseCommitted = committed::add
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 80f, .5f, 1)
      for (step in 1..15) dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 250f, 80f + step * 20f, .5f, (step + 1).toLong())
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 250f, 380f, .5f, 20)
    }
    assertEquals(1, committed.size)
    assertTrue(committed.single().size >= 2)
    assertTrue(committed.single().all { it.type == "pen" && it.extraFields[PenStroke.POLYLINE_FIELD] == true })
  }

  @Test fun eraserShowsBoundaryWhileStylusHoversAndHidesOnExit() {
    onUi {
      val view = createCanvas().apply { selectedTool = PenCanvasTool.ERASER; eraserRadiusPx = 30f }
      val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
      withEvent(MotionEvent.ACTION_HOVER_MOVE, 250f, 250f, 0f, 0, 2) { view.onHoverEvent(it) }
      view.draw(Canvas(bitmap))
      assertTrue(Color.alpha(bitmap.getPixel(280, 250)) > 0)
      withEvent(MotionEvent.ACTION_HOVER_EXIT, 250f, 250f, 0f, 0, 3) { view.onHoverEvent(it) }
      bitmap.eraseColor(Color.TRANSPARENT)
      view.draw(Canvas(bitmap))
      assertEquals(0, Color.alpha(bitmap.getPixel(280, 250)))
      withEvent(MotionEvent.ACTION_HOVER_MOVE, 250f, 250f, 0f, 0, 4) { view.onHoverEvent(it) }
      view.cancelActiveGesture()
      bitmap.eraseColor(Color.TRANSPARENT)
      view.draw(Canvas(bitmap))
      assertEquals("cancel must clear a hover-only cursor", 0, Color.alpha(bitmap.getPixel(280, 250)))
      bitmap.recycle()
    }
  }
  @Test
  fun slowDwellRecognitionDoesNotBlockInputOrApplyAfterLift() {
    lateinit var canvas: PenCanvasView
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    onUi {
      canvas = createCanvas().apply {
        shapeRecognizer = { points ->
          started.countDown()
          release.await(5, TimeUnit.SECONDS)
          PenShapeRecognizer.recognize(points)
        }
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ -> recognized += shape; true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    try {
      assertTrue(started.await(3, TimeUnit.SECONDS))
      onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.6f, 4) }
      assertEquals(1, added.size)
    } finally { release.countDown() }
    Thread.sleep(200)
    onUi { }
    assertTrue("late recognition must not modify a completed gesture", recognized.isEmpty())
  }

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
      for (step in 1..5) {
        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 40f + step * 40f, 80f, 0.7f, (step + 2).toLong())
      }
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.6f, 8) }

    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
    assertTrue("recognized shape must not also be persisted as freehand", added.isEmpty())
    assertTrue(canvas.strokes.isEmpty())
  }

  @Test
  fun fingerDrawnLineKeepsFreehandAfterDwell() {
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    fun touch(action: Int, x: Float, time: Long) {
      withEvent(action, x, 80f, 0.5f, 0, time, toolType = MotionEvent.TOOL_TYPE_FINGER) {
        canvas.dispatchTouchEvent(it)
      }
    }
    onUi {
      canvas = createCanvas().apply {
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ -> recognized += shape; true }
      }
      touch(MotionEvent.ACTION_DOWN, 40f, 2)
      touch(MotionEvent.ACTION_MOVE, 240f, 3)
    }
    Thread.sleep(650)
    onUi { touch(MotionEvent.ACTION_UP, 240f, 4) }
    assertTrue(recognized.isEmpty())
    assertEquals("pen", added.single().type)
  }

  @Test
  fun ballpointDwellKeepsFreehand() {
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    onUi {
      canvas = createCanvas().apply {
        selectedTool = PenCanvasTool.BALLPOINT
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ -> recognized += shape; true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 240f, 80f, 0.7f, 3)
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 240f, 80f, 0.6f, 4) }
    assertTrue(recognized.isEmpty())
    assertEquals("pen", added.single().type)
    assertTrue(added.single().points.all { it.pressure == 1f })
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
      for (step in 1..5) {
        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 100f + step * 18f, 250f, 0.7f, (step + 2).toLong())
      }
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 190f, 250f, 0.6f, 8) }
    assertEquals(listOf("line"), recognized.map(PenRecognizedShape::type))
  }

  @Test
  fun movingAfterReferencePreviewKeepsFreehandAtTheNewEndpoint() {
    val added = mutableListOf<PenStroke>()
    val recognized = mutableListOf<PenRecognizedShape>()
    lateinit var canvas: PenCanvasView
    onUi {
      canvas = createCanvas().apply {
        onStrokeAdded = added::add
        onShapeRecognized = { shape, _ -> recognized += shape; true }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 40f, 80f, 0.5f, 2)
      for (step in 1..5) {
        dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 40f + step * 40f, 80f, 0.7f, (step + 2).toLong())
      }
    }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 320f, 80f, 0.7f, 8) }
    Thread.sleep(650)
    onUi { dispatchTouch(canvas, MotionEvent.ACTION_UP, 320f, 80f, 0.7f, 9) }
    assertTrue(recognized.isEmpty())
    assertEquals("pen", added.single().type)
    assertEquals(640f, added.single().points.last().x, 0.01f)
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
  fun areaEraserCutsLocalInkAndCommitsOnlyOncePerGesture() {
    lateinit var canvas: PenCanvasView
    val commits = mutableListOf<List<PenStroke>>()
    onUi {
      val stroke = PenStroke("pen", "#112233", 4.0,
        listOf(PenPoint(100f, 500f, 0.2f), PenPoint(900f, 500f, 0.8f)))
      canvas = createCanvas().apply {
        strokes = listOf(stroke)
        selectedTool = PenCanvasTool.ERASER
        onAreaEraseCommitted = { commits.add(it) }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 220f, 0.5f, 2)
      dispatchTouch(canvas, MotionEvent.ACTION_MOVE, 250f, 280f, 0.5f, 3)
      assertTrue("erasure remains an open gesture", commits.isEmpty())
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 250f, 280f, 0.5f, 4)
    }
    assertEquals(1, commits.size)
    assertEquals(2, commits.single().size)
    assertEquals(100f, commits.single().first().points.first().x, 0.01f)
    assertEquals(900f, commits.single().last().points.last().x, 0.01f)
  }

  @Test
  fun changingPageCancelsUnfinishedAreaEraseWithoutCommittingToNewPage() {
    val commits = mutableListOf<List<PenStroke>>()
    lateinit var canvas: PenCanvasView
    val original = PenStroke("pen", "#112233", 4.0,
      listOf(PenPoint(100f, 500f, 1f), PenPoint(900f, 500f, 1f)))
    onUi {
      canvas = createCanvas().apply {
        contentIdentity = "project:page1"
        strokes = listOf(original); selectedTool = PenCanvasTool.ERASER
        onAreaEraseCommitted = { commits.add(it) }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 250f, 0.5f, 2)
      assertEquals(2, canvas.strokes.size)
      canvas.contentIdentity = "project:page2"
    }
    assertTrue("An unfinished erase must not be committed to the next page", commits.isEmpty())
    assertEquals(listOf(original), canvas.strokes)
  }

  @Test
  fun systemCancelRestoresUnfinishedAreaEraseWithoutCommitting() {
    val commits = mutableListOf<List<PenStroke>>()
    lateinit var canvas: PenCanvasView
    val original = PenStroke("pen", "#112233", 4.0,
      listOf(PenPoint(100f, 500f, 1f), PenPoint(900f, 500f, 1f)))
    onUi {
      canvas = createCanvas().apply {
        strokes = listOf(original)
        selectedTool = PenCanvasTool.ERASER
        onAreaEraseCommitted = { commits.add(it) }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 250f, 0.5f, 2)
      assertEquals(2, canvas.strokes.size)
      dispatchTouch(canvas, MotionEvent.ACTION_CANCEL, 250f, 250f, 0.5f, 3)
    }
    assertTrue(commits.isEmpty())
    assertEquals(listOf(original), canvas.strokes)
    onUi {
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 250f, 0.5f, 4)
      canvas.cancelActiveGesture()
    }
    assertTrue(commits.isEmpty())
    assertEquals(listOf(original), canvas.strokes)
    onUi {
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 250f, 0.5f, 5)
      dispatchTouch(canvas, MotionEvent.ACTION_UP, 250f, 250f, 0.5f, 6)
    }
    assertEquals(1, commits.size)
  }

  @Test fun disablingInputRestoresUnfinishedAreaErase() {
    val commits = mutableListOf<List<PenStroke>>()
    lateinit var canvas: PenCanvasView
    val original = PenStroke("pen", "#112233", 4.0,
      listOf(PenPoint(100f, 500f, 1f), PenPoint(900f, 500f, 1f)))
    onUi {
      canvas = createCanvas().apply {
        strokes = listOf(original)
        selectedTool = PenCanvasTool.ERASER
        onAreaEraseCommitted = { commits.add(it) }
      }
      dispatchTouch(canvas, MotionEvent.ACTION_DOWN, 250f, 250f, 0.5f, 2)
      assertEquals(2, canvas.strokes.size)
      canvas.inputEnabled = false
    }
    assertTrue(commits.isEmpty())
    assertEquals(listOf(original), canvas.strokes)
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
