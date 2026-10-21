package io.pagenote.android

import io.pagenote.core.reader.PdfTextExtractionResult
import io.pagenote.core.reader.PdfTextExtractionStatus
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultPolicy
import io.pagenote.feature.ocr.OcrTrigger

data class PageTutorSourceText(
  val text: String,
  val usedOcr: Boolean,
  val warnings: List<String> = emptyList(),
)

class PageTutorSourceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class PageTutorSourceLoader(
  private val nativeText: suspend (Int) -> PdfTextExtractionResult,
  private val searchablePdfText: suspend (Int) -> String? = { null },
  private val ocrText: suspend (Int) -> OcrResult,
  private val hasBitmap: suspend (Int) -> Boolean = { false },
) {
  suspend fun loadCurrent(pageIndex: Int): PageTutorSourceText {
    require(pageIndex >= 0) { "page index must not be negative" }
    val native = nativeText(pageIndex)
    val fallback = searchablePdfFallback(pageIndex, native)
    return loadFromNative(pageIndex, native, fallback)
  }

  suspend fun loadNeighbor(pageIndex: Int): PageTutorSourceText? {
    require(pageIndex >= 0) { "page index must not be negative" }
    val native = nativeText(pageIndex)
    val text = preferredText(native) ?: searchablePdfText(pageIndex)?.takeIf(String::isNotBlank)
    return text?.let { PageTutorSourceText(text = it, usedOcr = false) }
  }

  suspend fun loadNeighborForAgent(pageIndex: Int): PageTutorSourceText {
    require(pageIndex >= 0) { "page index must not be negative" }
    val native = nativeText(pageIndex)
    val fallback = searchablePdfFallback(pageIndex, native)
    return loadFromNative(pageIndex, native, fallback)
  }

  private suspend fun searchablePdfFallback(
    pageIndex: Int,
    native: PdfTextExtractionResult,
  ): String? = if (preferredText(native) == null) {
    searchablePdfText(pageIndex)?.takeIf(String::isNotBlank)
  } else {
    null
  }

  private suspend fun loadFromNative(
    pageIndex: Int,
    native: PdfTextExtractionResult,
    searchableFallback: String?,
  ): PageTutorSourceText {
    val sourceText = preferredText(native) ?: searchableFallback.orEmpty()
    val pageHasBitmap = sourceText.isNotBlank() &&
      sourceText.length < OcrTrigger.SPARSE_CHARS_WITH_BITMAP && hasBitmap(pageIndex)
    val shouldOcr = OcrTrigger.shouldRun(
      nativeAvailable = sourceText.isNotBlank(),
      nativeText = sourceText,
      hasBitmap = pageHasBitmap,
    )
    if (!shouldOcr) return PageTutorSourceText(text = sourceText, usedOcr = false)

    val result = ocrText(pageIndex)
    try {
      OcrResultPolicy.validate(result)
    } catch (error: IllegalArgumentException) {
      throw PageTutorSourceException("Offline OCR returned an invalid result.", error)
    }
    val text = OcrResultPolicy.textOf(result).trim()
    if (result.status !in setOf("ok", "partial") || text.isEmpty()) {
      val detail = result.warnings.filter(String::isNotBlank).joinToString(" ")
        .ifBlank { "No usable text was recognized from this page." }
      throw PageTutorSourceException("Offline OCR could not prepare page text. $detail")
    }

    val warnings = result.warnings.filter(String::isNotBlank).toMutableList()
    if (OcrResultPolicy.needsVisualReview(result)) {
      warnings += "OCR text may be inaccurate. Verify it against the original PDF page."
    }
    val includesPdfText = pageHasBitmap && sourceText.isNotBlank()
    val combinedText = if (includesPdfText) {
      "PDF text layer:\n${sourceText.trim()}\n\nOffline OCR:\n$text"
    } else {
      text
    }
    if (includesPdfText) {
      warnings += "Both PDF text and OCR text are included. Verify them against the original page."
    }
    return PageTutorSourceText(text = combinedText, usedOcr = true, warnings = warnings.distinct())
  }

  private fun preferredText(native: PdfTextExtractionResult): String? =
    native.fullText.takeIf { native.status == PdfTextExtractionStatus.AVAILABLE && it.isNotBlank() }
}
