package io.pagenote.android

import io.pagenote.feature.pen.PenStyle
import kotlin.math.abs

internal enum class PdfAnnotationDrawTool(val type: String) {
  LINE("line"),
  ARROW("arrow"),
  RECTANGLE("rect"),
  ELLIPSE("ellipse"),
}

internal class PdfAnnotationVectorDraft private constructor(
  val type: String,
  val first: Pair<Double, Double>,
  val second: Pair<Double, Double>,
  val color: String,
  val width: Double,
) {
  fun toItem(): Map<String, Any?> = mapOf(
    "type" to type,
    "color" to color,
    "width" to width,
    "points" to listOf(
      listOf(first.first, first.second),
      listOf(second.first, second.second),
    ),
  )

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
    ): PdfAnnotationVectorDraft? {
      if (type !in supportedTypes || !colorPattern.matches(color) ||
        !width.isFinite() || width !in 0.1..100.0
      ) {
        return null
      }
      val coordinates = listOf(first.first, first.second, second.first, second.second)
      if (coordinates.any { !it.isFinite() || abs(it) > MAX_COORDINATE }) return null

      val sameX = first.first == second.first
      val sameY = first.second == second.second
      if (if (type == "line" || type == "arrow") sameX && sameY else sameX || sameY) {
        return null
      }
      return PdfAnnotationVectorDraft(type, first, second, color, width)
    }
  }
}
