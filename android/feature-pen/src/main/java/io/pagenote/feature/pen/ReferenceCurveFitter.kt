package io.pagenote.feature.pen

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.atan

/** Least-squares fallback used by GraphUtils.isCurveAndCreate after isLine. */
internal object ReferenceCurveFitter {
  private const val POLYNOMIAL_COEFFICIENTS = 6

  private data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width.coerceAtLeast(0.0) * height.coerceAtLeast(0.0)

    fun contains(x: Double, y: Double) = x >= left && x < right && y >= top && y < bottom
  }

  private data class Candidate(
    val points: List<Pair<Float, Float>>,
    val deviation: Double,
    val invalid: Boolean,
  )

  private enum class OutlierRule { FOUR_AFTER_FIT_TAIL, FOUR_ANYWHERE, FIRST_ANYWHERE }

  /** Returns fitted points in input screen coordinates, or null when the reference rejects the curve. */
  fun fit(
    points: List<PenPoint>,
    density: Float,
    arrowPosition: Int? = null,
    strokeWidth: Float = 4f,
  ): List<Pair<Float, Float>>? {
    if (points.size < POLYNOMIAL_COEFFICIENTS || !density.isFinite() || density <= 0f) return null
    val fitPoints = if (arrowPosition == null || arrowPosition !in 1 until points.size) {
      points
    } else {
      points.take(arrowPosition)
    }
    if (fitPoints.size < POLYNOMIAL_COEFFICIENTS) return null

    val start = fitPoints.first()
    val end = fitPoints.last()
    val bounds = bounds(points)
    val maxDimension = max(bounds.width, bounds.height)
    val minEndpointDistance = if (maxDimension > 60.0 * density) maxDimension / 4.0 else 15.0 * density
    if (distance(start, end) < minEndpointDistance) return null

    // GraphUtils expands its x-fit rectangle by half its width, then expands
    // that same rectangle by half its height before the y-fit pass. The source
    // truncates each margin to integer screen pixels.
    val xFitMargin = bounds.width.toInt() / 2
    val xFitBounds = bounds.expanded(xFitMargin)
    val yFitMargin = (bounds.height + 2.0 * xFitMargin).toInt() / 2
    val yFitBounds = xFitBounds.expanded(yFitMargin)
    val fitYFromX = PolynomialFit.create(fitPoints.map { it.x.toDouble() }, fitPoints.map { it.y.toDouble() })
    val fitXFromY = PolynomialFit.create(fitPoints.map { it.y.toDouble() }, fitPoints.map { it.x.toDouble() })

    val candidateX = fitYFromX?.let {
      candidate(
        points = points,
        fitCount = fitPoints.size,
        start = start,
        end = end,
        independent = PenPoint::x,
        dependent = PenPoint::y,
        independentIsX = true,
        fit = it,
        bounds = xFitBounds,
        tolerance = 5.0 * density,
        outlierRule = if (end.x > start.x) OutlierRule.FOUR_AFTER_FIT_TAIL else OutlierRule.FOUR_ANYWHERE,
        accumulateDeviation = true,
      )
    }
    val candidateY = fitXFromY?.let {
      candidate(
        points = points,
        fitCount = fitPoints.size,
        start = start,
        end = end,
        independent = PenPoint::y,
        dependent = PenPoint::x,
        independentIsX = false,
        fit = it,
        bounds = yFitBounds,
        tolerance = 5.0 * density,
        outlierRule = if (end.y > start.y) OutlierRule.FOUR_AFTER_FIT_TAIL else OutlierRule.FIRST_ANYWHERE,
        accumulateDeviation = false,
      )
    }

    val x = candidateX
    val y = candidateY
    val (chosen, validationDeviation) = when {
      x == null && y != null && !y.invalid -> y to y.deviation
      x == null || x.invalid && (y == null || y.invalid) -> return null
      x.invalid -> y!! to x.deviation
      y == null || y.invalid -> x to x.deviation
      y.points.size >= x.points.size && x.deviation > y.deviation -> y to y.deviation
      else -> x to x.deviation
    }

    if (chosen.points.size < 2 || validationDeviation > distance(start, end)) return null
    val fittedBounds = boundsOfCoordinates(chosen.points)
    if (bounds.area > fittedBounds.area * 2.5) return null
    val result = chosen.points.toMutableList()
    if (arrowPosition != null && arrowPosition >= 8 && arrowPosition < points.size && result.size > 10) {
      result += referenceArrowHead(
        previous = result[result.lastIndex - 2],
        tip = result.last(),
        density = density,
        strokeWidth = strokeWidth,
      )
    }
    return result
  }

  /** Mirrors GraphUtils' three sampled arrowhead segments appended after a fitted shaft. */
  private fun referenceArrowHead(
    previous: Pair<Float, Float>,
    tip: Pair<Float, Float>,
    density: Float,
    strokeWidth: Float,
  ): List<Pair<Float, Float>> {
    if (!strokeWidth.isFinite() || strokeWidth <= 0f) return emptyList()
    val scale = density * strokeWidth / 4.0
    val halfWidth = 3.0 * scale
    val length = 5.0 * scale
    val angle = atan(halfWidth / length)
    val vectorX = tip.first - previous.first
    val vectorY = tip.second - previous.second
    val vectorLength = hypot(vectorX.toDouble(), vectorY.toDouble())
    if (vectorLength <= 1e-9) return emptyList()
    val headLength = sqrt(halfWidth * halfWidth + length * length)
    fun rotate(radians: Double): Pair<Float, Float> {
      val rotatedX = vectorX * kotlin.math.cos(radians) - vectorY * kotlin.math.sin(radians)
      val rotatedY = vectorX * kotlin.math.sin(radians) + vectorY * kotlin.math.cos(radians)
      return (rotatedX / vectorLength * headLength).toFloat() to
        (rotatedY / vectorLength * headLength).toFloat()
    }
    val firstVector = rotate(angle)
    val secondVector = rotate(-angle)
    val firstBase = (tip.first - firstVector.first).toInt().toFloat() to
      (tip.second - firstVector.second).toInt().toFloat()
    val secondBase = (tip.first - secondVector.first).toInt().toFloat() to
      (tip.second - secondVector.second).toInt().toFloat()
    val spacing = 3.0 * density
    return sampleSegment(tip, firstBase, spacing) +
      sampleSegment(firstBase, tip, spacing) +
      sampleSegment(tip, secondBase, spacing)
  }

  private fun sampleSegment(
    start: Pair<Float, Float>,
    end: Pair<Float, Float>,
    spacing: Double,
  ): List<Pair<Float, Float>> {
    val dx = end.first - start.first
    val dy = end.second - start.second
    val distance = hypot(dx.toDouble(), dy.toDouble())
    val steps = ceil(distance / spacing).toInt().coerceAtLeast(1)
    return (0 until steps).map { step ->
      val ratio = step * spacing / distance
      (start.first + dx * ratio).toFloat() to (start.second + dy * ratio).toFloat()
    }
  }

  private fun candidate(
    points: List<PenPoint>,
    fitCount: Int,
    start: PenPoint,
    end: PenPoint,
    independent: (PenPoint) -> Float,
    dependent: (PenPoint) -> Float,
    independentIsX: Boolean,
    fit: PolynomialFit,
    bounds: Bounds,
    tolerance: Double,
    outlierRule: OutlierRule,
    accumulateDeviation: Boolean,
  ): Candidate {
    val increasing = independent(end) > independent(start)
    var threshold = independent(start).toDouble() + if (increasing) -1.0 else 1.0
    val result = mutableListOf<Pair<Float, Float>>()
    var accumulatedDeviation = 0.0
    var lastDeviation = 0.0
    var outlierCount = 0

    fun currentDeviation() = if (result.isEmpty()) Double.MAX_VALUE
      else if (accumulateDeviation) accumulatedDeviation else lastDeviation

    points.forEachIndexed { index, point ->
      val sampleNumber = index + 1
      val input = independent(point).toDouble()
      if (if (increasing) input < threshold else input > threshold) return@forEachIndexed
      val predicted = fit.value(input)
      if (!predicted.isFinite()) return Candidate(result, currentDeviation(), invalid = true)
      val actual = dependent(point).toDouble()
      val error = abs(predicted - actual)
      if (error > tolerance) {
        val qualifiesAsOutlier = when (outlierRule) {
          OutlierRule.FOUR_AFTER_FIT_TAIL -> abs(fitCount - sampleNumber) > 4
          OutlierRule.FOUR_ANYWHERE, OutlierRule.FIRST_ANYWHERE -> true
        }
        if (qualifiesAsOutlier) {
          outlierCount++
          if (outlierRule == OutlierRule.FIRST_ANYWHERE || outlierCount >= 4) {
            return Candidate(result, currentDeviation(), invalid = true)
          }
        }
      }
      val outputX = if (independentIsX) input else predicted
      val outputY = if (independentIsX) predicted else input
      if (bounds.contains(outputX, outputY)) {
        result += outputX.toFloat() to outputY.toFloat()
        lastDeviation = error
        accumulatedDeviation += error
        threshold = input
      }
    }

    return Candidate(result, currentDeviation(), invalid = false)
  }

  private fun bounds(points: List<PenPoint>): Bounds = Bounds(
    left = points.minOf { it.x.toDouble() },
    top = points.minOf { it.y.toDouble() },
    right = points.maxOf { it.x.toDouble() },
    bottom = points.maxOf { it.y.toDouble() },
  )

  private fun Bounds.expanded(margin: Int) = Bounds(
    left = left - margin,
    top = top - margin,
    right = right + margin,
    bottom = bottom + margin,
  )

  private fun boundsOfCoordinates(points: List<Pair<Float, Float>>): Bounds = Bounds(
    left = points.minOf { it.first.toDouble() },
    top = points.minOf { it.second.toDouble() },
    right = points.maxOf { it.first.toDouble() },
    bottom = points.maxOf { it.second.toDouble() },
  )

  private fun distance(first: PenPoint, second: PenPoint) =
    hypot(first.x.toDouble() - second.x, first.y.toDouble() - second.y)

  /** Stable degree-five normal-equation fit, equivalent to the source's 6-coefficient model. */
  private class PolynomialFit private constructor(
    private val center: Double,
    private val radius: Double,
    private val coefficients: DoubleArray,
  ) {
    fun value(input: Double): Double {
      val normalized = (input - center) / radius
      var power = 1.0
      var result = 0.0
      coefficients.forEach { coefficient ->
        result += coefficient * power
        power *= normalized
      }
      return result
    }

    companion object {
      fun create(x: List<Double>, y: List<Double>): PolynomialFit? {
        if (x.size != y.size || x.size < POLYNOMIAL_COEFFICIENTS ||
          x.any { !it.isFinite() } || y.any { !it.isFinite() }
        ) return null
        val minimum = x.minOrNull() ?: return null
        val maximum = x.maxOrNull() ?: return null
        val center = (minimum + maximum) / 2.0
        val radius = (maximum - minimum) / 2.0
        if (!radius.isFinite() || radius <= 1e-9) return null

        val order = POLYNOMIAL_COEFFICIENTS
        val powers = Array(x.size) { index ->
          DoubleArray(order * 2) { exponent -> ((x[index] - center) / radius).let { value ->
            if (exponent == 0) 1.0 else Math.pow(value, exponent.toDouble())
          } }
        }
        val matrix = Array(order) { row ->
          DoubleArray(order) { column -> powers.sumOf { it[row + column] } }
        }
        val vector = DoubleArray(order) { degree ->
          powers.indices.sumOf { index -> powers[index][degree] * y[index] }
        }
        val coefficients = solve(matrix, vector) ?: return null
        return PolynomialFit(center, radius, coefficients)
      }

      private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray? {
        val size = vector.size
        val augmented = Array(size) { row -> matrix[row].copyOf(size + 1).apply { this[size] = vector[row] } }
        for (column in 0 until size) {
          val pivotRow = (column until size).maxByOrNull { abs(augmented[it][column]) } ?: return null
          val pivot = augmented[pivotRow][column]
          if (!pivot.isFinite() || abs(pivot) < 1e-12) return null
          if (pivotRow != column) {
            val temporary = augmented[pivotRow]
            augmented[pivotRow] = augmented[column]
            augmented[column] = temporary
          }
          val divisor = augmented[column][column]
          for (index in column..size) augmented[column][index] /= divisor
          for (row in 0 until size) {
            if (row == column) continue
            val factor = augmented[row][column]
            for (index in column..size) augmented[row][index] -= factor * augmented[column][index]
          }
        }
        return DoubleArray(size) { augmented[it][size] }.takeIf { values -> values.all(Double::isFinite) }
      }
    }
  }
}
