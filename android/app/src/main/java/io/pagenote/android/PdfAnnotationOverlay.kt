package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class PdfAnnotationOverlayItem(
  val type: String,
  val points: List<Pair<Double, Double>>,
  val color: String,
  val width: Double,
  val text: String?,
  val imageAssetName: String? = null,
  val html: String? = null,
  val sourceIndex: Int = -1,
) {
  companion object {
    private val colorPattern = Regex("^#[a-fA-F0-9]{6}$")
    private val vectorTypes = setOf("line", "arrow", "ellipse")

    fun fromPage(page: JsonElement): List<PdfAnnotationOverlayItem> {
      val items = page as? JsonArray ?: return emptyList()
      return items.mapIndexedNotNull(::parseItem)
    }

    private fun parseItem(sourceIndex: Int, element: JsonElement): PdfAnnotationOverlayItem? {
      val item = element as? JsonObject ?: return null
      val type = item["type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?: return null
      if (type != "rect" && type != "text" && type != "image" && type != "html" && type !in vectorTypes) return null
      val color = item["color"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf(colorPattern::matches) ?: return null
      val width = item["width"]?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
        ?.takeIf { it.isFinite() && it >= 0.1 && it <= 100.0 } ?: return null
      val rawPoints = item["points"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?: return null
      val minimumPointCount = if (type == "text" || type in vectorTypes) 1 else 2
      val pointCount = if (type == "text") 1 else 2
      if (rawPoints.size < minimumPointCount) return null
      val points = rawPoints.take(pointCount).map { point ->
        val coordinates = runCatching { point.jsonArray }.getOrNull() ?: return null
        if (coordinates.size < 2) return null
        val x = runCatching { coordinates[0].jsonPrimitive.doubleOrNull }.getOrNull()
          ?.takeIf(Double::isFinite) ?: return null
        val y = runCatching { coordinates[1].jsonPrimitive.doubleOrNull }.getOrNull()
          ?.takeIf(Double::isFinite) ?: return null
        if (abs(x) > MAX_COORDINATE || abs(y) > MAX_COORDINATE) return null
        x to y
      }
      val completePoints = if (type in vectorTypes && points.size == 1) {
        listOf(points[0], points[0])
      } else {
        points
      }
      val normalizedPoints = if (type == "rect" || type == "ellipse" || type == "html") {
        val first = completePoints[0]
        val second = completePoints[1]
        val left = minOf(first.first, second.first)
        val top = minOf(first.second, second.second)
        val right = maxOf(first.first, second.first)
        val bottom = maxOf(first.second, second.second)
        if ((type == "rect" || type == "html") && (left == right || top == bottom)) return null
        listOf(left to top, right to bottom)
      } else {
        completePoints
      }
      val imageAssetName = if (type == "image") {
        val rawUrl = item["url"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
          ?: return null
        PdfAnnotationImageReference.assetName(rawUrl) ?: return null
      } else {
        null
      }
      if (type == "image" &&
        (normalizedPoints[1].first <= normalizedPoints[0].first ||
          normalizedPoints[1].second <= normalizedPoints[0].second)
      ) {
        return null
      }
      val text = item["text"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      if (type == "text" && text.isNullOrBlank()) return null
      val html = if (type == "html") {
        item["html"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
          ?.takeIf { it.length <= MAX_HTML_CHARS } ?: return null
      } else {
        null
      }
      return PdfAnnotationOverlayItem(
        type = type,
        points = normalizedPoints,
        color = color,
        width = width,
        text = text,
        imageAssetName = imageAssetName,
        html = html,
        sourceIndex = sourceIndex,
      )
    }

    private const val MAX_COORDINATE = 1_000_000.0
    private const val MAX_HTML_CHARS = 100_000
  }
}

@Composable
internal fun PdfAnnotationOverlay(
  items: List<PdfAnnotationOverlayItem>,
  modifier: Modifier = Modifier,
  images: Map<String, Bitmap> = emptyMap(),
  htmlBitmaps: Map<Int, Bitmap> = emptyMap(),
  selectedBounds: PdfAnnotationBounds? = null,
  showResizeHandle: Boolean = true,
) {
  Canvas(modifier.fillMaxSize().testTag("pdf_annotation_overlay")) {
    val pageWidthScale = size.width / 1000f
    fun toPixel(point: Pair<Double, Double>) = Offset(
      (point.first * pageWidthScale).toFloat(),
      (point.second * pageWidthScale).toFloat(),
    )

    items.forEach { item ->
      val color = Color(android.graphics.Color.parseColor(item.color))
      val strokeWidth = (item.width.toFloat() * pageWidthScale).coerceAtLeast(1f)
      val vectorStroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
      )
      when (item.type) {
        "image" -> {
          val first = item.points[0]
          val second = item.points[1]
          val topLeft = toPixel(first)
          val width = ((second.first - first.first) * pageWidthScale).toFloat()
          val height = ((second.second - first.second) * pageWidthScale).toFloat()
          if (width > 0f && height > 0f) {
            val asset = item.imageAssetName?.let(images::get)
            if (asset != null && !asset.isRecycled) {
              drawImage(
                image = asset.asImageBitmap(),
                dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium,
              )
            } else {
              val imageSize = Size(width, height)
              val background = Color(0xFFFFF4D6)
              val marker = Color(0xFFC05040)
              drawRect(background, topLeft, imageSize)
              drawRect(
                color = marker,
                topLeft = topLeft,
                size = imageSize,
                style = Stroke(width = strokeWidth),
              )
              val inset = minOf(width, height) * 0.2f
              drawLine(marker, topLeft + Offset(inset, inset), topLeft + Offset(width - inset, height - inset), strokeWidth)
              drawLine(marker, topLeft + Offset(width - inset, inset), topLeft + Offset(inset, height - inset), strokeWidth)
            }
          }
        }
        "rect" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawRect(
            color = color,
            topLeft = toPixel(first),
            size = Size(
              ((second.first - first.first) * pageWidthScale).toFloat(),
              ((second.second - first.second) * pageWidthScale).toFloat(),
            ),
            style = Stroke(width = strokeWidth),
          )
        }
        "text" -> {
          val point = item.points.first()
          val textSize = maxOf(20.0, item.width * 5.0).toFloat() * pageWidthScale
          val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.parseColor(item.color)
            this.textSize = textSize
            style = Paint.Style.FILL
          }
          item.text.orEmpty().split('\n').forEachIndexed { index, line ->
            drawContext.canvas.nativeCanvas.drawText(
              line,
              (point.first * pageWidthScale).toFloat(),
              (point.second * pageWidthScale + index * textSize * 1.4f).toFloat(),
              paint,
            )
          }
        }
        "html" -> {
          val first = item.points[0]
          val second = item.points[1]
          val topLeft = toPixel(first)
          val width = ((second.first - first.first) * pageWidthScale).toFloat()
          val height = ((second.second - first.second) * pageWidthScale).toFloat()
          if (width > 0f && height > 0f) {
            val bitmap = htmlBitmaps[item.sourceIndex]
            if (bitmap != null && !bitmap.isRecycled) {
              drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium,
              )
            } else {
              val imageSize = Size(width, height)
              drawRect(Color(0xFFF5F8FD), topLeft, imageSize)
              drawRect(color, topLeft, imageSize, style = Stroke(width = strokeWidth))
            }
          }
        }
        "line", "arrow" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawLine(
            color = color,
            start = toPixel(first),
            end = toPixel(second),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
          )
          if (item.type == "arrow") {
            val direction = atan2(second.second - first.second, second.first - first.first)
            val headLength = 14.0 + item.width * 2.0
            listOf(direction - 0.45, direction + 0.45).forEach { wingAngle ->
              val wing = (second.first - headLength * cos(wingAngle)) to
                (second.second - headLength * sin(wingAngle))
              drawLine(
                color = color,
                start = toPixel(second),
                end = toPixel(wing),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
              )
            }
          }
        }
        "ellipse" -> {
          val first = item.points[0]
          val second = item.points[1]
          drawOval(
            color = color,
            topLeft = toPixel(first),
            size = Size(
              ((second.first - first.first) * pageWidthScale).toFloat(),
              ((second.second - first.second) * pageWidthScale).toFloat(),
            ),
            style = vectorStroke,
          )
        }
      }
    }
    selectedBounds?.let { bounds ->
      val topLeft = toPixel(bounds.left - 5.0 to bounds.top - 5.0)
      val bottomRight = toPixel(bounds.right + 5.0 to bounds.bottom + 5.0)
      val frameWidth = (2f * density).coerceAtLeast(1f)
      drawRect(
        color = Color(0xFF185ABD),
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        style = Stroke(width = frameWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * density, 4f * density))),
      )
      if (showResizeHandle) {
        drawCircle(
          color = Color.White,
          radius = 8f * density,
          center = bottomRight,
        )
        drawCircle(
          color = Color(0xFF185ABD),
          radius = 8f * density,
          center = bottomRight,
          style = Stroke(width = frameWidth),
        )
      }
    }
  }
}
