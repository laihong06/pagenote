package io.pagenote.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSchemasTest {
  @Test
  fun allLocalToolsHaveSchemas() {
    assertEquals(AgentLoop.LOCAL_TOOLS.toSet(), ToolSchemas.SCHEMAS.keys)
  }

  @Test
  fun validWriteNotePasses() {
    val check = ToolSchemas.check(
      ToolRequest("write_note", mapOf("page" to "3", "expectedRevision" to "rev")),
    )
    assertTrue(check is ToolSchemas.Check.Ok)
  }

  @Test
  fun missingRevisionRejected() {
    val check = ToolSchemas.check(ToolRequest("write_note", mapOf("page" to "3")))
    assertTrue(check is ToolSchemas.Check.Rejected)
    check as ToolSchemas.Check.Rejected
    assertTrue(check.reason.contains("expectedRevision"))
  }

  @Test
  fun badPageRejected() {
    val check = ToolSchemas.check(ToolRequest("read_page", mapOf("page" to "0")))
    assertTrue(check is ToolSchemas.Check.Rejected)
  }

  @Test
  fun unknownToolRejected() {
    val check = ToolSchemas.check(ToolRequest("rm_rf_all", emptyMap()))
    assertTrue(check is ToolSchemas.Check.Rejected)
  }


  @Test
  fun writeReviewStatusUsesIdParam() {
    val ok = ToolSchemas.check(ToolRequest("write_review_status", mapOf("id" to "r1", "status" to "applied")))
    assertTrue(ok is ToolSchemas.Check.Ok)
    val bad = ToolSchemas.check(ToolRequest("write_review_status", mapOf("reviewId" to "r1", "status" to "applied")))
    assertTrue(bad is ToolSchemas.Check.Rejected)
  }

  @Test
  fun writeHandoffRequiresPageSummaryAndRevision() {
    val ok = ToolSchemas.check(
      ToolRequest("write_handoff", mapOf("page" to "2", "summary" to "s", "expectedRevision" to "r", "sourceRevision" to "src")),
    )
    assertTrue(ok is ToolSchemas.Check.Ok)
    val bad = ToolSchemas.check(ToolRequest("write_handoff", mapOf("page" to "2", "summary" to "s")))
    assertTrue(bad is ToolSchemas.Check.Rejected)
  }
}
