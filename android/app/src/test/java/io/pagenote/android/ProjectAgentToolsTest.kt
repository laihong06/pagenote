package io.pagenote.android

import io.pagenote.agent.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAgentToolsTest {
  @Test
  fun readPageArgsValidation() {
    val valid = ProjectAgentTools.parsePage(mapOf("page" to "3"), currentPage = 1)
    assertEquals(3, valid)

    val fallback = ProjectAgentTools.parsePage(emptyMap(), currentPage = 2)
    assertEquals(2, fallback)

    try {
      ProjectAgentTools.parsePage(mapOf("page" to "0"), currentPage = 1)
      throw AssertionError("expected bad page")
    } catch (_: IllegalArgumentException) {
    }
    try {
      ProjectAgentTools.parsePage(mapOf("page" to "abc"), currentPage = 1)
      throw AssertionError("expected bad page")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun reviewStatusFilter() {
    assertTrue(ProjectAgentTools.validReviewStatus(null))
    assertTrue(ProjectAgentTools.validReviewStatus("open"))
    assertFalse(ProjectAgentTools.validReviewStatus("bogus"))
  }

  @Test
  fun toolNamesAreRegistered() {
    assertTrue(ProjectAgentTools.READ_TOOLS.contains("read_page"))
    assertTrue(ProjectAgentTools.READ_TOOLS.contains("read_review"))
    assertTrue(ProjectAgentTools.READ_TOOLS.contains("read_handoff"))
  }

  @Test
  fun writeToolsAreRegistered() {
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("write_note"))
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("patch_note"))
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("write_review_status"))
    assertTrue(ProjectAgentTools.WRITE_TOOLS.contains("write_handoff"))
  }

  @Test
  fun patchArgsValidation() {
    val parsed = ProjectAgentTools.parsePatch(
      mapOf("start" to "5", "end" to "10", "replacement" to "hi"),
      pageLength = 20,
    )
    assertEquals(5, parsed.start)
    assertEquals(10, parsed.end)
    assertEquals("hi", parsed.replacement)

    try {
      ProjectAgentTools.parsePatch(mapOf("start" to "-1", "replacement" to "x"), pageLength = 20)
      throw AssertionError("expected bad patch")
    } catch (_: IllegalArgumentException) {
    }
    try {
      ProjectAgentTools.parsePatch(mapOf("start" to "25", "replacement" to "x"), pageLength = 20)
      throw AssertionError("expected bad patch")
    } catch (_: IllegalArgumentException) {
    }
    try {
      ProjectAgentTools.parsePatch(mapOf("start" to "5"), pageLength = 20)
      throw AssertionError("expected missing replacement")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun reviewListFormattingBoundsPreviewsAndOmitsFullContext() {
    val records = (1..23).map { index ->
      reviewRecord(
        id = "review-$index",
        instruction = "i".repeat(180),
        quote = "😀".repeat(200),
      )
    }

    val summaries = Json.parseToJsonElement(
      ProjectAgentTools.formatReviewSummaries(records),
    ).jsonArray

    assertEquals(20, summaries.size)
    val first = summaries.first().jsonObject
    assertEquals("review-1", first["id"]?.jsonPrimitive?.content)
    assertEquals("note", first["side"]?.jsonPrimitive?.content)
    assertEquals("selection", first["kind"]?.jsonPrimitive?.content)
    assertEquals("3", first["page"]?.jsonPrimitive?.content)
    assertEquals("open", first["status"]?.jsonPrimitive?.content)
    assertEquals("captured-revision", first["revision"]?.jsonPrimitive?.content)
    val instruction = first["instruction"]!!.jsonPrimitive.content
    val quote = first["quote"]!!.jsonPrimitive.content
    assertEquals(160, instruction.length)
    assertTrue(instruction.endsWith("...[truncated]"))
    assertEquals(320, quote.length)
    assertTrue(quote.endsWith("...[truncated]"))
    assertFalse("context" in first)
    assertFalse("anchor" in first)
  }

  @Test
  fun reviewDetailFormattingPreservesEveryStoredField() {
    val record = reviewRecord(
      id = "review-detail",
      instruction = "Check this passage",
      quote = "exact quote",
    )

    val detail = Json.parseToJsonElement(
      ProjectAgentTools.formatReviewDetail(record),
    )

    assertEquals(record, detail)
    assertEquals("full page context", (detail as JsonObject)["context"]?.jsonPrimitive?.content)
    assertEquals("captured-revision", detail["revision"]?.jsonPrimitive?.content)
    assertEquals(record["anchor"], detail["anchor"])
  }

  @Test
  fun reviewStatusSuccessIncludesRetainedRecoveryCopyName() {
    assertEquals(
      "review=review-1 status=dismissed recoveryCopy=pagenote-recovery-1.json",
      ProjectAgentTools.formatReviewStatusSuccess(
        id = "review-1",
        status = "dismissed",
        recoveryCopyName = "pagenote-recovery-1.json",
      ),
    )
  }

  @Test
  fun reviewDetailFormattingRejectsJsonOverEightMiBUtf8() {
    val record = buildJsonObject {
      put("quote", JsonPrimitive("界".repeat(2_800_000)))
    }

    val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      ProjectAgentTools.formatReviewDetail(record)
    }

    assertTrue(error.message.orEmpty().contains("8 MiB"))
  }

  @Test
  fun reviewListNullsMetadataOutsideSpecBounds() {
    val record = buildJsonObject {
      put("id", JsonPrimitive("i".repeat(129)))
      put("side", JsonPrimitive("s".repeat(17)))
      put("kind", JsonPrimitive("k".repeat(17)))
      put("page", JsonPrimitive(10_000_000_000L))
      put("status", JsonPrimitive("t".repeat(17)))
      put("revision", JsonPrimitive("r".repeat(129)))
    }

    val summary = Json.parseToJsonElement(
      ProjectAgentTools.formatReviewSummaries(listOf(record)),
    ).jsonArray.single().jsonObject

    listOf("id", "side", "kind", "page", "status", "revision").forEach { field ->
      assertEquals("$field should be null when outside its SPEC bound", kotlinx.serialization.json.JsonNull, summary[field])
    }
  }

  @Test
  fun matchingChangedWriteAuthorizesItsReview() {
    val evidence = requireNotNull(
      ProjectAgentTools.rewriteEvidenceFrom(
        mapOf("page" to "3", "expectedRevision" to "captured-revision"),
        ToolResult(ok = true, touchedPage = 3, updatedPage = 3),
      ),
    )

    assertTrue(
      ProjectReviewStore.authorizesApplied(
        reviewRecord(),
        listOf(ProjectReviewStore.ApplyExpectation(evidence.page, evidence.expectedRevision)),
      ),
    )

    val documentReview = JsonObject(reviewRecord() + mapOf(
      "side" to JsonPrimitive("document"),
      "revision" to JsonPrimitive("pdf-page-revision"),
      "targetNoteRevision" to JsonPrimitive("captured-revision"),
    ))
    assertTrue(
      ProjectReviewStore.authorizesApplied(
        documentReview,
        listOf(ProjectReviewStore.ApplyExpectation(evidence.page, evidence.expectedRevision)),
      ),
    )
    assertFalse(
      ProjectReviewStore.authorizesApplied(
        documentReview,
        listOf(ProjectReviewStore.ApplyExpectation(evidence.page, "pdf-page-revision")),
      ),
    )
  }

  @Test
  fun appliedAuthorizationRejectsWrongPageStaleOrMissingRevision() {
    val wrongPage = requireNotNull(
      ProjectAgentTools.rewriteEvidenceFrom(
        mapOf("page" to "4", "expectedRevision" to "captured-revision"),
        ToolResult(ok = true, updatedPage = 4),
      ),
    )
    val staleRevision = requireNotNull(
      ProjectAgentTools.rewriteEvidenceFrom(
        mapOf("page" to "3", "expectedRevision" to "stale-revision"),
        ToolResult(ok = true, updatedPage = 3),
      ),
    )

    assertFalse(
      ProjectReviewStore.authorizesApplied(
        reviewRecord(),
        listOf(ProjectReviewStore.ApplyExpectation(wrongPage.page, wrongPage.expectedRevision)),
      ),
    )
    assertFalse(
      ProjectReviewStore.authorizesApplied(
        reviewRecord(),
        listOf(ProjectReviewStore.ApplyExpectation(staleRevision.page, staleRevision.expectedRevision)),
      ),
    )
    assertFalse(
      ProjectReviewStore.authorizesApplied(
        reviewRecord(revision = null),
        listOf(ProjectReviewStore.ApplyExpectation(staleRevision.page, staleRevision.expectedRevision)),
      ),
    )
    assertFalse(
      ProjectReviewStore.authorizesApplied(
        reviewRecord(),
        listOfNotNull(
          ProjectAgentTools.rewriteEvidenceFrom(
            mapOf("page" to "3"),
            ToolResult(ok = true, updatedPage = 3),
          ),
        ).map { ProjectReviewStore.ApplyExpectation(it.page, it.expectedRevision) },
      ),
    )
  }

  @Test
  fun noOpFailedAndUnrelatedWritesCannotAuthorizeApply() {
    val noOp = ProjectAgentTools.rewriteEvidenceFrom(
      mapOf("page" to "3", "expectedRevision" to "captured-revision"),
      ToolResult(ok = true, touchedPage = 3),
    )
    val failed = ProjectAgentTools.rewriteEvidenceFrom(
      mapOf("page" to "3", "expectedRevision" to "captured-revision"),
      ToolResult(ok = false, touchedPage = 3, updatedPage = 3),
    )
    val unrelatedPageWrite = requireNotNull(
      ProjectAgentTools.rewriteEvidenceFrom(
        mapOf("page" to "2", "expectedRevision" to "other-revision"),
        ToolResult(ok = true, updatedPage = 2),
      ),
    )

    assertEquals(null, noOp)
    assertEquals(null, failed)
    assertFalse(
      ProjectReviewStore.authorizesApplied(
        reviewRecord(),
        listOf(ProjectReviewStore.ApplyExpectation(unrelatedPageWrite.page, unrelatedPageWrite.expectedRevision)),
      ),
    )
  }

  private fun reviewRecord(
    id: String = "review-3",
    instruction: String = "Review this passage",
    quote: String = "selected passage",
    page: Int = 3,
    revision: String? = "captured-revision",
  ) = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("side", JsonPrimitive("note"))
    put("kind", JsonPrimitive("selection"))
    put("page", JsonPrimitive(page))
    put("status", JsonPrimitive("open"))
    put("revision", revision?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
    put("instruction", JsonPrimitive(instruction))
    put("quote", JsonPrimitive(quote))
    put("context", JsonPrimitive("full page context"))
    put("anchor", buildJsonObject {
      put("coordinateSystem", JsonPrimitive("markdown-source-utf16"))
      put("start", JsonPrimitive(2))
      put("end", JsonPrimitive(18))
    })
    put("desktopOnly", JsonPrimitive("preserved"))
  }
}
