package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfPageMergePlanTest {
  @Test
  fun parsesRangesAndSinglePagesAscendingAndDeduplicated() {
    assertEquals(listOf(1, 2, 3, 5), PdfPageMergePlan.parse("1-3,5", pageCount = 10))
    assertEquals(listOf(1, 2), PdfPageMergePlan.parse("2,1-2", pageCount = 10))
    assertEquals(listOf(4), PdfPageMergePlan.parse(" 4 ", pageCount = 10))
    assertEquals(listOf(1, 2, 3), PdfPageMergePlan.parse("3,2,1", pageCount = 10))
    assertEquals(listOf(10), PdfPageMergePlan.parse("10", pageCount = 10))
  }

  @Test
  fun toleratesChineseSeparatorsAndWhitespace() {
    assertEquals(listOf(1, 2, 4), PdfPageMergePlan.parse("1-2，4", pageCount = 10))
    assertEquals(listOf(1, 2, 4), PdfPageMergePlan.parse("1-2、4", pageCount = 10))
  }

  @Test
  fun rejectsEmptyMalformedOutOfRangeAndReversedSelections() {
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("", pageCount = 10)
    }
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("  ", pageCount = 10)
    }
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("1,,2", pageCount = 10)
    }
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("1a", pageCount = 10)
    }
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("0", pageCount = 10)
    }
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("1-11", pageCount = 10)
    }
    assertThrows(PdfPageMergePlan.InvalidPageRange::class.java) {
      PdfPageMergePlan.parse("3-1", pageCount = 10)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PdfPageMergePlan.parse("1", pageCount = 0)
    }
  }
}
