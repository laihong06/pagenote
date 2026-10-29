package io.pagenote.android

import io.pagenote.feature.annotations.AnnotationTextStyle

object AnnotationTextDraft {
  private val COLOR = Regex("^#[a-fA-F0-9]{6}$")

  /**
   * Builds one `type: "text"` item. [style] (port batch 4 text style schema)
   * adds the font/fontSize/align extension fields; passing null keeps the
   * legacy desktop-compatible item without extension fields.
   */
  fun toItem(
    x: Double,
    y: Double,
    color: String,
    width: Double,
    text: String,
    style: AnnotationTextStyle? = null,
  ): Map<String, Any?> {
    require(x.isFinite() && y.isFinite()) { "bad annotation point" }
    require(COLOR.matches(color)) { "bad annotation color: $color" }
    require(width.isFinite() && width >= 0.1 && width <= 100.0) { "bad annotation width: $width" }
    require(text.isNotBlank()) { "annotation text must not be blank" }
    val item = linkedMapOf<String, Any?>(
      "type" to "text",
      "color" to color,
      "width" to width,
      "points" to listOf(listOf(x, y)),
      "text" to text.take(10000),
    )
    style?.extensionFields()?.forEach { (key, value) ->
      require(key !in item.keys) { "text style field collides: $key" }
      item[key] = value
    }
    return item
  }
}
