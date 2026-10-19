package io.pagenote.android

enum class MarkdownParagraphPrefix(val value: String) {
  HEADING_1("# "),
  HEADING_2("## "),
  HEADING_3("### "),
  UNORDERED_LIST("- "),
  ORDERED_LIST("1. "),
  TASK_LIST("- [ ] "),
  QUOTE("> "),
}

data class MarkdownParagraphEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val cursor: Int,
)

object MarkdownParagraphFormatting {
  fun prefixLines(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: MarkdownParagraphPrefix,
  ): MarkdownParagraphEdit {
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val selectionFrom = minOf(first, second)
    val selectionTo = maxOf(first, second)
    val lineStart = markdown.lastIndexOf('\n', selectionFrom - 1) + 1
    val nextLineBreak = markdown.indexOf('\n', selectionTo)
    val lineEnd = if (nextLineBreak < 0) markdown.length else nextLineBreak
    val selected = markdown.substring(lineStart, lineEnd)
    val replacement = buildString {
      append(prefix.value)
      selected.forEach { character ->
        append(character)
        if (character == '\n') append(prefix.value)
      }
    }

    return MarkdownParagraphEdit(
      replaceStart = lineStart,
      replaceEnd = lineEnd,
      replacement = replacement,
      cursor = lineStart + replacement.length,
    )
  }
}
