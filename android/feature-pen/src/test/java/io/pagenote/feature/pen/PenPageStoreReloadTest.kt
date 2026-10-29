package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** port batch 6: external single-page replace used by the handwriting-OCR conversion. */
class PenPageStoreReloadTest {
  private fun stroke(type: String = "pen") = PenStroke(
    type,
    "#ff0000",
    2.0,
    listOf(PenPoint(10f, 20f, 0.5f), PenPoint(30f, 40f, 0.7f)),
  )

  private fun rawItem(type: String): Map<String, Any?> = mapOf(
    "type" to type,
    "color" to "#ff0000",
    "width" to 2.0,
    "points" to listOf(listOf(10.0, 20.0), listOf(30.0, 40.0)),
  )

  @Test
  fun reloadKeepsOnlyFreehandItemsAndReplacesContent() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 2, stroke())

    store.reloadPage(
      PenSide.DOCUMENT,
      1,
      listOf(rawItem("pen"), rawItem("highlight"), rawItem("text"), rawItem("image")),
    )

    val reloaded = store.getItems(PenSide.DOCUMENT, 1)
    assertEquals(listOf("pen", "highlight"), reloaded.map { it.type })
    // 其他页不受影响。
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 2).size)
  }

  @Test
  fun reloadClearsThatPageUndoRedoOnly() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.addStroke(PenSide.DOCUMENT, 2, stroke())
    assertTrue(store.canUndo(PenSide.DOCUMENT, 1))
    assertTrue(store.canUndo(PenSide.DOCUMENT, 2))

    store.reloadPage(PenSide.DOCUMENT, 1, listOf(rawItem("pen")))

    assertFalse("external replace invalidates pen-local history for that page", store.canUndo(PenSide.DOCUMENT, 1))
    assertFalse(store.canRedo(PenSide.DOCUMENT, 1))
    assertTrue("other pages keep their history", store.canUndo(PenSide.DOCUMENT, 2))
  }

  @Test
  fun reloadWithEmptyListClearsThePage() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    store.reloadPage(PenSide.DOCUMENT, 1, emptyList<Any>())
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
  }

  @Test
  fun reloadRejectsInvalidItemsWithoutPartialApply() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    val before = store.getItems(PenSide.DOCUMENT, 1)
    val invalid = rawItem("pen").toMutableMap().apply { put("width", 500.0) }
    try {
      store.reloadPage(PenSide.DOCUMENT, 1, listOf(invalid))
      throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
    assertEquals(before, store.getItems(PenSide.DOCUMENT, 1))
  }

  @Test
  fun reloadFeedsSerializationRoundTripForOcrConversion() {
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke())
    val serialized = store.getItems(PenSide.DOCUMENT, 1).map { it.toAnnotationsItem() }
    store.reloadPage(PenSide.DOCUMENT, 1, serialized + rawItem("text"))
    val reloaded = store.getItems(PenSide.DOCUMENT, 1)
    assertEquals(1, reloaded.size)
    assertEquals(serialized[0], reloaded[0].toAnnotationsItem())
  }
}
