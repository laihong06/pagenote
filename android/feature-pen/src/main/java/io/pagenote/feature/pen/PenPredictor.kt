package io.pagenote.feature.pen

import kotlin.math.acos
import kotlin.math.hypot

/**
 * Display-only stroke extrapolation in view-pixel space, reimplemented from
 * the Newskyer PathPrediction heuristics: a nine-sample window gates
 * prediction, the recent turn angle buckets the extrapolation distance,
 * deceleration and pressure spikes damp it, and high refresh rates lengthen
 * it. Predicted points are never written to PenStroke storage; the owner
 * maps them back through PenCanvasGeometry when drawing the live tail.
 *
 * [predict] is a pure function of the current window: calling it repeatedly
 * without new samples returns the same prediction.
 */
class PenPredictor(
  private val densityPx: Float = 1f,
  private val refreshRateHz: Float = 60f,
) {
  data class Prediction(val x: Float, val y: Float)

  private data class Sample(val x: Float, val y: Float, val pressure: Float)

  private val window = ArrayDeque<Sample>()

  fun onDown() = reset()

  fun onUp() = reset()

  fun onMove(xPx: Float, yPx: Float, pressure: Float) {
    window.addLast(Sample(xPx, yPx, pressure))
    while (window.size > WINDOW_SIZE) window.removeFirst()
  }

  fun predict(): Prediction? {
    if (window.size < WINDOW_SIZE) return null
    val a = window[window.size - 3]
    val b = window[window.size - 2]
    val c = window[window.lastIndex]
    val stepPx = hypot(c.x - b.x, c.y - b.y)
    if (!stepPx.isFinite() || stepPx < MIN_STEP_DP * densityPx) return null
    val previousStepPx = hypot(b.x - a.x, b.y - a.y)

    var distancePx = baseExtrapolationDp(turnAngleDegrees(a, b, c)) * densityPx *
      speedFactor(stepPx)
    if (previousStepPx > 0f && stepPx < previousStepPx * DECELERATION_RATIO) {
      distancePx *= DECELERATION_DAMP
    }
    if (b.pressure > MIN_PRESSURE_FOR_RATIO && c.pressure / b.pressure >= PRESSURE_SPIKE_RATIO) {
      distancePx *= PRESSURE_DAMP
    }
    if (refreshRateHz > HIGH_REFRESH_HZ) distancePx *= HIGH_REFRESH_BOOST
    distancePx = distancePx.coerceAtMost(MAX_EXTRAPOLATION_DP * densityPx)

    val dx = (c.x - b.x) / stepPx
    val dy = (c.y - b.y) / stepPx
    return Prediction(c.x + dx * distancePx, c.y + dy * distancePx)
  }

  fun reset() {
    window.clear()
  }

  /** 0° means a perfectly straight continuation; 90° is a right-angle turn. */
  private fun turnAngleDegrees(a: Sample, b: Sample, c: Sample): Float {
    val v1x = b.x - a.x
    val v1y = b.y - a.y
    val v2x = c.x - b.x
    val v2y = c.y - b.y
    val v1 = hypot(v1x, v1y)
    val v2 = hypot(v2x, v2y)
    if (v1 <= 0f || v2 <= 0f) return 0f
    val cos = ((v1x * v2x) + (v1y * v2y)) / (v1 * v2)
    val angle = Math.toDegrees(acos(cos.coerceIn(-1.0f, 1.0f)).toDouble()).toFloat()
    return angle
  }

  private fun speedFactor(stepPx: Float): Float =
    (stepPx / (SPEED_REFERENCE_DP * densityPx)).coerceIn(MIN_SPEED_FACTOR, MAX_SPEED_FACTOR)

  private fun baseExtrapolationDp(turnAngleDegrees: Float): Float = when {
    turnAngleDegrees < 2f -> 5.0f
    turnAngleDegrees < 4f -> 4.0f
    turnAngleDegrees < 12f -> 2.5f
    turnAngleDegrees < 18f -> 1.5f
    turnAngleDegrees < 22f -> 1.0f
    else -> 0.4f
  }

  private companion object {
    const val WINDOW_SIZE = 9
    const val MIN_STEP_DP = 0.75f
    const val SPEED_REFERENCE_DP = 15f
    const val MIN_SPEED_FACTOR = 0.5f
    const val MAX_SPEED_FACTOR = 2.5f
    const val DECELERATION_RATIO = 0.7f
    const val DECELERATION_DAMP = 0.4f
    const val MIN_PRESSURE_FOR_RATIO = 0.1f
    const val PRESSURE_SPIKE_RATIO = 1.25f
    const val PRESSURE_DAMP = 0.5f
    const val HIGH_REFRESH_HZ = 90f
    const val HIGH_REFRESH_BOOST = 1.4f
    const val MAX_EXTRAPOLATION_DP = 20f
  }
}
