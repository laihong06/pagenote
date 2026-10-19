package io.pagenote.android

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewHandoffSafTest {
  @Test
  fun reviewTransitionRules() {
    val open = ReviewSafBoundary.blankRecord("1", "open")
    assertTrue(ReviewSafBoundary.canTransition(open, "applied"))
    assertTrue(ReviewSafBoundary.canTransition(open, "dismissed"))
    assertFalse(ReviewSafBoundary.canTransition(open, "open"))

    val applied = ReviewSafBoundary.blankRecord("1", "applied")
    assertFalse(ReviewSafBoundary.canTransition(applied, "open"))
    assertFalse(ReviewSafBoundary.canTransition(applied, "bogus"))
  }

  @Test
  fun handoffStaleDetection() {
    val record = HandoffSafBoundary.record(2, "s", "n1", "s1")

    assertFalse(HandoffSafBoundary.isStale(record, "n1", "s1"))
    assertTrue(HandoffSafBoundary.isStale(record, "n2", "s1"))
    assertTrue(HandoffSafBoundary.isStale(record, "n1", "s2"))
    assertFalse(HandoffSafBoundary.isStale(null, "n1", "s1"))
  }

  @Test
  fun handoffSummaryValidation() {
    assertTrue(HandoffSafBoundary.validSummary("ok"))
    assertFalse(HandoffSafBoundary.validSummary("  "))
    assertFalse(HandoffSafBoundary.validSummary("x".repeat(4001)))
  }
}
