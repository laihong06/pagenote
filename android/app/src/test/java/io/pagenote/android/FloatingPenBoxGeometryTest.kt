package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPenBoxGeometryTest {
  @Test fun snapsToNearestEdgeAndClampsToParent() {
    assertEquals(PenBoxEdge.LEFT, PenBoxGeometry.nearestEdge(-20f, 250f, 60f, 160f, 900f, 700f))
    assertEquals(PenBoxEdge.RIGHT, PenBoxGeometry.nearestEdge(850f, 250f, 60f, 160f, 900f, 700f))
    assertEquals(PenBoxEdge.TOP, PenBoxGeometry.nearestEdge(400f, 3f, 160f, 60f, 900f, 700f))
    assertEquals(PenBoxEdge.BOTTOM, PenBoxGeometry.nearestEdge(400f, 650f, 160f, 60f, 900f, 700f))
  }

  @Test fun restoresFractionOnNewSizeAndClampsOversizedBoxes() {
    assertEquals(350f, PenBoxGeometry.restoreAlongEdge(.5f, 700f, 100f), .001f)
    assertEquals(0f, PenBoxGeometry.restoreAlongEdge(.5f, 50f, 100f), .001f)
    assertEquals(600f, PenBoxGeometry.restoreAlongEdge(1f, 700f, 100f), .001f)
  }

  @Test fun reorderKeepsTheSelectedItemIdentity() {
    assertEquals(listOf("marker", "pen", "pencil"), PenBoxGeometry.moveItem(listOf("pen", "pencil", "marker"), 2, 0))
    assertEquals(listOf("pen", "pencil"), PenBoxGeometry.moveItem(listOf("pen", "pencil"), -1, 1))
  }

}
