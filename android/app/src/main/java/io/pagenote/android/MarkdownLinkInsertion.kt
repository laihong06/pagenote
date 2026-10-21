package io.pagenote.android

data class MarkdownLinkEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val selectionStart: Int,
  val selectionEnd: Int,
)

object MarkdownLinkInsertion {
  private const val URL = "https://example.com"

  fun createEdit(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    placeholder: String,
  ): MarkdownLinkEdit {
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val start = minOf(first, second)
    val end = maxOf(first, second)
    val label = markdown.substring(start, end).ifEmpty { placeholder }
    val selectionStart = start + 1

    return MarkdownLinkEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = "[$label]($URL)",
      selectionStart = selectionStart,
      selectionEnd = selectionStart + label.length,
    )
  }
}
