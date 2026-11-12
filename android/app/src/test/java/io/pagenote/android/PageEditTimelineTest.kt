package io.pagenote.android

import org.junit.Assert.*
import org.junit.Test

class PageEditTimelineTest {
  private fun commit(history: PageEditTimeline, token: PageEditTimeline.Token) {
    assertTrue(history.startCommit(token))
    assertTrue(history.resolve(token, retained = true))
  }

  @Test fun writeInFlightPreventsUndoUntilHistoryRecordIsFinished() {
    val history = PageEditTimeline()
    val pending = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, ink)
    assertTrue(history.startCommit(pending))
    assertNull(history.startUndo(1))
    assertTrue(history.resolve(pending, retained = true))
    assertEquals(ink, history.startUndo(1))
  }
  @Test fun interleavedInkAndAnnotationUndoAndRedoFollowGestureOrder() {
    val history = PageEditTimeline()
    val first = history.begin(1, PageEditTimeline.Kind.INK)!!
    commit(history, first)
    val second = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    commit(history, second)
    val third = history.begin(1, PageEditTimeline.Kind.INK)!!
    commit(history, third)
    assertEquals(third, history.startUndo(1)); assertTrue(history.finishUndo(third, true))
    assertEquals(second, history.startUndo(1)); assertTrue(history.finishUndo(second, true))
    assertEquals(first, history.startUndo(1)); assertTrue(history.finishUndo(first, true))
    assertEquals(first, history.startRedo(1)); assertTrue(history.finishRedo(first, true))
    assertEquals(second, history.startRedo(1)); assertTrue(history.finishRedo(second, true))
    assertEquals(third, history.startRedo(1)); assertTrue(history.finishRedo(third, true))
  }

  @Test fun pendingAndFailedActionsDoNotCorruptOrderOrBlockOtherPages() {
    val history = PageEditTimeline()
    val pending = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!
    commit(history, ink)
    val other = history.begin(2, PageEditTimeline.Kind.INK)!!
    commit(history, other)
    assertEquals(ink, history.startUndo(1)); assertTrue(history.finishUndo(ink, true))
    assertNull(history.startUndo(1))
    assertTrue(history.isWaiting(1))
    assertEquals(other, history.startUndo(2)); assertTrue(history.finishUndo(other, true))
    history.resolve(pending, false)
    assertNull(history.startUndo(1)); assertFalse(history.isWaiting(1))
    assertEquals(ink, history.startRedo(1))
  }

  @Test fun failedUndoKeepsActionAndNewEditInvalidatesAllRedoKinds() {
    val history = PageEditTimeline()
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, ink)
    val shape = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!; commit(history, shape)
    assertEquals(shape, history.startUndo(1)); assertTrue(history.finishUndo(shape, false))
    assertEquals(shape, history.startUndo(1)); assertTrue(history.finishUndo(shape, true))
    assertEquals(ink, history.startUndo(1)); assertTrue(history.finishUndo(ink, true))
    val next = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, next)
    assertNull(history.startRedo(1))
    assertEquals(next, history.startUndo(1))
  }

  @Test fun openHistoryOperationCannotBeSupersededByNewEdit() {
    val history = PageEditTimeline()
    val action = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, action)
    assertEquals(action, history.startUndo(1))
    assertNull(history.begin(1, PageEditTimeline.Kind.ANNOTATION))
    assertFalse(history.finishRedo(action, true))
    assertTrue(history.finishUndo(action, true))
    assertNotNull(history.begin(1, PageEditTimeline.Kind.ANNOTATION))
  }

  @Test fun pendingActionsWaitWhileUndoIsInFlight() {
    val history = PageEditTimeline()
    val pending = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, ink)
    assertEquals(ink, history.startUndo(1))
    assertFalse(history.canCommit(pending))
    assertFalse(history.resolve(pending, false))
    assertTrue(history.finishUndo(ink, true))
    assertTrue(history.resolve(pending, false))
  }

  @Test fun failedNewEditRestoresRedoButSuccessfulLaterEditDiscardsIt() {
    val history = PageEditTimeline()
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, ink)
    val shape = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!; commit(history, shape)
    assertEquals(shape, history.startUndo(1)); assertTrue(history.finishUndo(shape, true))
    val failure = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    assertTrue(history.resolve(failure, false))
    assertEquals(shape, history.startRedo(1)); assertTrue(history.finishRedo(shape, true))
    assertEquals(shape, history.startUndo(1)); assertTrue(history.finishUndo(shape, true))
    val earlierFailure = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val laterSuccess = history.begin(1, PageEditTimeline.Kind.INK)!!
    commit(history, laterSuccess)
    assertTrue(history.resolve(earlierFailure, false))
    assertNull(history.startRedo(1))
  }

  @Test fun earlierPendingFailureShiftsBranchBoundary() {
    val history = PageEditTimeline()
    val pending = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val later = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, later)
    assertEquals(later, history.startUndo(1)); assertTrue(history.finishUndo(later, true))
    val newInk = history.begin(1, PageEditTimeline.Kind.INK)!!
    assertTrue(history.resolve(pending, false))
    assertNull(history.startRedo(1))
    assertTrue(history.resolve(newInk, false))
    assertEquals(later, history.startRedo(1))
  }

  @Test fun laterSuccessAfterEarlierFailureCannotReviveDiscardedRedo() {
    val history = PageEditTimeline()
    val pending = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val oldRedo = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, oldRedo)
    assertEquals(oldRedo, history.startUndo(1)); assertTrue(history.finishUndo(oldRedo, true))
    val newInk = history.begin(1, PageEditTimeline.Kind.INK)!!
    assertTrue(history.resolve(pending, false))
    commit(history, newInk)
    assertNull(history.startRedo(1))
  }

  @Test fun sameKindSavesMustCommitInGestureOrder() {
    val history = PageEditTimeline()
    val first = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val second = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    assertFalse(history.canCommit(second))
    assertFalse(history.resolve(second, true))
    assertTrue(history.canCommit(first)); commit(history, first)
    assertTrue(history.canCommit(second)); commit(history, second)
    assertEquals(second, history.startUndo(1))
  }

  @Test fun failedDiskSaveRetainsActionWhenInkRemainsOnCanvas() {
    val history = PageEditTimeline()
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!
    // The caller retained the edit locally despite the failed write.
    commit(history, ink)
    assertFalse(history.resolve(ink, retained = false))
    assertEquals(ink, history.startUndo(1))
  }

  @Test fun depthMatchesUnderlyingStoresAndDoesNotEvictPendingActions() {
    val history = PageEditTimeline(maxEntries = 2)
    val first = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    val second = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, second)
    assertNull(history.begin(1, PageEditTimeline.Kind.INK))
    commit(history, first)
    val third = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, third)
    assertEquals(third, history.startUndo(1)); history.finishUndo(third, true)
    assertEquals(second, history.startUndo(1)); history.finishUndo(second, true)
    assertNull(history.startUndo(1))
  }

  @Test fun failedEditAtCapacityRestoresOldestUndo() {
    val history = PageEditTimeline(maxEntries = 2)
    val first = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, first)
    val second = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, second)
    val failed = history.begin(1, PageEditTimeline.Kind.INK)!!
    assertNull(history.begin(1, PageEditTimeline.Kind.ANNOTATION))
    assertTrue(history.resolve(failed, false))
    assertEquals(second, history.startUndo(1)); history.finishUndo(second, true)
    assertEquals(first, history.startUndo(1))
  }

  @Test fun mixedActionsSerializeWithBothInkAndAnnotations() {
    val history = PageEditTimeline()
    val mixed = history.begin(1, PageEditTimeline.Kind.MIXED)!!
    val ink = history.begin(1, PageEditTimeline.Kind.INK)!!
    val annotation = history.begin(1, PageEditTimeline.Kind.ANNOTATION)!!
    assertFalse(history.canCommit(ink))
    assertFalse(history.canCommit(annotation))
    commit(history, mixed)
    assertTrue(history.canCommit(ink))
    assertTrue(history.canCommit(annotation))
  }

  @Test fun tokensAreBoundToInstanceAndCanBeCleared() {
    val one = PageEditTimeline()
    val two = PageEditTimeline()
    val token = one.begin(1, PageEditTimeline.Kind.INK)!!
    two.begin(1, PageEditTimeline.Kind.INK)
    assertFalse(two.resolve(token, true))
    assertFalse(one.clear())
    assertTrue(one.resolve(token, false))
    assertTrue(one.clear())
    assertFalse(one.resolve(token, true))
    assertNull(one.startUndo(1))
  }

  @Test fun clearCannotRemoveAnInFlightUndoOrCommit() {
    val history = PageEditTimeline()
    val action = history.begin(1, PageEditTimeline.Kind.INK)!!
    assertTrue(history.startCommit(action))
    assertFalse(history.clear())
    assertTrue(history.resolve(action, retained = true))
    assertEquals(action, history.startUndo(1))
    assertFalse(history.clear())
    assertTrue(history.finishUndo(action, false))
    assertTrue(history.clear())
  }

  @Test fun pageRemapPreservesHistoryAndRejectsPendingOperations() {
    val history = PageEditTimeline()
    val pageOne = history.begin(1, PageEditTimeline.Kind.INK)!!; commit(history, pageOne)
    val pending = history.begin(2, PageEditTimeline.Kind.ANNOTATION)!!
    assertFalse(history.remapPages(mapOf(1 to 3, 2 to 4)))
    commit(history, pending)
    assertFalse(history.remapPages(mapOf(1 to 3, 2 to 3)))
    assertTrue(history.remapPages(mapOf(1 to 3, 2 to 4)))
    assertNull(history.startUndo(1))
    val moved = history.startUndo(3)!!
    assertEquals(pageOne.sequence, moved.sequence)
    assertEquals(pageOne.actionId, moved.actionId)
    assertEquals(3, moved.page)
    assertTrue(history.finishUndo(moved, true))
    assertNotNull(history.startUndo(4))
  }
}
