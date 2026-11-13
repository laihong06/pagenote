package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderActionStackTest {
  private fun edit(id: String, layer: ReaderActionStack.Layer) = ReaderEditAction.Single(id, layer)

  @Test
  fun interleavedActionsUndoAndRedoInOrderAndBranchDropsRedoSuffix() {
    val history = ReaderActionStack()
    val key = "project" to 1
    history.record(key.first, key.second, edit("shape-1", ReaderActionStack.Layer.ANNOTATION))
    history.record(key.first, key.second, edit("ink-1", ReaderActionStack.Layer.PEN))
    history.record(key.first, key.second, edit("shape-2", ReaderActionStack.Layer.ANNOTATION))

    listOf("shape-2", "ink-1", "shape-1").forEach { expectedId ->
      val ticket = history.beginUndo(key.first, key.second) ?: error("missing undo action")
      assertEquals(expectedId, ticket.action.id)
      assertTrue(history.completeUndo(ticket))
    }
    assertNull(history.beginUndo(key.first, key.second))

    listOf("shape-1", "ink-1", "shape-2").forEach { expectedId ->
      val ticket = history.beginRedo(key.first, key.second) ?: error("missing redo action")
      assertEquals(expectedId, ticket.action.id)
      assertTrue(history.completeRedo(ticket))
    }

    val undone = history.beginUndo(key.first, key.second) ?: error("missing undo action")
    assertTrue(history.completeUndo(undone))
    history.record(key.first, key.second, edit("new-ink", ReaderActionStack.Layer.PEN))
    assertNull(history.beginRedo(key.first, key.second))
    assertEquals("new-ink", history.beginUndo(key.first, key.second)?.action?.id)
  }

  @Test
  fun pendingGroupKeepsItsReservedPositionAndEmptyGroupLeavesNoEntry() {
    val history = ReaderActionStack()
    val project = "project"
    val groupId = history.beginGroup(project, 1)
    assertTrue(history.recordInGroup(project, 1, groupId, edit("erase-ink", ReaderActionStack.Layer.PEN)))
    history.record(project, 1, edit("later-ink", ReaderActionStack.Layer.PEN))
    assertTrue(history.recordInGroup(project, 1, groupId, edit("erase-shape", ReaderActionStack.Layer.ANNOTATION)))
    assertTrue(history.endGroup(project, 1, groupId))

    val later = history.beginUndo(project, 1) ?: error("missing later action")
    assertEquals("later-ink", later.action.id)
    assertTrue(history.completeUndo(later))
    val grouped = history.beginUndo(project, 1) ?: error("missing grouped action")
    assertEquals(
      listOf("erase-ink", "erase-shape"),
      (grouped.action as ReaderEditAction.Group).operations.map { it.id },
    )
    assertTrue(history.completeUndo(grouped))

    val emptyGroup = history.beginGroup(project, 2)
    assertFalse(history.endGroup(project, 2, emptyGroup))
    assertNull(history.beginUndo(project, 2))
  }

  @Test
  fun rejectedUndoRedoAndDelayedRollbackKeepTheActionRetryable() {
    val history = ReaderActionStack()
    val project = "project"
    val action = edit("shape", ReaderActionStack.Layer.ANNOTATION)
    history.record(project, 1, action)

    val rejectedUndo = history.beginUndo(project, 1) ?: error("missing undo action")
    assertTrue(history.rollbackUndo(rejectedUndo))
    val retryUndo = history.beginUndo(project, 1) ?: error("undo was lost after rollback")
    assertEquals(action, retryUndo.action)
    assertTrue(history.rollbackUndo(retryUndo))

    val acceptedUndo = history.beginUndo(project, 1) ?: error("missing undo action")
    assertTrue(history.completeUndo(acceptedUndo))
    assertTrue(history.rollbackUndo(acceptedUndo))
    val restoredUndo = history.beginUndo(project, 1) ?: error("delayed failure did not restore undo")
    assertEquals(action, restoredUndo.action)
    assertTrue(history.rollbackUndo(restoredUndo))

    val completedUndo = history.beginUndo(project, 1) ?: error("missing undo action")
    assertTrue(history.completeUndo(completedUndo))
    val rejectedRedo = history.beginRedo(project, 1) ?: error("missing redo action")
    assertTrue(history.rollbackRedo(rejectedRedo))
    val retryRedo = history.beginRedo(project, 1) ?: error("redo was lost after rollback")
    assertEquals(action, retryRedo.action)
    assertTrue(history.rollbackRedo(retryRedo))
  }

  @Test
  fun delayedUndoRollbackPreservesEditsCommittedWhileSaveWasPending() {
    val history = ReaderActionStack()
    val project = "project"
    history.record(project, 1, edit("before", ReaderActionStack.Layer.PEN))
    history.record(project, 1, edit("pending-undo", ReaderActionStack.Layer.ANNOTATION))

    val pendingUndo = history.beginUndo(project, 1) ?: error("missing undo action")
    assertTrue(history.completeUndo(pendingUndo, awaitPersistence = true))
    history.record(project, 1, edit("later-edit", ReaderActionStack.Layer.PEN))

    assertTrue(history.rollbackUndo(pendingUndo))
    val undoLater = history.beginUndo(project, 1) ?: error("later edit was lost")
    assertEquals("later-edit", undoLater.action.id)
    assertTrue(history.completeUndo(undoLater))
    val undoRestored = history.beginUndo(project, 1) ?: error("failed undo was not restored")
    assertEquals("pending-undo", undoRestored.action.id)
  }

  @Test
  fun failedRedoRollbackKeepsTheNewBranchAndDiscardsThePendingRedoAction() {
    val history = ReaderActionStack()
    val project = "project"
    history.record(project, 1, edit("before", ReaderActionStack.Layer.PEN))
    history.record(project, 1, edit("redo-branch", ReaderActionStack.Layer.ANNOTATION))
    val undoBranch = history.beginUndo(project, 1) ?: error("missing undo action")
    assertTrue(history.completeUndo(undoBranch))

    val pendingRedo = history.beginRedo(project, 1) ?: error("missing redo action")
    assertTrue(history.completeRedo(pendingRedo, awaitPersistence = true))
    history.record(project, 1, edit("new-branch", ReaderActionStack.Layer.PEN))

    assertTrue(history.rollbackRedo(pendingRedo))
    val undoNewBranch = history.beginUndo(project, 1) ?: error("new branch was lost")
    assertEquals("new-branch", undoNewBranch.action.id)
    assertTrue(history.completeUndo(undoNewBranch))
    assertEquals("before", history.beginUndo(project, 1)?.action?.id)
  }

  @Test
  fun depthPageIsolationAndPageRemappingPreserveActionIdentity() {
    val history = ReaderActionStack(maxDepth = 2)
    history.record("project", 1, edit("old", ReaderActionStack.Layer.PEN))
    history.record("project", 1, edit("middle", ReaderActionStack.Layer.ANNOTATION))
    val newest = edit("newest", ReaderActionStack.Layer.PEN)
    history.record("project", 1, newest)
    history.record("project", 2, edit("other-page", ReaderActionStack.Layer.PEN))

    val first = history.beginUndo("project", 1) ?: error("missing newest action")
    assertEquals("newest", first.action.id)
    assertTrue(history.completeUndo(first))
    val second = history.beginUndo("project", 1) ?: error("missing middle action")
    assertEquals("middle", second.action.id)
    assertTrue(history.completeUndo(second))
    assertNull(history.beginUndo("project", 1))
    val otherPageEdit = history.beginUndo("project", 2) ?: error("missing other-page action")
    assertEquals("other-page", otherPageEdit.action.id)
    assertTrue(history.completeUndo(otherPageEdit))

    history.remapPages("project", mapOf(2 to 4))
    assertNull(history.beginUndo("project", 2))
    assertEquals("other-page", history.beginRedo("project", 4)?.action?.id)
  }
}
