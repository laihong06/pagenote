package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEditHistoryOrderTest {
  @Test
  fun undoAndRedoFollowTheOriginalCrossLayerEditOrder() {
    val history = ReaderEditHistoryOrder()
    val project = "project"
    history.record(project, 1, ReaderEditHistoryOrder.Layer.ANNOTATION)
    history.record(project, 1, ReaderEditHistoryOrder.Layer.PEN)
    history.record(project, 1, ReaderEditHistoryOrder.Layer.ANNOTATION)

    assertEquals(ReaderEditHistoryOrder.Layer.ANNOTATION, history.nextUndo(project, 1))
    assertTrue(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.ANNOTATION))
    assertEquals(ReaderEditHistoryOrder.Layer.PEN, history.nextUndo(project, 1))
    assertTrue(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.PEN))
    assertEquals(ReaderEditHistoryOrder.Layer.ANNOTATION, history.nextUndo(project, 1))
    assertTrue(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.ANNOTATION))
    assertNull(history.nextUndo(project, 1))

    listOf(
      ReaderEditHistoryOrder.Layer.ANNOTATION,
      ReaderEditHistoryOrder.Layer.PEN,
      ReaderEditHistoryOrder.Layer.ANNOTATION,
    ).forEach { layer ->
      assertEquals(layer, history.nextRedo(project, 1))
      assertTrue(history.completeRedo(project, 1, layer))
    }
    assertNull(history.nextRedo(project, 1))
  }

  @Test
  fun aNewEditDropsRedoAndPagesAreIsolated() {
    val history = ReaderEditHistoryOrder()
    val project = "project"
    history.record(project, 1, ReaderEditHistoryOrder.Layer.PEN)
    assertTrue(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.PEN))
    history.record(project, 1, ReaderEditHistoryOrder.Layer.ANNOTATION)

    assertNull(history.nextRedo(project, 1))
    assertNull(history.nextUndo(project, 2))
    assertNull(history.nextUndo("other-project", 1))
    assertFalse(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.PEN))
  }

  @Test
  fun pageReorderingMovesHistoryWithItsPage() {
    val history = ReaderEditHistoryOrder()
    history.record("project", 3, ReaderEditHistoryOrder.Layer.ANNOTATION)
    history.remapPages("project", mapOf(3 to 5))

    assertNull(history.nextUndo("project", 3))
    assertEquals(ReaderEditHistoryOrder.Layer.ANNOTATION, history.nextUndo("project", 5))
  }

  @Test
  fun groupedCrossLayerGestureIsOneUndoAndRedoEntry() {
    val history = ReaderEditHistoryOrder()
    val project = "project"
    history.record(project, 1, ReaderEditHistoryOrder.Layer.PEN)
    val groupId = history.beginGroup(project, 1)
    history.recordInGroup(project, 1, groupId, ReaderEditHistoryOrder.Layer.PEN)
    history.recordInGroup(project, 1, groupId, ReaderEditHistoryOrder.Layer.ANNOTATION)

    assertNull(history.nextUndo(project, 1))
    assertTrue(history.endGroup(project, 1, groupId))
    assertEquals(ReaderEditHistoryOrder.Layer.COMPOSITE, history.nextUndo(project, 1))
    assertEquals(
      listOf(ReaderEditHistoryOrder.Layer.PEN, ReaderEditHistoryOrder.Layer.ANNOTATION),
      history.layersForUndo(project, 1),
    )
    assertTrue(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.COMPOSITE))
    assertEquals(ReaderEditHistoryOrder.Layer.COMPOSITE, history.nextRedo(project, 1))
    assertEquals(
      listOf(ReaderEditHistoryOrder.Layer.PEN, ReaderEditHistoryOrder.Layer.ANNOTATION),
      history.layersForRedo(project, 1),
    )
    assertTrue(history.completeRedo(project, 1, ReaderEditHistoryOrder.Layer.COMPOSITE))
    assertEquals(ReaderEditHistoryOrder.Layer.COMPOSITE, history.nextUndo(project, 1))
  }

  @Test
  fun laterEditsStayAfterPendingGestureGroup() {
    val history = ReaderEditHistoryOrder()
    val project = "project"
    val groupId = history.beginGroup(project, 1)
    history.recordInGroup(project, 1, groupId, ReaderEditHistoryOrder.Layer.PEN)
    history.record(project, 1, ReaderEditHistoryOrder.Layer.PEN)
    history.recordInGroup(project, 1, groupId, ReaderEditHistoryOrder.Layer.ANNOTATION)
    assertTrue(history.endGroup(project, 1, groupId))

    assertEquals(ReaderEditHistoryOrder.Layer.PEN, history.nextUndo(project, 1))
    assertTrue(history.completeUndo(project, 1, ReaderEditHistoryOrder.Layer.PEN))
    assertEquals(ReaderEditHistoryOrder.Layer.COMPOSITE, history.nextUndo(project, 1))
    assertEquals(
      listOf(ReaderEditHistoryOrder.Layer.PEN, ReaderEditHistoryOrder.Layer.ANNOTATION),
      history.layersForUndo(project, 1),
    )
  }
}
