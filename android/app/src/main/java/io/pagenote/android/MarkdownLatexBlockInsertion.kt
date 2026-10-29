package io.pagenote.android

/**
 * 公式识别 → 笔记 Markdown 块公式插入（移植批次 6）。
 *
 * 模板与桌面源码模式、[MarkdownFormulaInsertion] 完全一致
 * （`\n\n$$\n` + 正文 + `\n$$\n\n`），区别仅在于公式正文来自识别/修正结果
 * 而非当前选区；原选区内容被识别结果替换。选区与光标计算同为 UTF-16
 * 单位（与 [MarkdownFormulaInsertion.createEdit] 相同的夹取规则）。
 */
object MarkdownLatexBlockInsertion {
  private const val OPENING = "\n\n$$\n"
  private const val CLOSING = "\n$$\n\n"

  fun createEdit(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    latex: String,
  ): MarkdownFormulaEdit? {
    val body = latex.trim()
    if (body.isEmpty()) return null
    val first = selectionStart.coerceIn(0, markdown.length)
    val second = selectionEnd.coerceIn(0, markdown.length)
    val start = minOf(first, second)
    val end = maxOf(first, second)
    val bodyStart = start + OPENING.length
    return MarkdownFormulaEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = OPENING + body + CLOSING,
      selectionStart = bodyStart,
      selectionEnd = bodyStart + body.length,
    )
  }
}
