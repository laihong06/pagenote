package io.pagenote.android

/**
 * Keeps chronological order while the reader still stores ink and native
 * annotation snapshots in their existing, separate page-scoped histories.
 */
internal class ReaderEditHistoryOrder(private val maxDepth: Int = 80) {
  enum class Layer { PEN, ANNOTATION }

  private data class PageHistory(
    val undo: ArrayDeque<Layer> = ArrayDeque(),
    val redo: ArrayDeque<Layer> = ArrayDeque(),
  )

  private val pages = mutableMapOf<Pair<String, Int>, PageHistory>()

  init {
    require(maxDepth > 0)
  }

  fun record(projectId: String, page: Int, layer: Layer) {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages.getOrPut(projectId to page, ::PageHistory)
    state.undo.addLast(layer)
    while (state.undo.size > maxDepth) state.undo.removeFirst()
    state.redo.clear()
  }

  fun nextUndo(projectId: String, page: Int): Layer? = pages[projectId to page]?.undo?.lastOrNull()

  fun completeUndo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    if (state.undo.lastOrNull() != layer) return false
    state.undo.removeLast()
    state.redo.addLast(layer)
    while (state.redo.size > maxDepth) state.redo.removeFirst()
    return true
  }

  fun rollbackUndo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    if (state.redo.lastOrNull() != layer) return false
    state.redo.removeLast()
    state.undo.addLast(layer)
    return true
  }

  fun nextRedo(projectId: String, page: Int): Layer? = pages[projectId to page]?.redo?.lastOrNull()

  fun completeRedo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    if (state.redo.lastOrNull() != layer) return false
    state.redo.removeLast()
    state.undo.addLast(layer)
    while (state.undo.size > maxDepth) state.undo.removeFirst()
    return true
  }

  fun rollbackRedo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    if (state.undo.lastOrNull() != layer) return false
    state.undo.removeLast()
    state.redo.addLast(layer)
    return true
  }

  fun remapPages(projectId: String, mapping: Map<Int, Int>) {
    val existing = pages.filterKeys { it.first == projectId }
    pages.keys.removeAll(existing.keys)
    existing.forEach { (key, state) -> mapping[key.second]?.let { pages[projectId to it] = state } }
  }
}
