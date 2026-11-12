package io.pagenote.feature.pen

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class ReferenceClosedShapeRecognizerInstrumentedTest {
  @Test
  fun closedRectangleUsesTheReferenceFourCornerBranch() {
    val shape = ReferenceClosedShapeRecognizer.recognize(rectangle(), geometry, 1f)

    assertEquals("rect", shape?.type)
    val rectangle = requireNotNull(shape)
    assertEquals(240f, rectangle.first.x, 3f)
    assertEquals(760f, rectangle.second.x, 3f)
    assertEquals(240f, rectangle.first.y, 3f)
    assertEquals(760f, rectangle.second.y, 3f)
  }

  @Test
  fun ellipseUsesTheReferenceEllipseFitAndReturnsClosedLinearPoints() {
    val shape = ReferenceClosedShapeRecognizer.recognize(ellipse(), geometry, 1f)

    assertEquals("closedPath", shape?.type)
    assertTrue(requireNotNull(shape).points.size >= 20)
    assertEquals(shape.points.first(), shape.points.last())
  }

  @Test
  fun triangleUsesTheReferenceClosedPolygonBranch() {
    val shape = ReferenceClosedShapeRecognizer.recognize(triangle(), geometry, 1f)

    assertEquals("closedPath", shape?.type)
    assertEquals(shape?.points?.first(), shape?.points?.last())
    assertTrue(requireNotNull(shape).points.size in 4..7)
  }

  @Test
  fun distantEndpointsDoNotEnterClosedContourRecognition() {
    assertNull(ReferenceClosedShapeRecognizer.recognize(rectangle().dropLast(8), geometry, 1f))
  }

  private fun rectangle(): List<Pair<Float, Float>> = path(
    listOf(144f to 144f, 456f to 144f, 456f to 456f, 144f to 456f, 144f to 144f),
    pointsPerEdge = 12,
  )

  private fun triangle(): List<Pair<Float, Float>> = path(
    listOf(150f to 430f, 300f to 150f, 450f to 430f, 150f to 430f),
    pointsPerEdge = 18,
  )

  private fun ellipse(): List<Pair<Float, Float>> = (0..80).map { index ->
    val angle = index / 80.0 * 2.0 * PI
    (300.0 + cos(angle) * 170.0).toFloat() to (300.0 + sin(angle) * 86.0).toFloat()
  }

  private fun path(vertices: List<Pair<Float, Float>>, pointsPerEdge: Int): List<Pair<Float, Float>> =
    vertices.zipWithNext().flatMapIndexed { edgeIndex, (start, end) ->
      (0 until pointsPerEdge).map { step ->
        val t = step.toFloat() / pointsPerEdge
        (start.first + (end.first - start.first) * t) to
          (start.second + (end.second - start.second) * t)
      } + if (edgeIndex == vertices.lastIndex - 1) listOf(vertices.last()) else emptyList()
    }

  private companion object {
    val geometry = PenCanvasGeometry(
      widthPx = 600f,
      heightPx = 600f,
      pageTransform = PenCanvasPageTransform(pageWidthPx = 600f, pageHeightPx = 600f),
    )

    @BeforeClass
    @JvmStatic
    fun loadOpenCv() {
      System.loadLibrary("opencv_java4")
    }
  }
}
