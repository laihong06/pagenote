package io.pagenote.feature.notes

/**
 * 笔记 Markdown → 纯文本（合并导出 PDF 的 convert note 前置）。
 *
 * 只做保守剥离：标题/引用/列表前缀、行内强调、链接与图片、行内代码与围栏标记；
 * 不解析完整 Markdown 语法，无法识别的文本原样保留。
 */
object NotePlainText {
  private val INLINE = listOf(
    Regex("!\\[([^\\]]*)\\]\\([^)]*\\)") to "[图:$1]",
    Regex("\\[([^\\]]+)\\]\\([^)]*\\)") to "$1",
    Regex("\\*\\*([^*]+)\\*\\*") to "$1",
    Regex("(?<!\\*)\\*([^*\\s][^*]*)\\*(?!\\*)") to "$1",
    Regex("~~([^~]+)~~") to "$1",
    Regex("`([^`]+)`") to "$1",
  )
  private val STRUCTURAL_PREFIX = Regex("^(?:[#>]+\\s*|[-*+]\\s+|\\d+\\.\\s+)")

  fun plainify(markdown: String): String = markdown
    .split('\n')
    .mapNotNull { raw ->
      val line = raw.trim()
      when {
        line.isEmpty() -> null
        line.startsWith("```") -> null
        else -> {
          val inlineStripped = INLINE.fold(line) { text, (pattern, replacement) ->
            pattern.replace(text, replacement)
          }
          STRUCTURAL_PREFIX.replace(inlineStripped, "").trim().takeIf(String::isNotEmpty)
        }
      }
    }
    .joinToString("\n")
}
