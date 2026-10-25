package io.pagenote.android

import io.pagenote.feature.pen.PenStyle
import io.pagenote.feature.pen.PenRecognizedShape
import io.pagenote.feature.pen.ShapeCatalog
import kotlin.math.abs

/**
 * 形状绘制工具。基础四项（line/arrow/rect/ellipse）不带 shapeKind，落盘无 `shape` 字段；
 * 目录扩展项（移植批次 2）的 [type] 取桌面 9 类型中的基类型，[shapeKind] 取
 * ShapeCatalog 的 kind id，序列化为 item extension 字段 `shape`（schema 决策 D5，
 * canvas-schema-decision.md）。
 */
internal enum class PdfAnnotationDrawTool(val type: String, val shapeKind: String? = null) {
  LINE("line"),
  ARROW("arrow"),
  RECTANGLE("rect"),
  ELLIPSE("ellipse"),
  DASHED_LINE("line", shapeKind = "dashed-line"),
  DOUBLE_ARROW("line", shapeKind = "double-arrow"),
  AXIS_QUADRANT_1("line", shapeKind = "axis-quadrant-1"),
  AXIS_QUADRANTS("line", shapeKind = "axis-quadrants"),
  WAVE("line", shapeKind = "wave"),
  ZIGZAG("line", shapeKind = "zigzag"),
  SQUARE("rect", shapeKind = "square"),
  ROUNDED_RECTANGLE("rect", shapeKind = "rounded-rect"),
  DASHED_RECTANGLE("rect", shapeKind = "dashed-rect"),
  TRIANGLE("rect", shapeKind = "triangle"),
  RIGHT_TRIANGLE("rect", shapeKind = "right-triangle"),
  DIAMOND("rect", shapeKind = "diamond"),
  PENTAGON("rect", shapeKind = "pentagon"),
  HEXAGON("rect", shapeKind = "hexagon"),
  STAR_5("rect", shapeKind = "star-5"),
  STAR_4("rect", shapeKind = "star-4"),
  PARALLELOGRAM("rect", shapeKind = "parallelogram"),
  TRAPEZOID("rect", shapeKind = "trapezoid"),
  CROSS("rect", shapeKind = "cross"),
  CIRCLE("ellipse", shapeKind = "circle"),
}

internal class PdfAnnotationVectorDraft private constructor(
  val type: String,
  val first: Pair<Double, Double>,
  val second: Pair<Double, Double>,
  val color: String,
  val width: Double,
  val shapeKind: String?,
) {
  fun toItem(): Map<String, Any?> {
    val item = mutableMapOf<String, Any?>(
      "type" to type,
      "color" to color,
      "width" to width,
      "points" to listOf(
        listOf(first.first, first.second),
        listOf(second.first, second.second),
      ),
    )
    if (shapeKind != null) {
      item["shape"] = shapeKind
    }
    return item
  }

  fun toOverlayItem() = PdfAnnotationOverlayItem(
    type = type,
    points = listOf(first, second),
    color = color,
    width = width,
    text = null,
  )

  companion object {
    private val colorPattern = Regex("^#[a-fA-F0-9]{6}$")
    private val supportedTypes = PdfAnnotationDrawTool.entries.map(PdfAnnotationDrawTool::type).toSet()
    private const val MAX_COORDINATE = 1_000_000.0

    fun create(
      type: String,
      first: Pair<Double, Double>,
      second: Pair<Double, Double>,
      color: String = PenStyle.DEFAULT.color,
      width: Double = PenStyle.DEFAULT.width,
      shapeKind: String? = null,
    ): PdfAnnotationVectorDraft? {
      if (!colorPattern.matches(color) || !width.isFinite() || width !in 0.1..100.0) {
        return null
      }
      // 目录扩展项：kind 必须存在且基类型与 type 匹配（D5）；基础项：type 限既有四类。
      val kind = shapeKind?.let(ShapeCatalog::byId)
      if (shapeKind != null && (kind == null || kind.baseType != type)) return null
      if (shapeKind == null && type !in supportedTypes) return null
      val coordinates = listOf(first.first, first.second, second.first, second.second)
      if (coordinates.any { !it.isFinite() || abs(it) > MAX_COORDINATE }) return null

      // 退化语义按基类型族区分（对齐 D4 §4）：线族拒绝两点重合，面族拒绝零面积。
      val isLineFamily = kind?.baseType == "line" || (kind == null && (type == "line" || type == "arrow"))
      val sameX = first.first == second.first
      val sameY = first.second == second.second
      if (if (isLineFamily) sameX && sameY else sameX || sameY) {
        return null
      }
      return PdfAnnotationVectorDraft(type, first, second, color, width, shapeKind)
    }

    fun fromRecognizedShape(
      shape: PenRecognizedShape,
      color: String,
      width: Double,
    ): PdfAnnotationVectorDraft? = create(
      type = shape.type,
      first = shape.first.x.toDouble() to shape.first.y.toDouble(),
      second = shape.second.x.toDouble() to shape.second.y.toDouble(),
      color = color,
      width = width,
    )
  }
}
