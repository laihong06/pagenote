package io.pagenote.feature.pen

/**
 * Scope-keyed undo/redo stacks of [PenAction]s (port batch 1).
 *
 * Semantics mirror the existing page-scoped history of [PenPageStore]:
 * undo/redo only touch the requested scope, a new record clears that scope's
 * redo stack only, and depth is capped at [maxDepth] (oldest dropped).
 * When a newly recorded action merges with the top of a scope's undo stack
 * (see [PenAction.merge]), the merged action replaces it as one undo step.
 */
class PenActionStack(private val maxDepth: Int = PenValidation.UNDO_DEPTH) {
  init {
    require(maxDepth >= 1) { "bad maxDepth: $maxDepth" }
  }

  private val undoStacks = mutableMapOf<String, ArrayDeque<PenAction>>()
  private val redoStacks = mutableMapOf<String, ArrayDeque<PenAction>>()

  /** Executes [action] and records it in its scope. Rejects unregistered ids. */
  fun execute(action: PenAction): PenAction {
    require(PenActionRegistry.isRegistered(action.actionId)) {
      "unregistered action id: ${action.actionId}"
    }
    action.doAction()
    val undo = undoStacks.getOrPut(action.scope) { ArrayDeque() }
    val top = undo.lastOrNull()
    val merged = top?.merge(action)
    if (merged != null) {
      undo.removeLast()
      undo.addLast(merged)
    } else {
      undo.addLast(action)
    }
    while (undo.size > maxDepth) undo.removeFirst()
    redoStacks[action.scope]?.clear()
    return action
  }

  /** Undoes the last recorded action in [scope]. False when nothing to undo. */
  fun undo(scope: String): Boolean {
    val action = undoStacks[scope]?.removeLastOrNull() ?: return false
    action.undo()
    redoStacks.getOrPut(scope) { ArrayDeque() }.addLast(action)
    return true
  }

  /** Redoes the last undone action in [scope]. False when nothing to redo. */
  fun redo(scope: String): Boolean {
    val action = redoStacks[scope]?.removeLastOrNull() ?: return false
    action.redo()
    undoStacks.getOrPut(scope) { ArrayDeque() }.addLast(action)
    return true
  }

  fun canUndo(scope: String): Boolean = !undoStacks[scope].isNullOrEmpty()

  fun canRedo(scope: String): Boolean = !redoStacks[scope].isNullOrEmpty()

  /** Most recently recorded action in [scope], without mutating the stack. */
  fun peekUndo(scope: String): PenAction? = undoStacks[scope]?.lastOrNull()

  /** Drops all history of [scope] (e.g. when the page is reloaded). */
  fun clear(scope: String) {
    undoStacks.remove(scope)
    redoStacks.remove(scope)
  }

  /** Drops all history of all scopes. */
  fun clearAll() {
    undoStacks.clear()
    redoStacks.clear()
  }
}
