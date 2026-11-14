package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceBrokenLineDiffTest {
  @Test fun matchesExtractedJavaAcrossTrajectories() {
    val random = java.util.Random(4201)
    var casesWithCorners = 0
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
      val threshold = 2f + random.nextFloat() * 30f
      val original = points.map { ReferenceBrokenLineOriginal.Point(it.x.toDouble(), it.y.toDouble()) }
      val expected = ReferenceBrokenLineOriginal.find(original, threshold)
      if (expected.isNotEmpty()) casesWithCorners++
      assertEquals(expected, ReferenceBrokenLine.find(points, threshold))
    }
    assertTrue("exercise actual corner detection", casesWithCorners > 0)
  }
}
