package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 1: unified action stack, registry, and the PenPageStore adapter. */
class PenActionStackTest {
  private fun stroke(color: String = "#ff0000", width: Double = 2.0) = PenStroke(
    "pen",
    color,
    width,
    listOf(PenPoint(10f, 20f, 0.5f), PenPoint(30f, 40f, 0.7f)),
  )

  @Test
  fun executesRecordsAndReplaysThroughTheUnifiedStack() {
    val store = PenPageStore()
    val stack = PenActionStack()
    val actions = PenPageStoreActions(store)

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111")))
    assertEquals(listOf("#111111"), store.getItems(PenSide.DOCUMENT, 1).map { it.color })

    stack.execute(actions.eraseAt(PenSide.DOCUMENT, 1, 0))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())

    assertTrue(stack.undo("document:1"))
    assertEquals(listOf("#111111"), store.getItems(PenSide.DOCUMENT, 1).map { it.color })
    assertTrue(stack.undo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
    assertTrue(stack.redo("document:1"))
    assertEquals(listOf("#111111"), store.getItems(PenSide.DOCUMENT, 1).map { it.color })
    assertTrue(stack.redo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
  }

  @Test
  fun unifiedStackLeavesBuiltInStoreHistoryUntouched() {
    val store = PenPageStore()
    val stack = PenActionStack()
    val actions = PenPageStoreActions(store)

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke()))
    // Action-driven mutations never push built-in snapshots.
    assertFalse(store.canUndo(PenSide.DOCUMENT, 1))
    assertFalse(store.canRedo(PenSide.DOCUMENT, 1))
    // The action stack is the driver for this scope.
    assertTrue(stack.canUndo("document:1"))
  }

  @Test
  fun scopesAreIsolatedAndNewRecordsClearOnlyThatScopeRedo() {
    val store = PenPageStore()
    val stack = PenActionStack()
    val actions = PenPageStoreActions(store)

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111")))
    stack.execute(actions.addStroke(PenSide.DOCUMENT, 2, stroke(color = "#222222")))
    assertTrue(stack.undo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 2).size)
    assertTrue(stack.canRedo("document:1"))

    // A new record clears the redo stack of ITS scope only — mirroring the
    // store's built-in page-scoped semantics.
    stack.execute(actions.addStroke(PenSide.DOCUMENT, 2, stroke(color = "#333333")))
    assertTrue(stack.canRedo("document:1"))
    assertFalse(stack.canRedo("document:2"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 2).map { it.color }.containsAll(listOf("#222222", "#333333")))

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#444444")))
    assertFalse(stack.canRedo("document:1"))
    // The earlier undo left page 1 empty, so it now holds only the new stroke.
    assertEquals(listOf("#444444"), store.getItems(PenSide.DOCUMENT, 1).map { it.color })
  }

  @Test
  fun consecutiveStyleEditsMergeIntoOneUndoStep() {
    val store = PenPageStore()
    val stack = PenActionStack()
    val actions = PenPageStoreActions(store)

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111")))
    stack.execute(actions.updateStyle(PenSide.DOCUMENT, 1, 0, color = "#222222"))
    stack.execute(actions.updateStyle(PenSide.DOCUMENT, 1, 0, color = "#333333"))
    stack.execute(actions.updateStyle(PenSide.DOCUMENT, 1, 0, width = 6.0))

    // The three style edits coalesce into a single undo step.
    assertTrue(stack.undo("document:1"))
    assertEquals("#111111", store.getItems(PenSide.DOCUMENT, 1).single().color)
    assertEquals(2.0, store.getItems(PenSide.DOCUMENT, 1).single().width, 0.0)

    assertTrue(stack.undo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())

    assertTrue(stack.redo("document:1"))
    assertEquals("#111111", store.getItems(PenSide.DOCUMENT, 1).single().color)

    assertTrue(stack.redo("document:1"))
    val restored = store.getItems(PenSide.DOCUMENT, 1).single()
    assertEquals("#333333", restored.color)
    assertEquals(6.0, restored.width, 0.0)
  }

  @Test
  fun styleMergeStopsAtOtherActionsAndScopes() {
    val store = PenPageStore()
    val stack = PenActionStack()
    val actions = PenPageStoreActions(store)

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111")))
    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#222222")))
    // Adds never merge: two separate undo steps.
    assertTrue(stack.undo("document:1"))
    assertEquals(listOf("#111111"), store.getItems(PenSide.DOCUMENT, 1).map { it.color })
    assertTrue(stack.undo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())

    // A style edit in one scope never merges with or undoes another scope.
    stack.execute(actions.addStroke(PenSide.NOTE, 1, stroke(color = "#111111")))
    stack.execute(actions.updateStyle(PenSide.NOTE, 1, 0, color = "#333333"))
    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#222222")))
    assertTrue(stack.undo("document:1"))
    assertEquals("#333333", store.getItems(PenSide.NOTE, 1).single().color)
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
  }

  @Test
  fun eraseInRectAndClearPageActionsRoundTrip() {
    val store = PenPageStore()
    val stack = PenActionStack()
    val actions = PenPageStoreActions(store)

    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#111111")))
    stack.execute(
      actions.eraseInRect(PenSide.DOCUMENT, 1, PenRect.ofCorners(0.0, 0.0, 50.0, 50.0)),
    )
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
    assertTrue(stack.undo("document:1"))
    assertEquals(listOf("#111111"), store.getItems(PenSide.DOCUMENT, 1).map { it.color })
    assertTrue(stack.redo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())

    stack.execute(actions.clearPage(PenSide.DOCUMENT, 1))
    stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#444444")))
    assertTrue(stack.undo("document:1"))
    assertTrue(stack.undo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
    assertTrue(stack.redo("document:1"))
    assertTrue(store.getItems(PenSide.DOCUMENT, 1).isEmpty())
  }

  @Test
  fun depthCapDropsOldestActions() {
    val store = PenPageStore()
    val stack = PenActionStack(maxDepth = 2)
    val actions = PenPageStoreActions(store)

    repeat(3) { index ->
      stack.execute(actions.addStroke(PenSide.DOCUMENT, 1, stroke(color = "#%06x".format(index + 1))))
    }
    assertTrue(stack.undo("document:1"))
    assertTrue(stack.undo("document:1"))
    assertFalse(stack.undo("document:1"))
    // The first add fell off the stack, so two strokes remain.
    assertEquals(1, store.getItems(PenSide.DOCUMENT, 1).size)
  }

  @Test
  fun registryCatalogsBuiltInActionIdsAndRejectsUnknownOnes() {
    val stack = PenActionStack()
    listOf(
      PenPageStoreActions.ADD_STROKE,
      PenPageStoreActions.ERASE_AT,
      PenPageStoreActions.ERASE_IN_RECT,
      PenPageStoreActions.UPDATE_STYLE,
      PenPageStoreActions.CLEAR_PAGE,
    ).forEach { id ->
      assertTrue("expected $id registered", PenActionRegistry.isRegistered(id))
      assertTrue(PenActionRegistry.describe(id)!!.isNotEmpty())
    }

    val rogue = object : PenAction {
      override val actionId = "pen.rogue"
      override val scope = "document:1"
      override val label = "rogue"
      var ran = false
      override fun doAction() { ran = true }
      override fun undo() = Unit
      override fun redo() = Unit
    }
    assertThrows(IllegalArgumentException::class.java) { stack.execute(rogue) }
    assertFalse(rogue.ran)
    assertFalse(PenActionRegistry.isRegistered("pen.rogue"))
  }
}
