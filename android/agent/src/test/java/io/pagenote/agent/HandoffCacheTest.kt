package io.pagenote.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffCacheTest {
  @Test
  fun missingHandoffIsNotStale() {
    val view = HandoffCache.view(emptyMap(), 3, "n1", "s1")
    assertNull(view.entry)
    assertFalse(view.stale)
  }

  @Test
  fun matchingRevisionsAreFresh() {
    val entries = HandoffCache.write(emptyMap(), 3, "done page 3", "n1", "s1", "t")
    val view = HandoffCache.view(entries, 3, "n1", "s1")
    assertFalse(view.stale)
    assertEquals("done page 3", view.entry?.summary)
  }

  @Test
  fun noteRevisionMismatchIsStale() {
    val entries = HandoffCache.write(emptyMap(), 3, "old", "n1", "s1", "t")
    val view = HandoffCache.view(entries, 3, "n2", "s1")
    assertTrue(view.stale)
  }

  @Test
  fun sourceRevisionMismatchIsStale() {
    val entries = HandoffCache.write(emptyMap(), 3, "old", "n1", "s1", "t")
    val view = HandoffCache.view(entries, 3, "n1", "s2")
    assertTrue(view.stale)
  }

  @Test(expected = IllegalArgumentException::class)
  fun emptySummaryRejected() {
    HandoffCache.write(emptyMap(), 3, "  ", "n1", "s1", "t")
  }
}
