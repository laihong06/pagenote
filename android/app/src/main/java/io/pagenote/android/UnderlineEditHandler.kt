package io.pagenote.android

import android.text.Editable
import android.text.Spanned
import android.text.style.UnderlineSpan
import io.noties.markwon.editor.AbstractEditHandler
import io.noties.markwon.editor.PersistedSpans

internal class UnderlineEditHandler : AbstractEditHandler<UnderlineSpan>() {
  override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
    builder.persistSpan(UnderlineSpan::class.java) { UnderlineSpan() }
  }

  @Suppress("UNUSED_PARAMETER")
  override fun handleMarkdownSpan(
    persistedSpans: PersistedSpans,
    editable: Editable,
    input: String,
    span: UnderlineSpan,
    spanStart: Int,
    spanTextLength: Int,
  ) {
    val match = findTagPair(input, spanStart) ?: return
    editable.setSpan(
      persistedSpans.get(UnderlineSpan::class.java),
      match.contentStart,
      match.contentEnd,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
  }

  override fun markdownSpanType(): Class<UnderlineSpan> = UnderlineSpan::class.java

  private fun findTagPair(input: String, start: Int): TagPair? {
    val pairs = listOf("<u>" to "</u>", "<ins>" to "</ins>")
    val candidate = pairs.mapNotNull { (open, close) ->
      val openStart = input.indexOf(open, start)
      if (openStart < 0) return@mapNotNull null
      TagCandidate(open, close, openStart)
    }.minByOrNull { it.openStart } ?: return null

    val contentStart = candidate.openStart + candidate.open.length
    var cursor = contentStart
    var depth = 1
    while (depth > 0) {
      val nextOpen = input.indexOf(candidate.open, cursor)
      val nextClose = input.indexOf(candidate.close, cursor)
      if (nextClose < 0) return null
      if (nextOpen >= 0 && nextOpen < nextClose) {
        depth++
        cursor = nextOpen + candidate.open.length
      } else {
        depth--
        if (depth == 0) return TagPair(contentStart, nextClose)
        cursor = nextClose + candidate.close.length
      }
    }
    return null
  }

  private data class TagCandidate(val open: String, val close: String, val openStart: Int)
  private data class TagPair(val contentStart: Int, val contentEnd: Int)
}
