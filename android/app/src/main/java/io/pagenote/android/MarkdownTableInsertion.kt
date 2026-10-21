package io.pagenote.android

data class MarkdownTableEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val cursor: Int,
)

object MarkdownTableInsertion {
  private const val TEMPLATE = "\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n"

  fun createEdit(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
  ): MarkdownTableEdit {
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val start = minOf(first, second)
    val end = maxOf(first, second)

    return MarkdownTableEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = TEMPLATE,
      cursor = start + TEMPLATE.length,
    )
  }
}
