package io.pagenote.android.flashcard

import io.pagenote.core.project.PageNotes

/**
 * 制卡捕获策略（移植批次 11 / 差距清单 #20）。
 *
 * 选区/笔记段落 → 卡片的纯策略层：边界语义与选区审阅（NoteSelectionReviewDraft）、
 * AI 选区动作（AiSelectionTask）一致——非空可选、UTF-16 偏移、捕获时记录页 revision。
 * 段落制卡在没有选区时按光标所在「空行分隔块」整体取段。
 */
object FlashcardCapture {
  const val MAX_TEXT_LENGTH = 20_000
  const val MAX_TAGS = 12
  const val MAX_TAG_LENGTH = 32

  /** 制卡捕获结果：front 即来源文本，sourceQuote/anchor 供来源定位回跳。 */
  data class Draft(
    val page: Int,
    val sourceKind: FlashcardSourceKind,
    val front: String,
    val sourceQuote: String,
    val anchorStart: Int,
    val anchorEnd: Int,
    val revision: String,
  )

  /** 选区制卡：要求非空可选且落在页文本内（与 AiSelectionTask.capture 同边界）。 */
  fun fromSelection(page: Int, context: CharSequence, start: Int, end: Int): Draft {
    require(page >= 1) { "page must be one-based" }
    val selectionStart = minOf(start, end)
    val selectionEnd = maxOf(start, end)
    require(selectionStart >= 0 && selectionEnd <= context.length) { "selection is outside note page" }
    require(selectionStart < selectionEnd) { "selection must not be empty" }
    val contextText = context.toString()
    val quote = contextText.substring(selectionStart, selectionEnd)
    require(!quote.isBlank()) { "selection must contain non-whitespace text" }
    val front = quote.trim()
    require(front.isNotEmpty()) { "selection must contain non-whitespace text" }
    require(front.length <= MAX_TEXT_LENGTH) { "card front is too large" }
    return Draft(
      page = page,
      sourceKind = FlashcardSourceKind.NOTE_SELECTION,
      front = front,
      sourceQuote = quote,
      anchorStart = selectionStart,
      anchorEnd = selectionEnd,
      revision = PageNotes.revision(contextText),
    )
  }

  /**
   * 段落定位：光标 [offset] 所在的「空行分隔块」（连续非空行）的字符区间，
   * 区间不含行尾换行符。光标落在空行/无段落时返回 null。
   */
  fun paragraphAround(context: String, offset: Int): Pair<Int, Int>? {
    require(offset >= 0) { "offset must be non-negative" }
    val length = context.length
    if (length == 0) return null
    var cursor = 0
    var blockStart = -1
    var blockEnd = -1
    var found: Pair<Int, Int>? = null
    while (cursor <= length && found == null) {
      val newline = context.indexOf('\n', cursor)
      val lineEnd = if (newline < 0) length else newline
      val line = context.substring(cursor, lineEnd)
      if (line.isBlank()) {
        if (blockStart >= 0) {
          if (offset in blockStart..blockEnd) found = blockStart to blockEnd
          blockStart = -1
          blockEnd = -1
        }
      } else {
        if (blockStart < 0) blockStart = cursor
        blockEnd = lineEnd
      }
      if (newline < 0) break
      cursor = newline + 1
    }
    if (found == null && blockStart >= 0 && offset in blockStart..blockEnd) {
      found = blockStart to blockEnd
    }
    return found
  }

  /** 段落制卡入口：无选区时用光标位置取段；段内文本截断到 [MAX_TEXT_LENGTH]。 */
  fun fromParagraph(page: Int, context: String, offset: Int): Draft {
    require(page >= 1) { "page must be one-based" }
    val (start, end) = paragraphAround(context, offset)
      ?: throw IllegalArgumentException("no paragraph to capture")
    val quote = context.substring(start, end)
    require(!quote.isBlank()) { "selection must contain non-whitespace text" }
    val front = quote.trim().take(MAX_TEXT_LENGTH)
    require(front.isNotEmpty()) { "selection must contain non-whitespace text" }
    return Draft(
      page = page,
      sourceKind = FlashcardSourceKind.NOTE_PARAGRAPH,
      front = front,
      sourceQuote = quote,
      anchorStart = start,
      anchorEnd = end,
      revision = PageNotes.revision(context),
    )
  }

  /**
   * 标签输入规范化：按中英文逗号/顿号/分号/换行切分，trim、去重、丢弃超长项，
   * 超出 [MAX_TAGS] 时截断。切分符不包含空格，便于「书名 章节名」类含空格标签。
   */
  fun normalizeTags(raw: String): List<String> {
    return raw.split(',', '，', '、', ';', '；', '\n', '\r')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .filter { it.length <= MAX_TAG_LENGTH }
      .take(MAX_TAGS)
  }
}
