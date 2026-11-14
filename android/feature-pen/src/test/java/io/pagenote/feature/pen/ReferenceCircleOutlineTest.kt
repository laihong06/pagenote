package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceCircleOutlineTest {
  @Test fun samplesReferenceCircleUsingDistanceDependentAngularStep() {
    // CircleTool.split: radius=100, step=max((int)(50/sqrt(100)), 2)=5.
    val points = ReferenceCircleOutline.sample(10f, 20f, 110f, 20f)
    assertEquals(74, points.size)
    assertEquals(110f, points[0].x, 0.001f)
    assertEquals(20f, points[0].y, 0.001f)
    assertEquals(10f, points[18].x, 0.001f)
    assertEquals(-80f, points[18].y, 0.001f)
    assertEquals(points[0].x, points[72].x, 0.001f)
    assertEquals(points[0].y, points[72].y, 0.001f)
  }

  @Test fun largeCircleUsesMinimumTwoDegreeStep() {
    val points = ReferenceCircleOutline.sample(0f, 0f, 2500f, 0f)
    assertEquals(182, points.size)
    assertEquals(2500f, points[180].x, 0.001f)
  }

  @Test fun zeroRadiusMatchesReferenceEmptyOutline() {
    assertEquals(emptyList<PenPoint>(), ReferenceCircleOutline.sample(2f, 3f, 2f, 3f))
  }
}
