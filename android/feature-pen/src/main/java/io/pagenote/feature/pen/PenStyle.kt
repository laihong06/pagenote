package io.pagenote.feature.pen

/** Active appearance for newly authored annotations; saved items keep their own style. */
class PenStyle private constructor(
  val color: String,
  val width: Double,
) {
  companion object {
    val DEFAULT = PenStyle("#dc3545", 3.0)
    val WIDTH_OPTIONS = listOf(1.5, 3.0, 6.0, 10.0)
    val COLOR_SWATCHES = listOf(
      "red" to "#dc3545",
      "blue" to "#185abd",
      "green" to "#2e7d32",
      "orange" to "#ef6c00",
      "purple" to "#7b1fa2",
      "dark" to "#212121",
      "amber" to "#f9a825",
      "teal" to "#00838f",
    )

    private val rgbColor = Regex("^#[a-fA-F0-9]{6}$")

    fun isValidColor(color: String): Boolean = rgbColor.matches(color)

    fun create(color: String, width: Double): PenStyle? {
      if (!isValidColor(color) || width !in WIDTH_OPTIONS) return null
      return PenStyle(color.lowercase(), width)
    }
  }
}
