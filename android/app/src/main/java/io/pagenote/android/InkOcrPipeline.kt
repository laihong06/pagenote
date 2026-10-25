package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRect
import io.pagenote.feature.pen.PenRectHit
import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 手写 OCR 数据管线（移植批次 6）：选区（0..1000 归一化页坐标）→ 文档单
 * 位矩形 → 命中笔迹 → 转写后的页变换。纯 JVM 逻辑独立于 Android 位图，
 * 便于单测覆盖几何与页变换语义。
 */
object InkOcrSelection {

  /**
   * 归一化选区 → 文档单位矩形。x 方向两侧空间一致（均 0..1000）；y 方向
   * 选区按页高归一化（0..1000），文档 y 上限是 `1000 * pageHeight/pageWidth`
   * （[io.pagenote.feature.pen.PenCanvasGeometry] 的 pageMaxY 语义）。
   */
  fun documentRect(region: NormalizedOcrRegion, aspect: Double): PenRect {
    require(aspect.isFinite() && aspect > 0.0) { "bad page aspect: $aspect" }
    return PenRect(
      left = region.left.toDouble(),
      right = region.right.toDouble(),
      top = region.top.toDouble() * aspect,
      bottom = region.bottom.toDouble() * aspect,
    ).normalized()
  }

  /** 内存笔迹模型（pen/highlight）中与选区相交的笔画下标，升序。 */
  fun selectStrokeIndexes(
    strokes: List<PenStroke>,
    region: NormalizedOcrRegion,
    aspect: Double,
  ): List<Int> {
    val rect = documentRect(region, aspect)
    return strokes.indices.filter { index ->
      strokes[index].type == "pen" || strokes[index].type == "highlight"
    }.filter { index -> PenRectHit.strokeIntersects(strokes[index], rect) }
  }

  /** 页 items（JsonObject 数组）中与选区相交的 pen/highlight item 下标，升序。 */
  fun selectItemIndexes(
    page: JsonArray,
    region: NormalizedOcrRegion,
    aspect: Double,
  ): List<Int> {
    val rect = documentRect(region, aspect)
    return page.indices.filter { index ->
      val type = page[index].stringValue("type")
      type == "pen" || type == "highlight"
    }.filter { index ->
      val points = page[index].documentPoints() ?: return@filter false
      if (points.isEmpty()) {
        return@filter false
      }
      PenRectHit.polylineIntersects(points, rect)
    }
  }
}

/**
 * OcrConvert 页变换：删除被转写的墨迹 item（按 [canonicalItemKey] 内容指纹
 * 匹配，容忍插入期间的下标漂移与数值写法差异），追加转写产物（文本项或
 * 公式项）。纯函数、可回放。
 */
object InkOcrConversion {

  /** 按指纹删除 pen/highlight item + 追加新 item；返回删除的条数。 */
  fun convertPage(
    page: JsonArray,
    removeFingerprints: Set<String>,
    append: JsonObject,
  ): Pair<JsonArray, Int> {
    val raw = annotationPageToRawList(page)
    var removed = 0
    val kept = page.mapIndexedNotNull { index, element ->
      val rawItem = raw.getOrNull(index)
      val type = rawItem?.get("type") as? String
      if (type == "pen" || type == "highlight") {
        if (rawItem != null && canonicalItemKey(rawItem) in removeFingerprints) {
          removed += 1
          null
        } else {
          element
        }
      } else {
        element
      }
    }
    return JsonArray(kept + append) to removed
  }

