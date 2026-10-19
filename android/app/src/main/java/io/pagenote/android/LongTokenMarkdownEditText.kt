package io.pagenote.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.EditText

internal class LongTokenMarkdownEditText(context: Context) : EditText(context) {
  var onMarkdownSelectionChanged: ((Int, Int) -> Unit)? = null
  var isSettingMarkdownSource = false
    private set
  private var changedSinceBreakHintCheck = 0
  private var requiresImmediateBreakHintCheck = false

  fun markdownSourceText(): String = MarkdownEditorTextProjection.sourceText(text)

  fun markdownSourceOffset(displayOffset: Int): Int =
    MarkdownEditorTextProjection.sourceOffset(text, displayOffset)

  fun displayOffsetForMarkdownSource(sourceOffset: Int): Int =
    MarkdownEditorTextProjection.displayOffset(text, sourceOffset)

  fun markdownSelectionStart(): Int = markdownSourceOffset(selectionStart)

  fun markdownSelectionEnd(): Int = markdownSourceOffset(selectionEnd)

  fun setMarkdownSourceText(source: String) {
    val sourceStart = selectionStart.takeIf { it >= 0 }?.let(::markdownSourceOffset)
    val sourceEnd = selectionEnd.takeIf { it >= 0 }?.let(::markdownSourceOffset)
    isSettingMarkdownSource = true
    try {
      setText(MarkdownEditorTextProjection.withBreakHints(source), BufferType.EDITABLE)
      if (sourceStart != null && sourceEnd != null) {
        setMarkdownSourceSelection(sourceStart, sourceEnd)
      }
    } finally {
      isSettingMarkdownSource = false
    }
  }

  fun setMarkdownSourceSelection(start: Int, end: Int = start) {
    setSelection(
      displayOffsetForMarkdownSource(start),
      displayOffsetForMarkdownSource(end),
    )
  }

  fun replaceMarkdownSourceRange(start: Int, end: Int, replacement: String) {
    val displayStart = displayOffsetForMarkdownSource(start)
    val displayEnd = displayOffsetForMarkdownSource(end)
    text.replace(
      displayStart,
      displayEnd,
      MarkdownEditorTextProjection.withBreakHints(replacement),
    )
    setMarkdownSourceSelection(start + replacement.length)
  }

  fun noteMarkdownTextChanged(before: Int, count: Int) {
    if (isSettingMarkdownSource) return
    val changed = maxOf(before, count)
    if (changed >= 4096) {
      requiresImmediateBreakHintCheck = true
    } else {
      changedSinceBreakHintCheck += maxOf(changed, 1)
    }
  }

  fun refreshBreakHintsIfNeeded() {
    if (!requiresImmediateBreakHintCheck && changedSinceBreakHintCheck < 128) return
    requiresImmediateBreakHintCheck = false
    changedSinceBreakHintCheck = 0
    val source = markdownSourceText()
    val projection = MarkdownEditorTextProjection.withBreakHints(source)
    if (projection.toString() == text.toString()) return
    val sourceStart = selectionStart.takeIf { it >= 0 }?.let(::markdownSourceOffset)
    val sourceEnd = selectionEnd.takeIf { it >= 0 }?.let(::markdownSourceOffset)
    isSettingMarkdownSource = true
    try {
      text.replace(0, text.length, projection)
      if (sourceStart != null && sourceEnd != null) {
        setMarkdownSourceSelection(sourceStart, sourceEnd)
      }
    } finally {
      isSettingMarkdownSource = false
    }
  }

  override fun onSelectionChanged(selStart: Int, selEnd: Int) {
    super.onSelectionChanged(selStart, selEnd)
    if (selStart >= 0 && selEnd >= 0) {
      onMarkdownSelectionChanged?.invoke(
        markdownSourceOffset(selStart),
        markdownSourceOffset(selEnd),
      )
    }
  }

  override fun onTextContextMenuItem(id: Int): Boolean {
    if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
      val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
      val pastedText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?: return true
      val start = minOf(markdownSelectionStart(), markdownSelectionEnd())
      val end = maxOf(markdownSelectionStart(), markdownSelectionEnd())
      replaceMarkdownSourceRange(start, end, pastedText)
      return true
    }
    if (id == android.R.id.shareText) {
      val source = markdownSourceText()
      val start = minOf(markdownSelectionStart(), markdownSelectionEnd()).coerceIn(0, source.length)
      val end = maxOf(markdownSelectionStart(), markdownSelectionEnd()).coerceIn(0, source.length)
      if (start == end) return true
      val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, source.substring(start, end))
      }
      context.startActivity(Intent.createChooser(share, null))
      return true
    }
    if (id != android.R.id.copy && id != android.R.id.cut) return super.onTextContextMenuItem(id)
    val source = markdownSourceText()
    val start = minOf(markdownSelectionStart(), markdownSelectionEnd()).coerceIn(0, source.length)
    val end = maxOf(markdownSelectionStart(), markdownSelectionEnd()).coerceIn(0, source.length)
    if (start == end) return true
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(null, source.substring(start, end)))
    if (id == android.R.id.cut) {
      replaceMarkdownSourceRange(start, end, "")
    }
    return true
  }
}

