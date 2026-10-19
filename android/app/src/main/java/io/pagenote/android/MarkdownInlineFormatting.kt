package io.pagenote.android

enum class MarkdownInlineStyle(val opening: String, val closing: String) {
  BOLD("**", "**"),
  ITALIC("*", "*"),
  STRIKETHROUGH("~~", "~~"),
  UNDERLINE("<u>", "</u>"),
}

data class MarkdownInlineEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val selectionStart: Int,
  val selectionEnd: Int,
)

object MarkdownInlineFormatting {
  fun wrapSelection(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    style: MarkdownInlineStyle,
    placeholder: String,
  ): MarkdownInlineEdit {
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val start = minOf(first, second)
    val end = maxOf(first, second)
    val selected = if (start == end) placeholder else markdown.substring(start, end)
    val replacement = style.opening + selected + style.closing
    val resultSelectionStart = start + style.opening.length

    return MarkdownInlineEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = replacement,
      selectionStart = resultSelectionStart,
      selectionEnd = resultSelectionStart + selected.length,
    )
  }
}
