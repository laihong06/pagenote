package io.pagenote.core.reader

import kotlin.math.abs

enum class PdfTextExtractionStatus {
  AVAILABLE,
  UNAVAILABLE_FALLBACK_TO_OCR,
}

data class PdfTextExtractionResult(
  val status: PdfTextExtractionStatus,
  val fullText: String,
  val blocks: List<PdfTextBlock>,
) {
  companion object {
    fun unavailableForOcr(): PdfTextExtractionResult = PdfTextExtractionResult(
      status = PdfTextExtractionStatus.UNAVAILABLE_FALLBACK_TO_OCR,
      fullText = "",
      blocks = emptyList(),
    )
  }
}

data class PdfTextBlock(
  val text: String,
  val x: Double,
  val y: Double,
  val width: Double,
  val height: Double,
)

data class PdfTextContent(
  val text: String,
  val bounds: List<PdfTextRect>,
)

data class PdfTextRect(
  val left: Double,
  val top: Double,
  val right: Double,
  val bottom: Double,
)

object PdfTextExtractionPolicy {
  const val MIN_SUPPORTED_API = 34
  const val MIN_NATIVE_API = 35

  fun statusForApi(apiLevel: Int): PdfTextExtractionStatus =
    if (apiLevel >= MIN_SUPPORTED_API) {
      PdfTextExtractionStatus.AVAILABLE
    } else {
      PdfTextExtractionStatus.UNAVAILABLE_FALLBACK_TO_OCR
    }
}

object PdfTextMapper {
  fun extract(page: PdfPageText): PdfTextExtractionResult {
    require(page.pageWidth.isFinite() && page.pageWidth > 0.0) { "PDF page width must be positive and finite" }
    require(page.pageHeight.isFinite() && page.pageHeight > 0.0) { "PDF page height must be positive and finite" }

    val glyphs = page.glyphs.filter {
      it.start >= 0 && it.start < it.end && it.end <= page.text.length && it.bounds.isUsable
    }.sortedWith(compareBy<PdfTextGlyph>({ it.bounds.top + it.bounds.bottom }, { it.start }))
    val lines = mutableListOf<MutableList<PdfTextGlyph>>()
    glyphs.forEach { glyph ->
      val centerY = (glyph.bounds.top + glyph.bounds.bottom) / 2.0
      val line = lines.minByOrNull { candidate ->
        val bounds = candidate.map(PdfTextGlyph::bounds).reduce(PdfTextBounds::union)
        abs(centerY - (bounds.top + bounds.bottom) / 2.0)
      }?.takeIf { candidate ->
        val bounds = candidate.map(PdfTextGlyph::bounds).reduce(PdfTextBounds::union)
        val lineHeight = bounds.bottom - bounds.top
        abs(centerY - (bounds.top + bounds.bottom) / 2.0) <= maxOf(lineHeight, glyph.bounds.bottom - glyph.bounds.top) * 0.55
      }
      if (line == null) lines += mutableListOf(glyph) else line += glyph
    }

    val contents = lines.mapNotNull { line ->
      val start = line.minOf(PdfTextGlyph::start)
      val end = line.maxOf(PdfTextGlyph::end)
      val text = page.text.substring(start, end).trim()
      if (text.isEmpty()) return@mapNotNull null
      val bounds = line.map(PdfTextGlyph::bounds).reduce(PdfTextBounds::union)
      PdfTextContent(
        text = text,
        bounds = listOf(PdfTextRect(bounds.left, bounds.top, bounds.right, bounds.bottom)),
      )
    }
    return extract(page.pageWidth, page.pageHeight, contents).copy(fullText = page.text)
  }

  fun extract(
    pageWidthPt: Double,
    pageHeightPt: Double,
    contents: List<PdfTextContent>,
  ): PdfTextExtractionResult {
    require(pageWidthPt.isFinite() && pageWidthPt > 0.0) { "PDF page width must be positive and finite" }
    require(pageHeightPt.isFinite() && pageHeightPt > 0.0) { "PDF page height must be positive and finite" }

    val blocks = contents.mapNotNull { content ->
      if (content.text.isEmpty()) return@mapNotNull null

      val validBounds = content.bounds.mapNotNull { it.clipToPage(pageWidthPt, pageHeightPt) }
      if (validBounds.isEmpty()) return@mapNotNull null

      val left = validBounds.minOf { it.left }
      val top = validBounds.minOf { it.top }
      val right = validBounds.maxOf { it.right }
      val bottom = validBounds.maxOf { it.bottom }
      PdfTextBlock(
        text = content.text,
        x = left * 1000.0 / pageWidthPt,
        y = top * 1000.0 / pageHeightPt,
        width = (right - left) * 1000.0 / pageWidthPt,
        height = (bottom - top) * 1000.0 / pageHeightPt,
      )
    }

    return PdfTextExtractionResult(
      status = PdfTextExtractionStatus.AVAILABLE,
      fullText = contents.asSequence().map { it.text }.filter { it.isNotEmpty() }.joinToString("\n"),
      blocks = blocks,
    )
  }

  private fun PdfTextRect.clipToPage(pageWidthPt: Double, pageHeightPt: Double): PdfTextRect? {
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
    if (right <= left || bottom <= top) return null

    val clipped = PdfTextRect(
      left = left.coerceIn(0.0, pageWidthPt),
      top = top.coerceIn(0.0, pageHeightPt),
      right = right.coerceIn(0.0, pageWidthPt),
      bottom = bottom.coerceIn(0.0, pageHeightPt),
    )
    return clipped.takeIf { it.right > it.left && it.bottom > it.top }
  }
}
