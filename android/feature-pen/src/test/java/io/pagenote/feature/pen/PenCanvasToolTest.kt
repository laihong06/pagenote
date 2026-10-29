package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PenCanvasToolTest {
  @Test fun ballpointUsesPortablePenItemsWithConstantPressure() {
    assertEquals("pen", PenCanvasTool.BALLPOINT.persistedType)
    assertEquals(1f, PenCanvasTool.BALLPOINT.pointPressure(0.2f))
    assertEquals(0.2f, PenCanvasTool.PEN.pointPressure(0.2f))
    assertEquals("highlight", PenCanvasTool.HIGHLIGHTER.persistedType)
  }

  @Test fun laserNeverProducesAPersistedStroke() {
    assertNull(PenCanvasTool.LASER.persistedType)
    assertNull(PenCanvasTool.SELECT.persistedType)
    assertNull(PenCanvasTool.ERASER.persistedType)
  }
}
