package io.pagenote.android

/**
 * 合并导出的页码选择（1 起页码）。
 *
 * 接受 `1-3,5` 形式的范围表达式：升序去重合并；越界、倒序区间、
 * 非法记号与空选择都直接报错，不做静默截断。
 */
internal object PdfPageMergePlan {
  class InvalidPageRange(message: String) : IllegalArgumentException(message)

  private val tokenPattern = Regex("^(\\d+)(?:-(\\d+))?$")

  fun parse(input: String, pageCount: Int): List<Int> {
    require(pageCount >= 1) { "document has no pages" }
    val text = input.trim()
    if (text.isEmpty()) throw InvalidPageRange("page range is empty")
    val selected = sortedSetOf<Int>()
    text.split(',', '，', '、').forEach { rawToken ->
      val token = rawToken.trim()
      if (token.isEmpty()) throw InvalidPageRange("empty page token in: $text")
      val match = tokenPattern.matchEntire(token) ?: throw InvalidPageRange("invalid page token: $token")
      val start = match.groupValues[1].toIntOrNull()
        ?: throw InvalidPageRange("invalid page number: $token")
      val end = match.groupValues[2].takeIf(String::isNotEmpty)
        ?.toIntOrNull()
        ?: start
      if (start < 1 || end < start) throw InvalidPageRange("page range out of order: $token")
      if (end > pageCount) throw InvalidPageRange("page $end exceeds document ($pageCount pages)")
      (start..end).forEach { selected += it }
    }
    if (selected.isEmpty()) throw InvalidPageRange("page range selected nothing")
    return selected.toList()
  }
}
