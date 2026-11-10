package io.pagenote.android

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentNoteReconciliationTest {
  @Test fun cleanPageShowsTheAgentUpdate() {
    val result = reconcileAgentNote("original", "original", "agent result", "old revision")
    assertEquals("agent result", result.draft)
    assertEquals(PageNotes.revision("agent result"), result.baseRevision)
    assertFalse(result.conflict)
  }

  @Test fun concurrentEditingRetainsDraftAndItsOriginalRevision() {
    val result = reconcileAgentNote("original", "my new edits", "agent result", "old revision")
    assertEquals("my new edits", result.draft)
    assertEquals("old revision", result.baseRevision)
    assertTrue(result.conflict)
  }

  @Test fun editsOnAnotherPageRemainSaveable() {
    val result = reconcileAgentNote("original", "my new edits", "original", "old revision")
    assertEquals("my new edits", result.draft)
    assertEquals("old revision", result.baseRevision)
    assertFalse(result.conflict)
  }
}
