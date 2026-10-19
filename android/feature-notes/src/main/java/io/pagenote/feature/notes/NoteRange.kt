package io.pagenote.feature.notes

data class NoteSlice(val text: String, val start: Int, val end: Int, val total: Int, val hasMore: Boolean)

object NoteRange {
  fun slice(markdown: String, start: Int, maxChars: Int = NotePage.PREVIEW_LIMIT): NoteSlice {
    require(start >= 0)
    require(maxChars > 0)
    val total = markdown.length
    if (start >= total) return NoteSlice("", start, start, total, false)
    val end = minOf(total, start + maxChars)
    return NoteSlice(markdown.substring(start, end), start, end, total, end < total)
  }

  fun patch(original: String, start: Int, replacement: String, end: Int? = null): String {
    require(start in 0..original.length)
    val actualEnd = end ?: minOf(original.length, start + replacement.length)
    require(actualEnd in start..original.length)
    return original.substring(0, start) + replacement + original.substring(actualEnd)
  }
}
