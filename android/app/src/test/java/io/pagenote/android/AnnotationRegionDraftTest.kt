package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationRegionDraftTest {
  @Test
  fun buildsNormalizedRect() {
    val item = AnnotationRegionDraft.toItem(300.0, 200.0, 100.0, 50.0, "#dc3545", 3.0, "重点")

    assertEquals("rect", item["type"])
    assertEquals(listOf(listOf(100.0, 50.0), listOf(300.0, 200.0)), item["points"])
    assertEquals("重点", item["text"])
  }

  @Test
  fun rejectsZeroArea() {
    try {
      AnnotationRegionDraft.toItem(10.0, 10.0, 10.0, 10.0, "#dc3545", 3.0, "x")
      throw AssertionError("expected zero-area rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun blankTextIsAllowed() {
    val item = AnnotationRegionDraft.toItem(0.0, 0.0, 200.0, 120.0, "#dc3545", 3.0, "  ")

    assertEquals("", item["text"])
  }
}
