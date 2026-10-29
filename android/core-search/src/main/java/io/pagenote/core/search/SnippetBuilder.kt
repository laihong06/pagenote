package io.pagenote.core.search

/** 结果摘要：命中词附近的窗口文本。 */
object SnippetBuilder {
  const val DEFAULT_MAX_LENGTH = 120
  const val ELLIPSIS = "…"

  /**
   * 规整空白后在正文中定位查询词（任一分词首次命中），返回命中附近的窗口。
   * 无命中时退化为开头截断。
   */
  fun build(content: String, query: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
    require(maxLength > 0) { "snippet length must be positive" }
    val text = content.replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return ""
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val index = terms.firstNotNullOfOrNull { term -> text.indexOf(term, ignoreCase = true) }
      ?.takeIf { it >= 0 }
      ?: return truncate(text, maxLength, start = 0)
    val start = (index - maxLength / 4).coerceAtLeast(0)
    return truncate(text, maxLength, start)
  }

  private fun truncate(text: String, maxLength: Int, start: Int): String {
    val end = (start + maxLength).coerceAtMost(text.length)
    val prefix = if (start > 0) ELLIPSIS else ""
    val suffix = if (end < text.length) ELLIPSIS else ""
    return prefix + text.substring(start, end) + suffix
  }
}
