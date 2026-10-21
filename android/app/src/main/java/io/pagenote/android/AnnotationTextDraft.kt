package io.pagenote.android

object AnnotationTextDraft {
  private val COLOR = Regex("^#[a-fA-F0-9]{6}$")

  fun toItem(x: Double, y: Double, color: String, width: Double, text: String): Map<String, Any?> {
    require(x.isFinite() && y.isFinite()) { "bad annotation point" }
    require(COLOR.matches(color)) { "bad annotation color: $color" }
    require(width.isFinite() && width >= 0.1 && width <= 100.0) { "bad annotation width: $width" }
    require(text.isNotBlank()) { "annotation text must not be blank" }
    return mapOf(
      "type" to "text",
      "color" to color,
      "width" to width,
      "points" to listOf(listOf(x, y)),
      "text" to text.take(10000),
    )
  }
}
