package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReferenceLineClassifierTest {
  @Test fun straightSampleUsesReferenceLineResult() {
    val points = (0..30).map { PenPoint(it * 5f, 10f, 1f) }
    val result = ReferenceLineClassifier.classify(points)
    assertEquals(0, result.controlIndex)
    assertFalse(result.arrow)
  }

  @Test fun tooFewOrShortSamplesAreNotLines() {
    assertEquals(-1, ReferenceLineClassifier.classify(listOf(PenPoint(0f, 0f, 1f))).controlIndex)
    assertEquals(-1, ReferenceLineClassifier.classify((0..5).map { PenPoint(it.toFloat(), 0f, 1f) }).controlIndex)
  }
}
