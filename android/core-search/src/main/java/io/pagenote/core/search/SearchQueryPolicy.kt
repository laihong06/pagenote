package io.pagenote.core.search

/**
 * 查询计划（FTS4 unicode61 分词的语义补偿）：
 * - 纯 CJK 查询走 `Contains`（LIKE 扫描）：unicode61 会把连续汉字当成单个
 *   token，FTS 前缀匹配无法覆盖"子串命中"，中文搜索必须走 LIKE。
 * - 其余（含拉丁词）走 `FullText`：每词引号包裹 + 前缀星号，词间 AND。
 */
sealed interface SearchQueryPlan {
  data object Empty : SearchQueryPlan
  data class FullText(val match: String) : SearchQueryPlan
  data class Contains(val pattern: String) : SearchQueryPlan
}

object SearchQueryPolicy {
  const val MAX_QUERY_LENGTH = 200
  const val LIKE_ESCAPE = '\\'

  fun plan(query: String): SearchQueryPlan {
    val trimmed = query.trim().take(MAX_QUERY_LENGTH)
    if (trimmed.isEmpty()) return SearchQueryPlan.Empty
    return if (isCjkQuery(trimmed)) {
      SearchQueryPlan.Contains(likePattern(trimmed))
    } else {
      SearchQueryPlan.FullText(buildMatch(trimmed))
    }
  }

  /** FTS4 MATCH 表达式：按空白拆词，每词 `"term"*`（前缀匹配），引号翻倍转义。 */
  fun buildMatch(query: String): String {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    require(terms.isNotEmpty()) { "query has no terms" }
    return terms.joinToString(" ") { term -> "\"${term.replace("\"", "\"\"")}\"*" }
  }

  /**
   * LIKE 通配转义（DAO 查询用 `ESCAPE '\'`）：反斜杠、`%`、`_` 均转义，
   * 使查询按字面匹配。
   */
  fun likePattern(query: String): String {
    val escaped = query
      .replace(LIKE_ESCAPE.toString(), "$LIKE_ESCAPE$LIKE_ESCAPE")
      .replace("%", "${LIKE_ESCAPE}%")
      .replace("_", "${LIKE_ESCAPE}_")
    return "%$escaped%"
  }

  /** 纯 CJK（含全角标点）且非空白 → 走 LIKE；空串不算。 */
  fun isCjkQuery(query: String): Boolean {
    val visible = query.filterNot(Char::isWhitespace)
    if (visible.isEmpty()) return false
    return visible.all(::isCjkChar)
  }

  private fun isCjkChar(char: Char): Boolean {
    val code = char.code
    return code in 0x2E80..0x9FFF || // CJK 部首扩展 / 符号标点 / 统一表意文字
      code in 0x3400..0x4DBF ||
      code in 0xF900..0xFAFF ||
      code in 0xFF00..0xFFEF || // 全角形式
      code in 0x3000..0x303F
  }
}