internal fun EditText.markdownSourceText(): String =
  (this as? LongTokenMarkdownEditText)?.markdownSourceText() ?: text.toString()

internal fun EditText.markdownSourceOffset(displayOffset: Int): Int =
  (this as? LongTokenMarkdownEditText)?.markdownSourceOffset(displayOffset) ?: displayOffset

internal fun EditText.markdownSelectionStart(): Int =
  (this as? LongTokenMarkdownEditText)?.markdownSelectionStart() ?: selectionStart

internal fun EditText.markdownSelectionEnd(): Int =
  (this as? LongTokenMarkdownEditText)?.markdownSelectionEnd() ?: selectionEnd

internal fun EditText.setMarkdownSourceText(source: String) {
  val markdownEditor = this as? LongTokenMarkdownEditText
  if (markdownEditor == null) {
    setText(source)
  } else {
    markdownEditor.setMarkdownSourceText(source)
  }
}

internal fun EditText.setMarkdownSourceSelection(start: Int, end: Int = start) {
  val markdownEditor = this as? LongTokenMarkdownEditText
  if (markdownEditor == null) {
    setSelection(start, end)
  } else {
    markdownEditor.setMarkdownSourceSelection(start, end)
  }
}

internal fun EditText.replaceMarkdownSourceRange(start: Int, end: Int, replacement: String) {
  val markdownEditor = this as? LongTokenMarkdownEditText
  if (markdownEditor == null) {
    text.replace(start, end, replacement)
    setSelection(start + replacement.length)
  } else {
    markdownEditor.replaceMarkdownSourceRange(start, end, replacement)
  }
}

private class MarkdownLayoutBoundarySpan : CharacterStyle(), UpdateAppearance {
  override fun updateDrawState(textPaint: TextPaint) = Unit
}

private object MarkdownEditorTextProjection {
  private const val BOUNDARY = '\u200e'
  private const val LONG_TOKEN_THRESHOLD = 4096
  private const val MAX_LAYOUT_CONTEXT = 256

  fun withBreakHints(source: String): SpannableStringBuilder {
    val result = SpannableStringBuilder()
    var index = 0
    while (index < source.length) {
      if (source[index].isWhitespace()) {
        result.append(source[index])
        index++
        continue
      }
      val tokenStart = index
      while (index < source.length && !source[index].isWhitespace()) index++
      val tokenEnd = index
      if (tokenEnd - tokenStart < LONG_TOKEN_THRESHOLD) {
        result.append(source, tokenStart, tokenEnd)
        continue
      }
      var segmentStart = tokenStart
      while (tokenEnd - segmentStart > MAX_LAYOUT_CONTEXT) {
        var segmentEnd = segmentStart + MAX_LAYOUT_CONTEXT
        if (segmentEnd < tokenEnd && source[segmentEnd].isLowSurrogate()) segmentEnd--
        result.append(source, segmentStart, segmentEnd)
        val boundaryStart = result.length
        result.append(BOUNDARY)
        result.setSpan(
          MarkdownLayoutBoundarySpan(),
          boundaryStart,
          boundaryStart + 1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        segmentStart = segmentEnd
      }
      result.append(source, segmentStart, tokenEnd)
    }
    return result
  }

  fun sourceText(displayText: CharSequence): String {
    val boundaries = boundaryRanges(displayText)
    if (boundaries.isEmpty()) return displayText.toString()
    return buildString(displayText.length - boundaries.size) {
      var cursor = 0
      boundaries.forEach { start ->
        append(displayText, cursor, start)
        cursor = start + 1
      }
      append(displayText, cursor, displayText.length)
    }
  }

  fun sourceOffset(displayText: CharSequence, displayOffset: Int): Int {
    val end = displayOffset.coerceIn(0, displayText.length)
    return end - boundaryRanges(displayText).count { it < end }
  }

  fun displayOffset(displayText: CharSequence, sourceOffset: Int): Int {
    val target = sourceOffset.coerceAtLeast(0)
    val boundaries = boundaryRanges(displayText).toHashSet()
    var display = 0
    var source = 0
    while (display < displayText.length && source < target) {
      if (display !in boundaries) source++
      display++
    }
    while (display < displayText.length && display in boundaries) display++
    return display
  }

  private fun boundaryRanges(text: CharSequence): List<Int> {
    val spanned = text as? Spanned ?: return emptyList()
    return spanned.getSpans(0, spanned.length, MarkdownLayoutBoundarySpan::class.java)
      .map { spanned.getSpanStart(it) }
      .filter { it >= 0 }
      .sorted()
  }
}
