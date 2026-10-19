package io.pagenote.android

object AnnotationRegionDraft {
  private val COLOR = Regex("^#[a-fA-F0-9]{6}$")

  fun toItem(
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
    color: String,
    width: Double,
    text: String,
  ): Map<String, Any?> {
    require(x1.isFinite() && y1.isFinite() && x2.isFinite() && y2.isFinite()) { "bad annotation point" }
    require(COLOR.matches(color)) { "bad annotation color: $color" }
    require(width.isFinite() && width >= 0.1 && width <= 100.0) { "bad annotation width: $width" }
    val left = minOf(x1, x2)
    val right = maxOf(x1, x2)
    val top = minOf(y1, y2)
    val bottom = maxOf(y1, y2)
    require(right > left && bottom > top) { "annotation region must not be empty" }
    return mapOf(
      "type" to "rect",
      "color" to color,
      "width" to width,
      "points" to listOf(listOf(left, top), listOf(right, bottom)),
      "text" to text.trim().take(10000),
    )
  }

  fun defaultSize(x: Double, y: Double, color: String, width: Double, text: String): Map<String, Any?> =
    toItem(x, y, x + 200.0, y + 120.0, color, width, text)
}
