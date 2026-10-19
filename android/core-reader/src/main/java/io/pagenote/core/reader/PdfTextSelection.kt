package io.pagenote.core.reader

data class PdfTextBounds(
  val left: Double,
  val top: Double,
  val right: Double,
  val bottom: Double,
) {
  val isUsable: Boolean
    get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
      right > left && bottom > top

  fun toWidth1000(pageWidth: Double): PdfTextBounds {
    require(pageWidth.isFinite() && pageWidth > 0.0) { "PDF page width must be positive and finite" }
    val scale = 1000.0 / pageWidth
    return PdfTextBounds(left * scale, top * scale, right * scale, bottom * scale)
  }

  fun union(other: PdfTextBounds) = PdfTextBounds(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
  )
}

data class PdfTextGlyph(
  val start: Int,
  val end: Int,
  val bounds: PdfTextBounds,
)

data class PdfPageText(
  val text: String,
  val revision: String,
  val pageWidth: Double,
  val pageHeight: Double,
  val glyphs: List<PdfTextGlyph>,
)

data class PdfTextPoint(val x: Double, val y: Double)

data class PdfTextSelection(
  val start: Int,
  val end: Int,
  val quote: String,
  val bounds: PdfTextBounds,
  val glyphBounds: List<PdfTextBounds>,
) {
  fun toWidth1000Bounds(pageWidth: Double) = bounds.toWidth1000(pageWidth)
}

object PdfTextSelectionMapper {
  fun select(
    page: PdfPageText,
    start: PdfTextPoint,
    end: PdfTextPoint,
  ): PdfTextSelection? {
    if (!start.x.isFinite() || !start.y.isFinite() || !end.x.isFinite() || !end.y.isFinite()) {
      return null
    }
    val glyphs = page.glyphs.filter {
      it.start >= 0 && it.start < it.end && it.end <= page.text.length && it.bounds.isUsable
    }
    if (glyphs.isEmpty()) return null

    val first = glyphs.minByOrNull { it.bounds.distanceSquaredTo(start) } ?: return null
    val last = glyphs.minByOrNull { it.bounds.distanceSquaredTo(end) } ?: return null
    val selectionStart = minOf(first.start, last.start)
    val selectionEnd = maxOf(first.end, last.end)
    if (selectionStart >= selectionEnd) return null
    val quote = page.text.substring(selectionStart, selectionEnd)
    if (quote.isBlank()) return null

    val selectedGlyphBounds = glyphs.asSequence()
      .filter { it.start < selectionEnd && it.end > selectionStart }
      .map { it.bounds }
      .toList()
    val selectedBounds = selectedGlyphBounds.reduceOrNull(PdfTextBounds::union) ?: return null
    return PdfTextSelection(selectionStart, selectionEnd, quote, selectedBounds, selectedGlyphBounds)
  }

  private fun PdfTextBounds.distanceSquaredTo(point: PdfTextPoint): Double {
    val dx = when {
      point.x < left -> left - point.x
      point.x > right -> point.x - right
      else -> 0.0
    }
    val dy = when {
      point.y < top -> top - point.y
      point.y > bottom -> point.y - bottom
      else -> 0.0
    }
    return dx * dx + dy * dy
  }
}
