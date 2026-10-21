package io.pagenote.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentLoopTest {
  @Test
  fun toolsStayConverged() {
    assertEquals(
      setOf(
        "read_page",
        "write_note",
        "patch_note",
        "read_review",
        "write_review_status",
        "read_handoff",
        "write_handoff",
      ),
      AgentLoop.LOCAL_TOOLS.toSet(),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun badMaxStepsRejected() {
    AgentLoop.validate(AgentTask("doc", 1, "teach", null, maxSteps = 99))
  }
}
