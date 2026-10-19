package io.pagenote.feature.pen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PenShapeRecognizerTest {
  @Test
  fun recognizesStraightLineDespiteSmallSamplingNoise() {
    val result = PenShapeRecognizer.recognize(
      points(
        0f to 0f,
        50f to 2f,
        100f to -1f,
        150f to 1f,
        200f to 0f,
      ),
    )

    assertEquals("line", result?.type)
    assertEquals(0f, result?.first?.x)
    assertEquals(200f, result?.second?.x)
  }

  @Test
  fun recognizesSingleStrokeArrowAndUsesItsTipAsEndpoint() {
    val result = PenShapeRecognizer.recognize(
      points(
        0f to 0f,
        200f to 0f,
        150f to -35f,
        200f to 0f,
        150f to 35f,
      ),
    )

    assertEquals("arrow", result?.type)
    assertEquals(0f, result?.first?.x)
    assertEquals(200f, result?.second?.x)
    assertEquals(0f, result?.second?.y)
  }

  @Test
  fun recognizesClosedRectangleAndEllipseAsTheirBounds() {
    val rectangle = PenShapeRecognizer.recognize(
      points(
        0f to 0f,
        200f to 0f,
        200f to 120f,
        0f to 120f,
        0f to 0f,
      ),
    )
    val ellipse = PenShapeRecognizer.recognize((0..24).map { index ->
      val angle = 2.0 * PI * index / 24
      PenPoint(
        100f + 100f * cos(angle).toFloat(),
        80f + 80f * sin(angle).toFloat(),
        1f,
      )
    })

    assertEquals("rect", rectangle?.type)
    assertEquals(0f to 0f, rectangle?.first?.let { it.x to it.y })
    assertEquals(200f to 120f, rectangle?.second?.let { it.x to it.y })
    assertEquals("ellipse", ellipse?.type)
  }

  @Test
  fun keepsShortNoisyAndAmbiguousPathsAsFreehand() {
    assertNull(PenShapeRecognizer.recognize(points(0f to 0f, 20f to 0f)))
    assertNull(PenShapeRecognizer.recognize(points(
      0f to 0f,
      40f to 60f,
      80f to -40f,
      120f to 70f,
      160f to -30f,
      200f to 0f,
    )))
    assertNull(PenShapeRecognizer.recognize(points(
      0f to 0f,
      180f to 10f,
      130f to 90f,
      30f to 120f,
      190f to 140f,
      0f to 0f,
    )))
  }

  private fun points(vararg coordinates: Pair<Float, Float>) = coordinates.map { (x, y) ->
    PenPoint(x, y, 1f)
  }
}