  /** 转写文本项：落在选区左上角，字号按选区高度推导（批次 4 文本样式 schema）。 */
  fun buildTextItem(
    region: NormalizedOcrRegion,
    aspect: Double,
    text: String,
    color: String,
    width: Double,
  ): JsonObject? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val rect = InkOcrSelection.documentRect(region, aspect)
    val height = rect.bottom - rect.top
    if (!height.isFinite() || height <= 0.0) return null
    // 行高按 1.35 倍字号估行数，约束在 schema 合法区间（20..500）。
    val fontSize = (height / 1.35).coerceIn(
      io.pagenote.feature.annotations.AnnotationTextStyle.MIN_FONT_SIZE,
      io.pagenote.feature.annotations.AnnotationTextStyle.MAX_FONT_SIZE,
    )
    return buildJsonObject {
      put("type", JsonPrimitive("text"))
      put("color", JsonPrimitive(color))
      put("width", JsonPrimitive(width))
      put("points", JsonArray(listOf(JsonArray(listOf(JsonPrimitive(rect.left), JsonPrimitive(rect.top))))))
      put("text", JsonPrimitive(trimmed.take(io.pagenote.feature.pen.PenValidation.MAX_TEXT_LENGTH)))
      put("font", JsonPrimitive(io.pagenote.feature.annotations.TextFontCatalog.DEFAULT_FONT_ID))
      put("fontSize", JsonPrimitive(fontSize.roundToInt().toDouble()))
      put("align", JsonPrimitive("left"))
    }
  }
}

/**
 * 墨迹 → 位图（OCR 输入）：选区内的 pen/highlight 笔画按文档单位比例缩放
 * 绘制到白底位图，长边约 [TARGET_LONG_EDGE_PX]，四周留 [PADDING_PX] 边距。
 * 输入笔画集合不被修改；返回位图归调用方所有。
 */
object InkBitmapComposer {
  const val TARGET_LONG_EDGE_PX = 1280
  const val PADDING_PX = 16
  const val MIN_SIDE_PX = 32

  fun bitmapSize(region: NormalizedOcrRegion, aspect: Double): Pair<Int, Int> {
    val rect = InkOcrSelection.documentRect(region, aspect)
    val widthUnits = max(rect.right - rect.left, 1.0)
    val heightUnits = max(rect.bottom - rect.top, 1.0)
    val scale = TARGET_LONG_EDGE_PX.toDouble() / max(widthUnits, heightUnits)
    val width = (widthUnits * scale).roundToInt().coerceAtLeast(MIN_SIDE_PX)
    val height = (heightUnits * scale).roundToInt().coerceAtLeast(MIN_SIDE_PX)
    return width + PADDING_PX * 2 to height + PADDING_PX * 2
  }

  fun render(
    strokes: List<PenStroke>,
    region: NormalizedOcrRegion,
    aspect: Double,
  ): Bitmap? {
    val rect = InkOcrSelection.documentRect(region, aspect)
    val selected = strokes.filter { it.type == "pen" || it.type == "highlight" }
      .filter { PenRectHit.strokeIntersects(it, rect) }
    if (selected.isEmpty()) return null
    val (width, height) = bitmapSize(region, aspect)
    val contentWidth = width - PADDING_PX * 2
    val contentHeight = height - PADDING_PX * 2
    val unitsPerPixelX = (rect.right - rect.left).coerceAtLeast(1e-6) / contentWidth
    val unitsPerPixelY = (rect.bottom - rect.top).coerceAtLeast(1e-6) / contentHeight

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    val canvas = Canvas(bitmap)
    canvas.translate(PADDING_PX.toFloat(), PADDING_PX.toFloat())
    canvas.scale((1f / unitsPerPixelX).toFloat(), (1f / unitsPerPixelY).toFloat())
    canvas.translate(-rect.left.toFloat(), -rect.top.toFloat())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
    selected.forEach { stroke ->
      paint.color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
      paint.alpha = if (stroke.type == "highlight") HIGHLIGHT_ALPHA else 255
      drawStroke(canvas, paint, stroke)
    }
    return bitmap
  }

  private fun drawStroke(canvas: Canvas, paint: Paint, stroke: PenStroke) {
    val points = stroke.points
    if (points.isEmpty()) return
    if (points.size == 1) {
      val radius = stroke.width.toFloat() * points[0].pressure / 2f
      paint.style = Paint.Style.FILL
      canvas.drawCircle(points[0].x, points[0].y, radius, paint)
      paint.style = Paint.Style.STROKE
      return
    }
    for (index in 1 until points.size) {
      val previous = points[index - 1]
      val current = points[index]
      paint.strokeWidth = stroke.width.toFloat() * (previous.pressure + current.pressure) / 2f
      canvas.drawLine(previous.x, previous.y, current.x, current.y, paint)
    }
  }

