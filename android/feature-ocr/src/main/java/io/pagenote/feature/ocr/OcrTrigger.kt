package io.pagenote.feature.ocr

object OcrTrigger {
  const val SPARSE_CHARS_WITH_BITMAP = 20
  private const val UTF8_REPLACEMENT_MOJIBAKE = "\u00EF\u00BF\u00BD"
  private const val MIN_SUSPICIOUS_CODE_POINTS = 2
  private const val SUSPICIOUS_CODE_POINT_RATIO = 0.10

  fun shouldRun(nativeAvailable: Boolean, nativeText: String, hasBitmap: Boolean): Boolean {
    if (!nativeAvailable) return true
    val trimmed = nativeText.trim()
    if (trimmed.isEmpty()) return true
    if (looksLikeEncodingDamage(trimmed)) return true
    if (hasBitmap && trimmed.length < SPARSE_CHARS_WITH_BITMAP) return true
    return false
  }

  private fun looksLikeEncodingDamage(text: String): Boolean {
    if ('\uFFFD' in text || UTF8_REPLACEMENT_MOJIBAKE in text) return true

    var codePointCount = 0
    var suspiciousCount = 0
    var index = 0
    while (index < text.length) {
      val codePoint = text.codePointAt(index)
      if (!Character.isWhitespace(codePoint)) {
        codePointCount++
        when (Character.getType(codePoint)) {
          Character.CONTROL.toInt(), Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt() ->
            suspiciousCount++
        }
      }
      index += Character.charCount(codePoint)
    }

    return suspiciousCount >= MIN_SUSPICIOUS_CODE_POINTS &&
      suspiciousCount.toDouble() / codePointCount >= SUSPICIOUS_CODE_POINT_RATIO
  }
}
