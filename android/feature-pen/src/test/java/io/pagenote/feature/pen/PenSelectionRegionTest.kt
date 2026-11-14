package io.pagenote.feature.pen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenSelectionRegionTest {
  @Test fun closedLassoContainsInteriorAndBoundaryButNotExteriorPoints() {
    val region = PenSelectionRegion.create(rectangleLasso())!!

    assertTrue(region.contains(50.0, 50.0))
    assertTrue(region.contains(10.0, 50.0))
    assertFalse(region.contains(9.0, 50.0))
    assertFalse(region.contains(Double.NaN, 50.0))
  }

  @Test fun freehandSelectionUsesTheFirstStoredSampleLikeTheReference() {
    val region = PenSelectionRegion.create(rectangleLasso())!!
    val startsInside = PenStroke("pen", "#000000", 3.0, listOf(PenPoint(20f, 20f, 1f), PenPoint(100f, 100f, 1f)))
    val startsOutsideThenEnters = PenStroke(
      "pen", "#000000", 3.0,
      listOf(PenPoint(0f, 50f, 1f), PenPoint(20f, 50f, 1f), PenPoint(40f, 50f, 1f)),
    )
    val startsInsideThenLeaves = PenStroke(
      "pen", "#000000", 3.0,
      listOf(PenPoint(20f, 20f, 1f), PenPoint(120f, 120f, 1f)),
    )

    assertTrue(region.selects(startsInside))
    assertFalse(region.selects(startsOutsideThenEnters))
    assertTrue(region.selects(startsInsideThenLeaves))
  }

  @Test fun requiresFiveSamplesAndReferenceMinimumBounds() {
    assertNull(PenSelectionRegion.create(rectangleLasso().take(4)))
    assertNull(PenSelectionRegion.create(rectangleLasso(), minimumWidth = 81.0))
    assertNotNull(PenSelectionRegion.create(rectangleLasso(), minimumWidth = 80.0, minimumHeight = 80.0))
  }

  @Test fun nativeMaterialBoundsMustBeEnclosedByLassoBounds() {
    val region = PenSelectionRegion.create(rectangleLasso())!!

    assertTrue(region.encloses(PenRect(20.0, 20.0, 80.0, 80.0)))
    assertFalse(region.encloses(PenRect(9.0, 20.0, 80.0, 80.0)))
  }

  private fun rectangleLasso() = listOf(
    PenPoint(10f, 10f, 1f),
    PenPoint(90f, 10f, 1f),
    PenPoint(90f, 90f, 1f),
    PenPoint(10f, 90f, 1f),
    PenPoint(10f, 10f, 1f),
  )
}
