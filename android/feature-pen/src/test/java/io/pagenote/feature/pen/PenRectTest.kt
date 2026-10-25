package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 1: document-space rect intersection and the rect eraser. */
class PenRectTest {
  private fun stroke(
    type: String = "pen",
    points: List<PenPoint> = listOf(PenPoint(10f, 20f, 1f), PenPoint(30f, 40f, 1f)),
  ) = PenStroke(type, "#185abd", 2.0, points)

  @Test
  fun rectNormalizesAnyCornerOrderAndTreatsEdgesInclusive() {
    val rect = PenRect.ofCorners(50.0, 60.0, 10.0, 20.0)
    assertEquals(PenRect(10.0, 20.0, 50.0, 60.0), rect.normalized())
    assertTrue(PenRectHit.pointInside(10.0, 20.0, rect))
    assertTrue(PenRectHit.pointInside(50.0, 60.0, rect))
    assertFalse(PenRectHit.pointInside(50.5, 60.0, rect))
    assertTrue(rect.overlaps(PenRect(50.0, 60.0, 100.0, 200.0)))
    assertFalse(rect.overlaps(PenRect(51.0, 61.0, 100.0, 200.0)))
    assertThrows(IllegalArgumentException::class.java) { PenRect(Double.NaN, 0.0, 1.0, 1.0) }
  }

  @Test
  fun polylineHitsCoverInsidePointsAndCrossingSegments() {
    val inside = stroke(points = listOf(PenPoint(10f, 10f, 1f), PenPoint(20f, 20f, 1f)))
    val crossing = stroke(
      points = listOf(PenPoint(-100f, 25f, 1f), PenPoint(600f, 25f, 1f)),
    )
    val nearby = stroke(points = listOf(PenPoint(100f, 200f, 1f), PenPoint(300f, 400f, 1f)))
    val eraser = PenRect(0.0, 0.0, 50.0, 50.0)

    assertTrue(PenRectHit.strokeIntersects(inside, eraser))
    // No point inside, but the segment crosses the rect.
    assertTrue(PenRectHit.strokeIntersects(crossing, eraser))
    assertFalse(PenRectHit.strokeIntersects(nearby, eraser))
  }

  @Test
  fun lineAndArrowUseTheStartEndSegment() {
    val eraser = PenRect(0.0, 0.0, 50.0, 50.0)
    val crossing = listOf(PenPoint(-100f, 25f, 1f), PenPoint(600f, 25f, 1f))
    val away = listOf(PenPoint(100f, 100f, 1f), PenPoint(600f, 600f, 1f))

    assertTrue(PenRectHit.strokeIntersects(stroke("line", crossing), eraser))
    assertTrue(PenRectHit.strokeIntersects(stroke("arrow", crossing), eraser))
    assertFalse(PenRectHit.strokeIntersects(stroke("line", away), eraser))
    assertFalse(PenRectHit.strokeIntersects(stroke("arrow", away), eraser))
  }

  @Test
  fun rectOutlineHitsItsBorderNotItsInterior() {
    val rectStroke = stroke(
      "rect",
      listOf(PenPoint(0f, 0f, 1f), PenPoint(100f, 100f, 1f)),
    )
    val interiorEraser = PenRect(40.0, 40.0, 60.0, 60.0)
    val borderEraser = PenRect(90.0, 0.0, 110.0, 20.0)

    // Fully interior eraser misses the drawn border.
    assertFalse(PenRectHit.strokeIntersects(rectStroke, interiorEraser))
    assertTrue(PenRectHit.strokeIntersects(rectStroke, borderEraser))
  }

  @Test
  fun areaTypesUseBoundingBoxOverlap() {
    val eraser = PenRect(10.0, 10.0, 20.0, 20.0)
    val bbox = listOf(PenPoint(0f, 0f, 1f), PenPoint(100f, 100f, 1f))
    val away = listOf(PenPoint(500f, 500f, 1f), PenPoint(900f, 900f, 1f))

    listOf("ellipse", "image", "html").forEach { type ->
      assertTrue(PenRectHit.strokeIntersects(stroke(type, bbox), eraser))
      assertFalse(PenRectHit.strokeIntersects(stroke(type, away), eraser))
    }
  }

  @Test
  fun textUsesItsAnchorPoint() {
    val text = stroke("text", listOf(PenPoint(25f, 25f, 1f)))
    assertTrue(PenRectHit.strokeIntersects(text, PenRect(0.0, 0.0, 50.0, 50.0)))
    assertFalse(PenRectHit.strokeIntersects(text, PenRect(26.0, 26.0, 50.0, 50.0)))
  }

  @Test
  fun storeErasesIntersectingStrokesAsOneBatchWithUndoRedo() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke(points = listOf(PenPoint(10f, 10f, 1f), PenPoint(20f, 20f, 1f))))
    store.addStroke(PenSide.DOCUMENT, 1, stroke(points = listOf(PenPoint(100f, 200f, 1f), PenPoint(300f, 400f, 1f))))
    store.addStroke(PenSide.DOCUMENT, 1, stroke(points = listOf(PenPoint(-100f, 25f, 1f), PenPoint(600f, 25f, 1f))))
    store.addStroke(PenSide.DOCUMENT, 2, stroke(points = listOf(PenPoint(10f, 10f, 1f), PenPoint(20f, 20f, 1f))))

    val removed = store.eraseInRect(PenSide.DOCUMENT, 1, PenRect(0.0, 0.0, 50.0, 50.0))

    assertEquals(listOf(0, 2), removed)
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 2).size)
    assertTrue(store.undo(PenSide.DOCUMENT, 1))
    assertEquals(3, store.getItems(PenSide.DOCUMENT, 1).size)
    assertTrue(store.redo(PenSide.DOCUMENT, 1))
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
  }

  @Test
  fun storeRectEraseWithoutHitsChangesNothing() {
    val store = PenPageStore()
    // Load (not add) so the built-in history starts empty.
    store.loadAnnotationsMap(
      mapOf(
        "document" to mapOf(
          "1" to listOf(stroke().toAnnotationsItem()),
        ),
      ),
    )
    assertFalse(store.canUndo(PenSide.DOCUMENT, 1))

    assertTrue(store.eraseInRect(PenSide.DOCUMENT, 1, PenRect(500.0, 500.0, 600.0, 600.0)).isEmpty())
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
    assertFalse(store.canUndo(PenSide.DOCUMENT, 1))
    // Missing page is a no-op too.
    assertTrue(store.eraseInRect(PenSide.NOTE, 7, PenRect(0.0, 0.0, 1.0, 1.0)).isEmpty())
  }

  @Test
  fun storeRectEraseAcceptsAnyCornerOrder() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke(points = listOf(PenPoint(10f, 10f, 1f), PenPoint(20f, 20f, 1f))))

    val removed = store.eraseInRect(PenSide.DOCUMENT, 1, PenRect(50.0, 50.0, 0.0, 0.0))

    assertEquals(listOf(0), removed)
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
  }
}
