package io.pagenote.android

data class MarkdownCodeBlockEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val selectionStart: Int,
  val selectionEnd: Int,
)

object MarkdownCodeBlockInsertion {
  private const val OPENING = "\n```python\n"
  private const val CLOSING = "\n```\n"

  fun createEdit(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    placeholder: String,
  ): MarkdownCodeBlockEdit {
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val start = minOf(first, second)
    val end = maxOf(first, second)
    val selected = if (start == end) placeholder else markdown.substring(start, end)
    val replacement = OPENING + selected + CLOSING
    val codeStart = start + OPENING.length

    return MarkdownCodeBlockEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = replacement,
      selectionStart = codeStart,
      selectionEnd = codeStart + selected.length,
    )
  }
}
