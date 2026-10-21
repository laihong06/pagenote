package io.pagenote.android

data class MarkdownFormulaEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val selectionStart: Int,
  val selectionEnd: Int,
)

object MarkdownFormulaInsertion {
  private const val OPENING = "\n\n$$\n"
  private const val CLOSING = "\n$$\n\n"
  private const val PLACEHOLDER = "E=mc^2"

  fun createEdit(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
  ): MarkdownFormulaEdit {
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val start = minOf(first, second)
    val end = maxOf(first, second)
    val formula = if (start == end) PLACEHOLDER else markdown.substring(start, end)
    val bodyStart = start + OPENING.length

    return MarkdownFormulaEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = OPENING + formula + CLOSING,
      selectionStart = bodyStart,
      selectionEnd = bodyStart + formula.length,
    )
  }
}
