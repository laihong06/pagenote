package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceBrokenLineTest {
  @Test fun shortAndStraightInputHaveNoCorners() {
    val straight = (0..40).map { PenPoint(it * 5f, 20f, 1f) }
    assertTrue(ReferenceBrokenLine.find(straight, 15f).isEmpty())
    assertTrue(ReferenceBrokenLine.find(straight.take(2), 15f).isEmpty())
  }

  @Test fun thresholdAndDuplicateSamplesMatchReferenceRules() {
    val small = (0..10).map { PenPoint(it.toFloat(), 0f, 1f) }
    assertEquals(emptyList<Int>(), ReferenceBrokenLine.find(small, 15f))
    val duplicate = List(40) { PenPoint(100f, 100f, 1f) }
    assertTrue(ReferenceBrokenLine.find(duplicate, 15f).isEmpty())
  }
}
