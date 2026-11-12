package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceLineDiffTest {
  @Test fun matchesReferenceJavaAndSmaliOnSyntheticPaths() {
    val random = java.util.Random(8209)
    var straight = 0
    var arrows = 0
    repeat(10_000) {
      var x = 0f
      var y = 0f
      var dx = 1f + random.nextFloat() * 8f
      var dy = 1f + random.nextFloat() * 8f
      val points = List(3 + random.nextInt(140)) {
        if (random.nextFloat() < .08f) {
          dx = random.nextFloat() * 16f - 8f
          dy = random.nextFloat() * 16f - 8f
        }
        if (random.nextFloat() > .08f) { x += dx; y += dy }
        PenPoint(x, y, 1f)
      }
      val source = points.map { ReferenceLineOriginal.Point(it.x.toDouble(), it.y.toDouble()) }
      val arrow = ReferenceLineOriginal.ArrowInfo()
      val expected = ReferenceLineOriginal.isLine(source, arrow)
      val actual = ReferenceLineClassifier.classify(points)
      if (expected == 0) straight++
      if (arrow.arrow) arrows++
      assertEquals(expected, actual.controlIndex)
      assertEquals(arrow.arrow, actual.arrow)
      assertEquals(arrow.arrowPosition, actual.arrowPosition)
    }
    assertTrue("exercise straight cases", straight > 0)
    assertTrue("exercise arrow cases", arrows > 0)
  }
}
