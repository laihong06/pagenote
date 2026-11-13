package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Closed-contour branch of GraphUtils.recogniseShape, adapted to PageNote page coordinates. */
internal object ReferenceClosedShapeRecognizer {
  private const val REFERENCE_CLOSE_DISTANCE_DP = 12f
  private const val REFERENCE_RASTER_STROKE_PX = 2f
  private const val REFERENCE_BLUR_KERNEL_PX = 5
  private const val REFERENCE_THRESHOLD = 60.0
  private const val ELLIPSE_APPROXIMATION = 0.01
  private const val POLYGON_APPROXIMATION = 0.032

  private data class RasterRegion(val left: Int, val top: Int, val width: Int, val height: Int)
  private data class LocatedPoint(val x: Float, val y: Float)

  fun recognize(
    sampledScreenPoints: List<Pair<Float, Float>>,
    geometry: PenCanvasGeometry,
    density: Float,
  ): PenRecognizedShape? {
    if (!geometry.isValid || !density.isFinite() || density <= 0f || sampledScreenPoints.size <= 10) return null
    if (!nativeOpenCvAvailable) return null
    val first = sampledScreenPoints.first()
    val last = sampledScreenPoints.last()
    if (distance(first, last) >= REFERENCE_CLOSE_DISTANCE_DP * density) return null

    val region = rasterRegion(sampledScreenPoints, geometry.widthPx.toInt(), geometry.heightPx.toInt()) ?: return null
    val bitmap = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888)
    val source = Mat()
    val gray = Mat()
    val blurred = Mat()
    val hierarchy = Mat()
    val contours = mutableListOf<MatOfPoint>()
    try {
      drawReferenceContour(bitmap, sampledScreenPoints, region)
      Utils.bitmapToMat(bitmap, source)
      // GraphUtils uses cvtColor(..., 6), a 5x5 Gaussian blur and threshold 60.
      Imgproc.cvtColor(source, gray, 6)
      Imgproc.GaussianBlur(gray, blurred, Size(REFERENCE_BLUR_KERNEL_PX.toDouble(), REFERENCE_BLUR_KERNEL_PX.toDouble()), 0.0)
      Imgproc.threshold(blurred, blurred, REFERENCE_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
      Imgproc.findContours(blurred, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
      if (contours.isEmpty()) return null

      recognizeEllipseOrCircle(contours, region, geometry)?.let { return it }
      return recognizeClosedPolygon(contours, region, geometry)
    } finally {
      contours.forEach(Mat::release)
      hierarchy.release()
      blurred.release()
      gray.release()
      source.release()
      if (!bitmap.isRecycled) bitmap.recycle()
    }
  }

  private fun drawReferenceContour(
    bitmap: Bitmap,
    points: List<Pair<Float, Float>>,
    region: RasterRegion,
  ) {
    val path = Path().apply {
      moveTo(points.first().first - region.left, points.first().second - region.top)
      points.drop(1).forEach { lineTo(it.first - region.left, it.second - region.top) }
      close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      strokeWidth = REFERENCE_RASTER_STROKE_PX
      strokeCap = Paint.Cap.ROUND
      style = Paint.Style.STROKE
    }
    Canvas(bitmap).drawPath(path, paint)
  }

  private fun recognizeEllipseOrCircle(
    contours: List<MatOfPoint>,
    region: RasterRegion,
    geometry: PenCanvasGeometry,
  ): PenRecognizedShape? {
    var circle: Pair<LocatedPoint, Float>? = null
    for (contour in contours) {
      val sourcePoints = MatOfPoint2f(*contour.toArray())
      val approximated = MatOfPoint2f()
      try {
        val perimeter = Imgproc.arcLength(sourcePoints, true)
        Imgproc.approxPolyDP(sourcePoints, approximated, perimeter * ELLIPSE_APPROXIMATION, true)
        val points = approximated.toArray()
        if (points.size < 10) continue
        val moments = Imgproc.moments(contour)
        if (moments.m00 == 0.0) continue
        val centerX = (moments.m10 / moments.m00).toFloat()
        val centerY = (moments.m01 / moments.m00).toFloat()
        val radius = points.maxOf { point -> hypot(point.x - centerX, point.y - centerY).toFloat() }
        if (!radius.isFinite() || radius <= 0f) continue
        val ratio = Imgproc.contourArea(approximated) / (PI * radius * radius)
        when {
          ratio > 0.25 && ratio < 0.7 -> {
            val fitted = Imgproc.fitEllipse(approximated)
            val step = ((360.0 / sqrt(perimeter)) * 0.7).toInt().coerceAtLeast(1)
            val ellipse = MatOfPoint()
            try {
              Imgproc.ellipse2Poly(
                fitted.center,
                Size(fitted.size.width / 2.0, fitted.size.height / 2.0),
                fitted.angle.toInt(), 0, 360, step, ellipse,
              )
              return closedPath(ellipse.toArray().map { it.x.toFloat() + region.left to it.y.toFloat() + region.top }, geometry)
            } finally {
              ellipse.release()
            }
          }
          ratio >= 0.7 -> circle = LocatedPoint(centerX + region.left, centerY + region.top) to radius
        }
      } finally {
        approximated.release()
        sourcePoints.release()
      }
    }
    val (center, radius) = circle ?: return null
    val first = geometry.toDocumentPoint(center.x - radius, center.y - radius, 1f) ?: return null
    val second = geometry.toDocumentPoint(center.x + radius, center.y + radius, 1f) ?: return null
    return PenRecognizedShape("ellipse", first, second, shapeKind = "circle")
  }

  private fun recognizeClosedPolygon(
    contours: List<MatOfPoint>,
    region: RasterRegion,
    geometry: PenCanvasGeometry,
  ): PenRecognizedShape? {
    for (contour in contours) {
      val sourcePoints = MatOfPoint2f(*contour.toArray())
      val approximated = MatOfPoint2f()
      try {
        Imgproc.approxPolyDP(sourcePoints, approximated, Imgproc.arcLength(sourcePoints, true) * POLYGON_APPROXIMATION, true)
        val points = approximated.toArray().map { LocatedPoint(it.x.toFloat() + region.left, it.y.toFloat() + region.top) }
        when (points.size) {
          4 -> {
            val corners = clockwiseStartAtOrigin(points)
            val rightAngles = (0..3).count { index ->
              abs(90f - angleAt(corners[index], corners[(index + 1) % 4], corners[(index + 2) % 4])) < 8f
            }
            if (rightAngles == 4) {
              val centerX = corners.sumOf { it.x.toDouble() }.toFloat() / 4f
              val centerY = corners.sumOf { it.y.toDouble() }.toFloat() / 4f
              val left = corners.filter { it.x < centerX }.map(LocatedPoint::x).average().toFloat()
              val right = corners.filter { it.x >= centerX }.map(LocatedPoint::x).average().toFloat()
              val top = corners.filter { it.y < centerY }.map(LocatedPoint::y).average().toFloat()
              val bottom = corners.filter { it.y >= centerY }.map(LocatedPoint::y).average().toFloat()
              val first = geometry.toDocumentPoint(left, top, 1f) ?: return null
              val second = geometry.toDocumentPoint(right, bottom, 1f) ?: return null
              return PenRecognizedShape("rect", first, second)
            }
            return closedPath(corners.map { it.x to it.y }, geometry)
          }
          in 3..6 -> return closedPath(points.map { it.x to it.y }, geometry)
          in 7..Int.MAX_VALUE -> {
            val moments = Imgproc.moments(contour)
            if (moments.m00 == 0.0) continue
            val centerX = (moments.m10 / moments.m00).toInt().toFloat() + region.left
            val centerY = (moments.m01 / moments.m00).toInt().toFloat() + region.top
            val radius = points.maxOf { hypot(it.x - centerX, it.y - centerY) }
            if (!radius.isFinite() || radius <= 0f) continue
            val star = buildList {
              for (index in 0 until 5) {
                val outerAngle = index * 72.0 * PI / 180.0
                add((centerX - sin(outerAngle) * radius).toFloat() to (centerY - cos(outerAngle) * radius).toFloat())
                val innerAngle = (index * 72.0 + 36.0) * PI / 180.0
                add((centerX - sin(innerAngle) * radius * 0.38).toFloat() to (centerY - cos(innerAngle) * radius * 0.38).toFloat())
              }
            }
            return closedPath(star, geometry)
          }
        }
      } finally {
        approximated.release()
        sourcePoints.release()
      }
    }
    return null
  }

  private fun closedPath(points: List<Pair<Float, Float>>, geometry: PenCanvasGeometry): PenRecognizedShape? {
    if (points.size < 3) return null
    val documentPoints = points.map { (x, y) -> geometry.toDocumentPoint(x, y, 1f) ?: return null }.toMutableList()
    if (documentPoints.first() != documentPoints.last()) documentPoints += documentPoints.first()
    return PenRecognizedShape("closedPath", documentPoints.first(), documentPoints.last(), documentPoints)
  }

  private fun clockwiseStartAtOrigin(points: List<LocatedPoint>): List<LocatedPoint> {
    val centerX = points.map(LocatedPoint::x).average().toFloat()
    val centerY = points.map(LocatedPoint::y).average().toFloat()
    val clockwise = points.sortedBy { atan2((it.y - centerY).toDouble(), (it.x - centerX).toDouble()) }
    val startIndex = clockwise.indices.minBy { index ->
      val point = clockwise[index]
      point.x * point.x + point.y * point.y
    }
    return List(clockwise.size) { clockwise[(startIndex + it) % clockwise.size] }
  }

  /** Absolute angle at the middle point, matching the reference's corner test. */
  private fun angleAt(first: LocatedPoint, middle: LocatedPoint, last: LocatedPoint): Float {
    val ax = first.x - middle.x
    val ay = first.y - middle.y
    val bx = last.x - middle.x
    val by = last.y - middle.y
    val denominator = hypot(ax, ay) * hypot(bx, by)
    if (denominator <= 0f) return 0f
    val cosine = ((ax * bx + ay * by) / denominator).coerceIn(-1f, 1f)
    return Math.toDegrees(kotlin.math.acos(cosine).toDouble()).toFloat()
  }

  private fun rasterRegion(points: List<Pair<Float, Float>>, canvasWidth: Int, canvasHeight: Int): RasterRegion? {
    if (canvasWidth <= 0 || canvasHeight <= 0) return null
    // The reference rasterizes a full canvas. A tight ROI plus blur padding keeps the same local contour while
    // avoiding a 10–30 MB bitmap/Mat allocation for every dwell recognition on a tablet.
    val padding = REFERENCE_BLUR_KERNEL_PX + 2
    val left = (points.minOf { it.first }.toInt() - padding).coerceIn(0, canvasWidth - 1)
    val top = (points.minOf { it.second }.toInt() - padding).coerceIn(0, canvasHeight - 1)
    val right = (points.maxOf { it.first }.toInt() + padding + 1).coerceIn(left + 1, canvasWidth)
    val bottom = (points.maxOf { it.second }.toInt() + padding + 1).coerceIn(top + 1, canvasHeight)
    if (right - left < 5 || bottom - top < 5) return null
    return RasterRegion(left, top, right - left, bottom - top)
  }

  private fun distance(first: Pair<Float, Float>, second: Pair<Float, Float>) =
    hypot(first.first - second.first, first.second - second.second)

  private val nativeOpenCvAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    runCatching { System.loadLibrary("opencv_java4") }.isSuccess
  }
}