  private const val HIGHLIGHT_ALPHA = 89
}

/** JsonArray 页 → PenPageStore.reloadPage 需要的原始 Map 形态。 */
internal fun annotationPageToRawList(page: JsonArray): List<Map<String, Any?>> =
  page.mapNotNull { element -> element.toRawMapOrNull() }

/**
 * item 内容指纹（OcrConvert 删除匹配用）：把页 item 归一化为稳定字符串，
 * 消除 `3` 与 `3.0` 这类 JSON 数值写法差异（org.json 与 kotlinx 序列化对
 * 整数值 Double 的写法不同）。两侧（内存 PenStroke 序列化、磁盘 JsonObject
 * 转回 Map）都经过本函数后再比较。
 */
internal fun canonicalItemKey(item: Map<String, Any?>): String {
  val builder = StringBuilder()
  appendCanonicalValue(builder, item)
  return builder.toString()
}

private fun appendCanonicalValue(builder: StringBuilder, value: Any?) {
  when (value) {
    null -> builder.append("null")
    is Map<*, *> -> {
      builder.append('{')
      value.entries
        .filter { it.key is String }
        .sortedBy { it.key as String }
        .forEachIndexed { index, entry ->
          if (index > 0) builder.append(',')
          builder.append(entry.key as String).append(':')
          appendCanonicalValue(builder, entry.value)
        }
      builder.append('}')
    }
    is List<*> -> {
      builder.append('[')
      value.forEachIndexed { index, entry ->
        if (index > 0) builder.append(',')
        appendCanonicalValue(builder, entry)
      }
      builder.append(']')
    }
    is String -> builder.append('"').append(value).append('"')
    is Double -> {
      if (value.isFinite() && value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
        builder.append(value.toLong().toString())
      } else {
        builder.append(value.toString())
      }
    }
    is Number -> builder.append(value.toString())
    is Boolean -> builder.append(value.toString())
    else -> builder.append('"').append(value.toString()).append('"')
  }
}

/** 一次待落盘的墨迹转换（手写文本或公式），携带删除指纹与选区上下文。 */
internal data class PendingInkConversion(
  val pageIndex: Int,
  val region: NormalizedOcrRegion,
  val aspect: Double,
  /** 需删除的 pen/highlight item 指纹（[canonicalItemKey]）。 */
  val fingerprints: Set<String>,
  /** 转写源笔迹条数（状态展示/判空）。 */
  val strokeCount: Int,
  val ocrResult: OcrResult? = null,
  val latexResult: io.pagenote.feature.ocr.LatexOcrResult? = null,
)

private fun JsonElement.toRawMapOrNull(): Map<String, Any?>? {
  val item = this as? JsonObject ?: return null
  return item.entries.associate { (key, value) -> key to value.toRawValue() }
}

private fun JsonElement.toRawValue(): Any? = when (this) {
  is JsonArray -> map { it.toRawValue() }
  is JsonObject -> toRawMapOrNull() ?: emptyMap<String, Any?>()
  is JsonPrimitive -> when {
    this is JsonPrimitive && isString -> content
    content == "true" -> true
    content == "false" -> false
    content == "null" -> null
    else -> doubleOrNull ?: content
  }
  else -> null
}

private fun JsonElement.stringValue(key: String): String? =
  (this as? JsonObject)?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

private fun JsonElement.documentPoints(): List<PenPoint>? {
  val item = this as? JsonObject ?: return null
  val raw = item["points"] as? JsonArray ?: return null
  val pressures = (item["pressures"] as? JsonArray)
    ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() }
    ?: emptyList()
  return raw.mapIndexed { index, point ->
    val pair = point as? JsonArray ?: return null
    val x = (pair.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: return null
    val y = (pair.getOrNull(1) as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: return null
    val pressure = pressures.getOrNull(index) ?: 1f
    PenPoint(x, y, pressure)
  }
}
