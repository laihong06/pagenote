package io.pagenote.feature.notes

import io.pagenote.core.project.PageNotes

data class PageNoteEditResult(
  val markdown: String,
  val pageRevision: String,
)

class PageNoteConflictException : IllegalStateException("笔记页冲突：内容已被其他修改更新，请重新载入后再保存")

object PageNoteEditor {
  fun appendPage(
    latestMarkdown: String,
    page: Int,
    addition: String,
    expectedRevision: String,
  ): PageNoteEditResult {
    require(page >= 1) { "页码必须从 1 开始" }
    val content = addition.trim()
    require(content.isNotEmpty()) { "追加内容不能为空" }
    val existing = NotePage.pageAt(latestMarkdown, page)
    val separator = when {
      existing.isEmpty() || existing.endsWith("\n\n") -> ""
      existing.endsWith('\n') -> "\n"
      else -> "\n\n"
    }
    return replacePage(
      latestMarkdown = latestMarkdown,
      page = page,
      replacement = existing + separator + content,
      expectedRevision = expectedRevision,
    )
  }

  fun replacePage(
    latestMarkdown: String,
    page: Int,
    replacement: String,
    expectedRevision: String,
  ): PageNoteEditResult {
    require(page >= 1) { "页码必须从 1 开始" }
    val pages = PageNotes.split(latestMarkdown).toMutableList()
    while (pages.size < page) pages += ""

    val index = page - 1
    val currentRevision = PageNotes.revision(pages[index])
    if (currentRevision != expectedRevision) throw PageNoteConflictException()

    pages[index] = replacement
    return PageNoteEditResult(
      markdown = PageNotes.join(pages),
      pageRevision = PageNotes.revision(replacement),
    )
  }
}
