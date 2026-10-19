package io.pagenote.android

data class MarkdownImageInsertionEdit(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val cursor: Int,
)

object MarkdownImageInsertion {
  private val assetNamePattern =
    Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpe?g|gif|webp)")

  fun createReference(altText: String, assetName: String): String {
    require(assetNamePattern.matches(assetName)) { "Invalid project image asset name" }
    val safeAlt = altText
      .map { if (it.isISOControl()) ' ' else it }
      .joinToString("")
      .trim()
      .replace("\\", "\\\\")
      .replace("[", "\\[")
      .replace("]", "\\]")
    return "![$safeAlt](assets/$assetName)"
  }

  fun createEdit(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    reference: String,
  ): MarkdownImageInsertionEdit {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, markdown.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, markdown.length)
    val replacement = "\n$reference\n"
    return MarkdownImageInsertionEdit(
      replaceStart = start,
      replaceEnd = end,
      replacement = replacement,
      cursor = start + replacement.length,
    )
  }
}
