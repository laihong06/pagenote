package io.pagenote.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PdfTextSelectionTest {
  @Test
  fun forwardAndReverseDragsSelectSameReadingOrderAndDesktopAnchor() {
    val page = PdfPageText(
      text = "AB\nCD",
      revision = "revision",
      pageWidth = 200.0,
      pageHeight = 400.0,
      glyphs = listOf(
        PdfTextGlyph(0, 1, PdfTextBounds(0.0, 0.0, 10.0, 10.0)),
        PdfTextGlyph(1, 2, PdfTextBounds(10.0, 0.0, 20.0, 10.0)),
        PdfTextGlyph(3, 4, PdfTextBounds(0.0, 20.0, 10.0, 30.0)),
        PdfTextGlyph(4, 5, PdfTextBounds(10.0, 20.0, 20.0, 30.0)),
      ),
    )

    val forward = PdfTextSelectionMapper.select(
      page,
      start = PdfTextPoint(15.0, 5.0),
      end = PdfTextPoint(5.0, 25.0),
    )
    val reverse = PdfTextSelectionMapper.select(
      page,
      start = PdfTextPoint(5.0, 25.0),
      end = PdfTextPoint(15.0, 5.0),
    )

    assertNotNull(forward)
    assertNotNull(reverse)
    assertEquals(1, forward!!.start)
    assertEquals(4, forward.end)
    assertEquals("B\nC", forward.quote)
    assertEquals(forward.start, reverse!!.start)
    assertEquals(forward.end, reverse.end)
    assertEquals(forward.quote, reverse.quote)
    assertEquals(2, forward.glyphBounds.size)
    assertEquals(
      PdfTextBounds(0.0, 0.0, 100.0, 150.0),
      forward.toWidth1000Bounds(pageWidth = 200.0),
    )
  }
}
