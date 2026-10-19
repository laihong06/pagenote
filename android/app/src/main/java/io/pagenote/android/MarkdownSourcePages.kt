package io.pagenote.android

import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage

object MarkdownSourcePages {
  fun isMarkdownExtension(extension: String): Boolean {
    val normalized = extension.lowercase()
    return normalized == "md" || normalized == "markdown"
  }

  fun splitSource(markdown: String): List<String> = PageNotes.split(markdown)

  fun pageCount(sourceMarkdown: String, notesMarkdown: String): Int =
    maxOf(splitSource(sourceMarkdown).size, PageNotes.split(notesMarkdown).size).coerceAtLeast(1)

  fun pageAt(sourceMarkdown: String, page: Int): String {
    require(page >= 1)
    return splitSource(sourceMarkdown).getOrElse(page - 1) { "" }
  }

  fun previewPage(
    sourceMarkdown: String,
    page: Int,
    maxChars: Int = NotePage.PREVIEW_LIMIT,
  ): Pair<String, Boolean> = NotePage.preview(pageAt(sourceMarkdown, page), maxChars)
}
