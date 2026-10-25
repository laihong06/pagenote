package io.pagenote.feature.pen

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenPredictorTest {
  private val density = 2f
  private val refresh = 120f

  @Test
  fun predictsNothingBeforeNineMoves() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(8) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    assertNull(predictor.predict())
  }

  @Test
  fun straightFastStrokeExtrapolatesForwardAlongTheLine() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(9) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    val lastX = 100f + 8 * 30f
    val prediction = predictor.predict()
    assertNotNull(prediction)
    assertEquals(200f, prediction!!.y, 0.01f)
    assertTrue(
      "expected forward extrapolation beyond $lastX, was ${prediction.x}",
      prediction.x > lastX,
    )
    assertTrue(
      "extrapolation must stay within the ${20f * density}px cap, was ${prediction.x - lastX}",
      prediction.x <= lastX + 20f * density,
    )
  }

  @Test
  fun sharpTurnExtrapolatesLessThanAStraightStrokeAndFollowsTheNewHeading() {
    val straight = PenPredictor(density, refresh)
    straight.onDown()
    repeat(9) { index -> straight.onMove(100f + index * 30f, 200f, 1f) }
    val straightDistance = straight.predict()!!.x - (100f + 8 * 30f)

    // Eight samples rightward, then one sample straight down: the corner sits
    // on the second-to-last sample, so the last heading turns 90 degrees.
    val turning = PenPredictor(density, refresh)
    turning.onDown()
    repeat(8) { index -> turning.onMove(100f + index * 30f, 200f, 1f) }
    turning.onMove(100f + 7 * 30f, 230f, 1f)
    val lastX = 100f + 7 * 30f
    val lastY = 230f
    val turned = turning.predict()!!
    val turnedDistance = hypot(turned.x - lastX, turned.y - lastY)
    assertTrue(
      "turn distance $turnedDistance should be below straight distance $straightDistance",
      turnedDistance < straightDistance,
    )
    assertTrue("prediction should follow the new downward heading", turned.y > lastY)
  }

  @Test
  fun stationaryTailPredictsNothing() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(9) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    predictor.onMove(100f + 8 * 30f, 200f, 1f)
    assertNull(predictor.predict())
  }

  @Test
  fun deceleratingStrokeExtrapolatesLessThanConstantSpeed() {
    fun extrapolation(lastStep: Float): Float {
      val predictor = PenPredictor(density, refresh)
      predictor.onDown()
      repeat(8) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
      predictor.onMove(100f + 7 * 30f + lastStep, 200f, 1f)
      val prediction = predictor.predict()!!
      return prediction.x - (100f + 7 * 30f + lastStep)
    }
    // The last sample's step is exactly lastStep (10 slows down from the
    // previous 30px step, 30 keeps constant speed), so only the 10px case
    // falls below 70% of its previous step and triggers DECELERATION_DAMP.
    // The doubled bound fails if the damping ever becomes a no-op.
    assertTrue(extrapolation(30f) > extrapolation(10f) * 2f)
  }

  @Test
  fun pressureSpikeDampsExtrapolation() {
    fun extrapolation(pressures: List<Float>): Float {
      val predictor = PenPredictor(density, refresh)
      predictor.onDown()
      pressures.forEachIndexed { index, pressure ->
        predictor.onMove(100f + index * 30f, 200f, pressure)
      }
      val prediction = predictor.predict()!!
      return prediction.x - (100f + (pressures.size - 1) * 30f)
    }
    val steady = extrapolation(List(9) { 0.4f })
    val spiking = extrapolation(List(8) { 0.4f } + 0.9f)
    assertTrue("spike $spiking should be damped below steady $steady", spiking < steady)
  }

  @Test
  fun upClearsTheWindowAndPredictIsIdempotent() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(9) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    val first = predictor.predict()
    val second = predictor.predict()
    assertNotNull(first)
    assertEquals(first!!.x, second!!.x, 0.001f)
    assertEquals(first.y, second.y, 0.001f)
    predictor.onUp()
    assertNull(predictor.predict())
  }
}
