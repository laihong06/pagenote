package io.pagenote.core.search

/** 全文检索的文档类别（移植批次 7：页文本 + 笔记 + OCR + 手写转写）。 */
enum class SearchDocumentKind(val key: String) {
  /** 阅读源页文本：PDF 提取文本或 Markdown 原文页。 */
  SOURCE("source"),

  /** 笔记页（保存后的页文本，不含未保存草稿）。 */
  NOTE("note"),

  /** OCR 识别文本（整页或区域）。 */
  OCR("ocr"),

  /** 手写转写 / 公式识别（LaTeX）落盘内容。 */
  TRANSCRIPT("transcript");

  companion object {
    val ALL: Set<SearchDocumentKind> = entries.toSet()

    /** 读侧容错：未知 key 返回 null，由调用方决定降级。 */
    fun fromKey(key: String): SearchDocumentKind? = entries.firstOrNull { it.key == key }
  }
}
