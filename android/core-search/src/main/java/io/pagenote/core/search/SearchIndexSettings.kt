package io.pagenote.core.search

/** 搜索索引范围设置：决定哪些类别进入索引。 */
data class SearchIndexSettings(
  val indexSource: Boolean = true,
  val indexNotes: Boolean = true,
  val indexOcr: Boolean = true,
  val indexTranscripts: Boolean = true,
) {
  fun kinds(): Set<SearchDocumentKind> = buildSet {
    if (indexSource) add(SearchDocumentKind.SOURCE)
    if (indexNotes) add(SearchDocumentKind.NOTE)
    if (indexOcr) add(SearchDocumentKind.OCR)
    if (indexTranscripts) add(SearchDocumentKind.TRANSCRIPT)
  }

  companion object {
    /** 读侧容错：全关时回落全开（空索引范围没有意义）。 */
    fun sanitize(settings: SearchIndexSettings): SearchIndexSettings =
      if (settings.kinds().isEmpty()) SearchIndexSettings() else settings

    fun validate(settings: SearchIndexSettings) {
      require(settings.kinds().isNotEmpty()) { "search index scope must not be empty" }
    }
  }
}
