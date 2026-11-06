package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenFreehandRenderingInstrumentedTest {
  @Test fun constantPressureStrokeKeepsInkVisibleWithoutSubmittingEverySegmentSeparately() {
    var pathCount = 0
    var visible = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
      try {
        val canvas = object : Canvas(bitmap) {
          override fun drawPath(path: Path, paint: Paint) {
            pathCount++
            super.drawPath(path, paint)
          }
        }
        val points = (0..200).map { i -> PenPoint(80f + i * 3f, 400f + kotlin.math.sin(i / 10f) * 60f, 0.7f) }
        PenStrokePainter.drawStroke(canvas, PenStroke("pen", "#112233", 8.0, points), PenCanvasGeometry(500f, 500f))
        visible = (0 until 500).any { x -> (0 until 500).any { y -> bitmap.getPixel(x, y) != 0 } }
      } finally { bitmap.recycle() }
    }
    assertTrue("the stroke must remain visible", visible)
    assertEquals("a constant-pressure stroke can be drawn in one path", 1, pathCount)
  }

  @Test fun changingPressureStillChangesTheStrokeWidth() {
    var thinEdge = 0
    var thickEdge = 0
    var pathCount = 0
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
      try {
        val canvas = object : Canvas(bitmap) {
          override fun drawPath(path: Path, paint: Paint) {
            pathCount++
            super.drawPath(path, paint)
          }
        }
        val points = (0..20).map { i -> PenPoint(50f + i * 20f, 250f, 0.2f + i * 0.035f) }
        PenStrokePainter.drawStroke(canvas, PenStroke("pen", "#112233", 30.0, points), PenCanvasGeometry(500f, 500f))
        thinEdge = android.graphics.Color.alpha(bitmap.getPixel(80, 258))
        thickEdge = android.graphics.Color.alpha(bitmap.getPixel(420, 258))
      } finally { bitmap.recycle() }
    }
    assertEquals("pressure changes must remain visible at the thin end", 0, thinEdge)
    assertTrue("pressure changes must remain visible at the thick end", thickEdge > 200)
    assertEquals("a pressure-varying stroke should use one draw command", 1, pathCount)
  }

  @Test fun batchedCurvesKeepTheSameVisibleShapeAsIndividualSegments() {
    verifyVisibleShape(varyPressure = false, selfCrossing = false)
    verifyVisibleShape(varyPressure = true, selfCrossing = false)
    verifyVisibleShape(varyPressure = true, selfCrossing = true)
  }

  private fun verifyVisibleShape(varyPressure: Boolean, selfCrossing: Boolean) {
    var intersection = 0
    var union = 0
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val expected = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
      val actual = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
      try {
        val points = (0..160).map { i ->
          PenPoint(if (selfCrossing) 250f + kotlin.math.sin(i / 20f) * 150f else 30f + i * 2.5f,
            250f + kotlin.math.sin(if (selfCrossing) i / 10f else i / 8f) * 80f,
            if (varyPressure) 0.55f + kotlin.math.sin(i / 10f) * 0.3f else 0.7f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = android.graphics.Color.BLACK
          style = Paint.Style.STROKE
          strokeCap = Paint.Cap.ROUND
          strokeJoin = Paint.Join.ROUND
        }
        val canvas = Canvas(expected)
        val path = Path()
        PenStrokeSmoother.forEachSegment(points, 12f) { x, y, cx, cy, ex, ey, width ->
          path.rewind()
          path.moveTo(x, y)
          path.quadTo(cx, cy, ex, ey)
          paint.strokeWidth = width
          canvas.drawPath(path, paint)
        }
        PenStrokePainter.drawStroke(Canvas(actual), PenStroke("pen", "#000000", 12.0, points), PenCanvasGeometry(500f, 500f))
        for (y in 0 until 500) for (x in 0 until 500) {
          val before = android.graphics.Color.alpha(expected.getPixel(x, y)) >= 128
          val after = android.graphics.Color.alpha(actual.getPixel(x, y)) >= 128
          if (before || after) union++
          if (before && after) intersection++
        }
      } finally {
        expected.recycle()
        actual.recycle()
      }
    }
    assertTrue("batched curves should preserve the original visible shape", union > 0 && intersection.toFloat() / union > 0.98f)
  }

  @Test fun zeroPressureHairlinesRemainVisibleAlongsideVariablePressureInk() {
    var hairlineVisible = false
    var thickInkVisible = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
      try {
        val points = listOf(
          PenPoint(50f, 250f, 0f), PenPoint(100f, 250f, 0f), PenPoint(150f, 250f, 0f),
          PenPoint(200f, 250f, 1f), PenPoint(300f, 250f, 1f), PenPoint(400f, 250f, 0f),
        )
        PenStrokePainter.drawStroke(Canvas(bitmap), PenStroke("pen", "#000000", 20.0, points), PenCanvasGeometry(500f, 500f))
        hairlineVisible = (249..250).any { android.graphics.Color.alpha(bitmap.getPixel(75, it)) > 0 }
        thickInkVisible = android.graphics.Color.alpha(bitmap.getPixel(250, 257)) > 0
      } finally { bitmap.recycle() }
    }
    assertTrue("the zero-pressure hairline must still be drawn", hairlineVisible)
    assertTrue("the nonzero-pressure portion must still be drawn", thickInkVisible)
  }

  @Test fun longPressureVaryingStrokeUsesBoundedDrawBatches() {
    var pathCount = 0
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
      try {
        val canvas = object : Canvas(bitmap) {
          override fun drawPath(path: Path, paint: Paint) {
            pathCount++
            super.drawPath(path, paint)
          }
        }
        val points = (0..2_000).map { i ->
          PenPoint(30f + i * 0.2f, 250f + kotlin.math.sin(i / 30f) * 30f,
            0.55f + kotlin.math.sin(i / 20f) * 0.3f)
        }
        val started = android.os.Debug.threadCpuTimeNanos()
        PenStrokePainter.drawStroke(canvas, PenStroke("pen", "#112233", 8.0, points), PenCanvasGeometry(500f, 500f))
        val batchedCpu = android.os.Debug.threadCpuTimeNanos() - started
        val originalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = android.graphics.Color.BLACK; style = Paint.Style.STROKE
          strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val originalPath = Path()
        val originalCanvas = Canvas(bitmap)
        val originalStart = android.os.Debug.threadCpuTimeNanos()
        PenStrokeSmoother.forEachSegment(points, 8f) { x, y, cx, cy, ex, ey, width ->
          originalPath.rewind(); originalPath.moveTo(x, y); originalPath.quadTo(cx, cy, ex, ey)
          originalPaint.strokeWidth = width
          originalCanvas.drawPath(originalPath, originalPaint)
        }
        val originalCpu = android.os.Debug.threadCpuTimeNanos() - originalStart
        android.util.Log.i("PagenoteInkBenchmark", "long_varying_pressure_draw_cpu_ms batch=${batchedCpu / 1_000_000.0} previous=${originalCpu / 1_000_000.0}")
      } finally { bitmap.recycle() }
    }
    assertTrue("long variable-pressure strokes should use bounded batches, got $pathCount", pathCount in 1..32)
  }
}
