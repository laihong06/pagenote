package io.pagenote.feature.notes

import io.pagenote.core.project.PageNotes

object NotePage {
  const val PREVIEW_LIMIT = 8000

  fun preview(markdown: String, maxChars: Int = PREVIEW_LIMIT): Pair<String, Boolean> {
    require(maxChars > 0)
    if (markdown.length <= maxChars) return markdown to false
    return markdown.substring(0, maxChars) to true
  }

  fun pageAt(rightMarkdown: String, page: Int): String {
    require(page >= 1)
    return PageNotes.split(rightMarkdown).getOrElse(page - 1) { "" }
  }
}
