package io.pagenote.feature.ocr

object OcrPolicy {
  const val TIMEOUT_MS = 90_000L

  fun mergedText(nativeText: String, ocrText: String): String {
    if (nativeText.isNotEmpty() && ocrText.isNotEmpty()) {
      return "[PDF/Markdown 原文]\n$nativeText\n\n[OCR 补充，需核对原图]\n$ocrText"
    }
    return nativeText.ifEmpty { ocrText }
  }
}
