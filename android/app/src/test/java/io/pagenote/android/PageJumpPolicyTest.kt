package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageJumpPolicyTest {
  @Test
  fun convertsOneBasedFirstAndLastPagesToZeroBasedTargets() {
    assertEquals(0, PageJumpPolicy.parseTarget("1", totalPages = 4))
    assertEquals(3, PageJumpPolicy.parseTarget(" 4 ", totalPages = 4))
  }

  @Test
  fun rejectsBlankMalformedAndOutOfRangeInput() {
    listOf("", "  ", "0", "-1", "5", "1.0", "page").forEach { input ->
      assertNull("input '$input' must not select a page", PageJumpPolicy.parseTarget(input, totalPages = 4))
    }
  }

  @Test
  fun rejectsInputWhenThereAreNoPages() {
    assertNull(PageJumpPolicy.parseTarget("1", totalPages = 0))
  }
}
