package io.pagenote.core.search

/** 检索文档主键：项目 + 页 + 类别。 */
data class SearchDocKey(
  val projectId: String,
  val page: Int,
  val kind: SearchDocumentKind,
)

/**
 * 归一化定位矩形（0..1000，与阅读器 OCR 选区同语义：x 按页宽、y 按页高）。
 * 结果跳转时映射回页内坐标高亮并居中视口。
 */
data class SearchRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  init {
    require(left in 0..1000 && top in 0..1000 && right in 0..1000 && bottom in 0..1000) {
      "search rect must be normalized to 0..1000"
    }
    require(right > left && bottom > top) { "search rect must be non-empty" }
  }
}

/** 一条待索引文档。revision 变化才重写（增量索引的幂等闸门）。 */
data class SearchDoc(
  val projectId: String,
  val page: Int,
  val kind: SearchDocumentKind,
  val content: String,
  val revision: String,
  val rect: SearchRect? = null,
) {
  init {
    require(projectId.isNotBlank()) { "search doc projectId must not be blank" }
    require(page >= 1) { "search doc page must start at 1" }
  }

  val key: SearchDocKey get() = SearchDocKey(projectId, page, kind)
}

/** 一条检索结果：定位到页码 +（可选）页内矩形。 */
data class SearchHit(
  val projectId: String,
  val page: Int,
  val kind: SearchDocumentKind,
  val snippet: String,
  val rect: SearchRect? = null,
)
