package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentPageRangeTest {
  @Test
  fun parsesSinglePage() {
    assertEquals(listOf(3), AgentPageRange.parse("3", totalPages = 5))
  }

  @Test
  fun parsesRangeAndList() {
    assertEquals(listOf(2, 3, 4), AgentPageRange.parse("2-4", totalPages = 5))
    assertEquals(listOf(1, 3, 5), AgentPageRange.parse("1, 3,5", totalPages = 5))
  }

  @Test
  fun dedupesAndKeepsOrder() {
    assertEquals(listOf(2, 1), AgentPageRange.parse("2,1,2", totalPages = 5))
  }

  @Test
  fun rejectsBadRange() {
    listOf("", "0", "6", "3-2", "a", "1-6", "2,,3").forEach { text ->
      try {
        AgentPageRange.parse(text, totalPages = 5)
        throw AssertionError("expected rejection: $text")
      } catch (_: IllegalArgumentException) {
      }
    }
  }
}
