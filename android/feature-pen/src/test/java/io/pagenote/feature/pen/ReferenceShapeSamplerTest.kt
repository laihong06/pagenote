package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceShapeSamplerTest {
  @Test fun movementUsesSquaredScreenDistanceAndStrictThreshold() {
    val sampler = ReferenceShapeSampler()
    sampler.begin(100f, 100f)
    assertFalse(sampler.move(103f, 104f, 25f))
    assertTrue(sampler.move(109f, 104f, 25f))
    assertEquals(listOf(109f to 104f), sampler.points())
  }

  @Test fun longStationaryRunSuppressesOneSampleThenRecovers() {
    val sampler = ReferenceShapeSampler()
    sampler.begin(0f, 0f)
    repeat(11) { assertFalse(sampler.move(0f, 0f, 5f)) }
    assertFalse(sampler.move(20f, 0f, 5f))
    assertTrue(sampler.move(20f, 0f, 5f))
    assertEquals(listOf(20f to 0f), sampler.points())
  }

  @Test fun newGestureClearsSampledCoordinates() {
    val sampler = ReferenceShapeSampler()
    sampler.begin(0f, 0f)
    assertTrue(sampler.move(10f, 0f, 5f))
    sampler.begin(30f, 30f)
    assertTrue(sampler.points().isEmpty())
    assertTrue(sampler.move(40f, 30f, 5f))
    assertEquals(listOf(40f to 30f), sampler.points())
  }
}
