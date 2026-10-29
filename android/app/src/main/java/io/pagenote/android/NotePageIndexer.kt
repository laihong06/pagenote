package io.pagenote.android

import io.pagenote.core.project.PageNotes
import io.pagenote.core.search.SearchDoc
import io.pagenote.core.search.SearchDocumentKind
import io.pagenote.core.search.SearchRect

/** 已分页文本：页号（1 起）、页文本与页级 revision。 */
data class IndexedPageText(val page: Int, val content: String, val revision: String)

/**
 * 页文本 → 索引文档（移植批次 7）：笔记页与 Markdown 源页共用
 * `PageNotes` 的页分隔语义；空白页不建索引。
 */
object NotePageIndexer {
  fun pages(markdown: String): List<IndexedPageText> =
    PageNotes.split(markdown)
      .mapIndexed { index, content -> IndexedPageText(index + 1, content, PageNotes.revision(content)) }
      .filter { it.content.isNotBlank() }

  fun noteDocs(projectId: String, markdown: String): List<SearchDoc> =
    pages(markdown).map { it.toDoc(projectId, SearchDocumentKind.NOTE) }

  fun sourceDocs(projectId: String, markdown: String): List<SearchDoc> =
    pages(markdown).map { it.toDoc(projectId, SearchDocumentKind.SOURCE) }

  fun ocrDoc(projectId: String, page: Int, text: String, rect: SearchRect? = null): SearchDoc? =
    text.takeIf { it.isNotBlank() }?.toDoc(projectId, page, SearchDocumentKind.OCR, rect)

  fun transcriptDoc(projectId: String, page: Int, text: String, rect: SearchRect? = null): SearchDoc? =
    text.takeIf { it.isNotBlank() }?.toDoc(projectId, page, SearchDocumentKind.TRANSCRIPT, rect)

  private fun IndexedPageText.toDoc(projectId: String, kind: SearchDocumentKind): SearchDoc =
    SearchDoc(projectId = projectId, page = page, kind = kind, content = content, revision = revision)

  private fun String.toDoc(
    projectId: String,
    page: Int,
    kind: SearchDocumentKind,
    rect: SearchRect?,
  ): SearchDoc = SearchDoc(
    projectId = projectId,
    page = page,
    kind = kind,
    content = this,
    revision = PageNotes.revision(this),
    rect = rect,
  )
}
