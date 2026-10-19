package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PenPageStoreTest {
  private fun stroke(color: String = "#ff0000", width: Double = 2.0) =
    PenStroke(
      "pen",
      color,
      width,
      listOf(PenPoint(10f, 20f, 0.5f), PenPoint(30f, 40f, 0.7f)),
    )

  @Test
  fun strokesAreIsolatedPerPage() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
    assertTrue(store.getItems(PenSide.DOCUMENT, 2).isEmpty())
  }

  @Test
  fun sidesAreIsolated() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    assertTrue(store.getItems(PenSide.NOTE, 1).isEmpty())
  }

  @Test
  fun eraserRemovesOnlyTargetStroke() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111"))
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#222222"))
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#333333"))
    assertTrue(store.eraseAt(PenSide.DOCUMENT, 1, 1))
    assertEquals(
      listOf("#111111", "#333333"),
      store.getItems(PenSide.DOCUMENT, 1).map { it.color },
    )
  }

  @Test
  fun eraserOutOfBoundsChangesNothing() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    assertFalse(store.eraseAt(PenSide.DOCUMENT, 1, 5))
    assertFalse(store.eraseAt(PenSide.DOCUMENT, 2, 0))
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
  }

  @Test
  fun eraserDoesNotAffectOtherPages() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 2, stroke())
    assertTrue(store.eraseAt(PenSide.DOCUMENT, 1, 0))
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 2).size)
  }

  @Test
  fun clearPageOnlyClearsCurrentPage() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 2, stroke())
    assertTrue(store.clearPage(PenSide.DOCUMENT, 1))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 2).size)
    assertFalse(store.clearPage(PenSide.DOCUMENT, 1))
  }

  @Test
  fun undoRestoresErasedStrokeOnSamePage() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111"))
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#222222"))
    assertTrue(store.eraseAt(PenSide.DOCUMENT, 1, 0))
    assertTrue(store.undo(PenSide.DOCUMENT, 1))
    assertEquals(
      listOf("#111111", "#222222"),
      store.getItems(PenSide.DOCUMENT, 1).map { it.color },
    )
  }

  @Test
  fun undoDoesNotDeleteOtherPages() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 2, stroke())
    assertTrue(store.eraseAt(PenSide.DOCUMENT, 1, 0))
    assertTrue(store.undo(PenSide.DOCUMENT, 1))
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 2).size)
    // Undoing page 2 only touches page 2, never page 1.
    assertTrue(store.undo(PenSide.DOCUMENT, 2))
    assertTrue(store.getItems(PenSide.DOCUMENT, 2).isEmpty())
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
  }

  @Test
  fun redoReappliesAfterUndo() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    assertTrue(store.eraseAt(PenSide.DOCUMENT, 1, 0))
    assertTrue(store.undo(PenSide.DOCUMENT, 1))
    assertTrue(store.redo(PenSide.DOCUMENT, 1))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
  }

  @Test
  fun newEditClearsRedo() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    assertTrue(store.eraseAt(PenSide.DOCUMENT, 1, 0))
    assertTrue(store.undo(PenSide.DOCUMENT, 1))
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#00ff00"))
    assertFalse(store.canRedo(PenSide.DOCUMENT, 1))
    assertFalse(store.redo(PenSide.DOCUMENT, 1))
  }

  @Test
  fun illegalColorAndWidthRejectedOnAdd() {
    val store = PenPageStore()
    assertThrows(IllegalArgumentException::class.java) {
      store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "red"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      store.addStroke(PenSide.DOCUMENT, 1, stroke(width = 500.0))
    }
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
    assertFalse(store.canUndo(PenSide.DOCUMENT, 1))
  }

  @Test
  fun annotationsMapRoundTrip() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#00ff00"))
    store.addStroke(PenSide.NOTE, 2, stroke())
    val map = store.toAnnotationsMap()
    assertTrue(map.containsKey("document"))
    assertTrue(map.containsKey("note"))
    assertTrue(map["document"]!!.containsKey("1"))
    val firstItem = map["document"]!!["1"]!![0]
    assertEquals(
      listOf(listOf(10.0, 20.0), listOf(30.0, 40.0)),
      firstItem["points"],
    )
    val loaded = PenPageStore()
    loaded.loadAnnotationsMap(map)
    assertEquals(store.getItems(PenSide.DOCUMENT, 1), loaded.getItems(PenSide.DOCUMENT, 1))
    assertEquals(store.getItems(PenSide.NOTE, 2), loaded.getItems(PenSide.NOTE, 2))
  }

  @Test
  fun loadRejectsIllegalWidth() {
    val raw: Map<String, Any?> = mapOf(
      "document" to mapOf(
        "1" to listOf(
          mapOf(
            "type" to "pen",
            "color" to "#ff0000",
            "width" to 500.0,
            "points" to listOf(listOf(1.0, 2.0)),
          ),
        ),
      ),
      "note" to emptyMap<String, Any?>(),
    )
    assertThrows(IllegalArgumentException::class.java) {
      PenPageStore().loadAnnotationsMap(raw)
    }
  }
}
