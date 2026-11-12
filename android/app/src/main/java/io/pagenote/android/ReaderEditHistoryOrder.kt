package io.pagenote.android

/**
 * Keeps chronological order while the reader still stores ink and native
 * annotation snapshots in their existing, separate page-scoped histories.
 */
internal class ReaderEditHistoryOrder(private val maxDepth: Int = 80) {
  enum class Layer { PEN, ANNOTATION, COMPOSITE }

  private data class Edit(
    val layers: MutableList<Layer>,
    val groupId: Long? = null,
    var pending: Boolean = false,
  ) {
    val historyLayer: Layer?
      get() = if (pending) null else layers.singleOrNull() ?: Layer.COMPOSITE
  }

  private data class PageHistory(
    val undo: ArrayDeque<Edit> = ArrayDeque(),
    val redo: ArrayDeque<Edit> = ArrayDeque(),
    val groups: MutableMap<Long, Edit> = mutableMapOf(),
  )

  private val pages = mutableMapOf<Pair<String, Int>, PageHistory>()
  private var nextGroupId = 1L

  init {
    require(maxDepth > 0)
  }

  fun record(projectId: String, page: Int, layer: Layer) {
    require(projectId.isNotBlank() && page >= 1)
    require(layer != Layer.COMPOSITE)
    val state = pages.getOrPut(projectId to page, ::PageHistory)
    append(state, Edit(mutableListOf(layer)))
  }

  /** Reserves this gesture's chronological undo position before async model saves finish. */
  fun beginGroup(projectId: String, page: Int): Long {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages.getOrPut(projectId to page, ::PageHistory)
    val id = nextGroupId++
    val edit = Edit(mutableListOf(), groupId = id, pending = true)
    state.groups[id] = edit
    append(state, edit)
    return id
  }

  /** Adds an operation to the exact gesture group, leaving later edits in chronological order. */
  fun recordInGroup(projectId: String, page: Int, groupId: Long, layer: Layer): Boolean {
    require(projectId.isNotBlank() && page >= 1)
    require(layer != Layer.COMPOSITE)
    val state = pages[projectId to page] ?: return false
    val edit = state.groups[groupId] ?: return false
    edit.layers += layer
    return true
  }

  /** Publishes the reserved transaction after its model saves have succeeded or failed. */
  fun endGroup(projectId: String, page: Int, groupId: Long): Boolean {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages[projectId to page] ?: return false
    val edit = state.groups.remove(groupId) ?: return false
    edit.pending = false
    if (edit.layers.isNotEmpty()) return true
    state.undo.remove(edit)
    state.redo.remove(edit)
    return false
  }

  private fun append(state: PageHistory, edit: Edit) {
    state.undo.addLast(edit)
    while (state.undo.size > maxDepth) {
      state.undo.removeFirst().groupId?.let(state.groups::remove)
    }
    state.redo.clear()
  }

  fun nextUndo(projectId: String, page: Int): Layer? =
    pages[projectId to page]?.undo?.lastOrNull()?.historyLayer

  fun hasPendingUndo(projectId: String, page: Int): Boolean =
    pages[projectId to page]?.undo?.lastOrNull()?.pending == true

  fun layersForUndo(projectId: String, page: Int): List<Layer> =
    pages[projectId to page]?.undo?.lastOrNull()?.takeUnless { it.pending }?.layers.orEmpty()

  fun completeUndo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    val edit = state.undo.lastOrNull() ?: return false
    if (edit.historyLayer != layer) return false
    state.undo.removeLast()
    state.redo.addLast(edit)
    while (state.redo.size > maxDepth) state.redo.removeFirst()
    return true
  }

  fun rollbackUndo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    val edit = state.redo.lastOrNull() ?: return false
    if (edit.historyLayer != layer && layer !in edit.layers) return false
    state.redo.removeLast()
    state.undo.addLast(edit)
    return true
  }

  fun nextRedo(projectId: String, page: Int): Layer? =
    pages[projectId to page]?.redo?.lastOrNull()?.historyLayer

  fun layersForRedo(projectId: String, page: Int): List<Layer> =
    pages[projectId to page]?.redo?.lastOrNull()?.takeUnless { it.pending }?.layers.orEmpty()

  fun completeRedo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    val edit = state.redo.lastOrNull() ?: return false
    if (edit.historyLayer != layer) return false
    state.redo.removeLast()
    state.undo.addLast(edit)
    while (state.undo.size > maxDepth) state.undo.removeFirst()
    return true
  }

  fun rollbackRedo(projectId: String, page: Int, layer: Layer): Boolean {
    val state = pages[projectId to page] ?: return false
    val edit = state.undo.lastOrNull() ?: return false
    if (edit.historyLayer != layer && layer !in edit.layers) return false
    state.undo.removeLast()
    state.redo.addLast(edit)
    return true
  }

  fun remapPages(projectId: String, mapping: Map<Int, Int>) {
    val existing = pages.filterKeys { it.first == projectId }
    pages.keys.removeAll(existing.keys)
    existing.forEach { (key, state) -> mapping[key.second]?.let { pages[projectId to it] = state } }
  }
}
