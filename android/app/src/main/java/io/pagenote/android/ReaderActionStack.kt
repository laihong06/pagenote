package io.pagenote.android

/**
 * A page-scoped, cursor-based action list matching the reference ActionStack
 * ordering. The action payloads identify PageNote's current layer adapters;
 * they do not replace those stores' page snapshots in this migration step.
 */
internal class ReaderActionStack(private val maxDepth: Int = 80) {
  enum class Layer { PEN, ANNOTATION, COMPOSITE }

  enum class Direction { UNDO, REDO }

  internal enum class TicketStatus { ACTIVE, COMPLETED, AWAITING_FINALIZE, FINALIZED, ROLLED_BACK, STALE }

  private class Entry(
    var action: ReaderEditAction? = null,
    val groupId: Long? = null,
    val groupChildren: MutableList<ReaderEditAction.Single>? = null,
  ) {
    val pending: Boolean get() = groupId != null && action == null
  }

  private class PageHistory {
    val actions = mutableListOf<Entry>()
    val groups = mutableMapOf<Long, Entry>()
    var current = 0
    var activeTicket: Ticket? = null
    var appendRevision = 0L
  }

  /** A reserved cursor movement, retained until async persistence is final. */
  class Ticket internal constructor(
    val action: ReaderEditAction,
    val direction: Direction,
    val id: Long,
    internal val owner: Any,
    internal val entry: Any,
    internal val originalIndex: Int,
    internal val appendRevision: Long,
  ) {
    internal var status = TicketStatus.ACTIVE
  }

  init {
    require(maxDepth > 0)
  }

  private val pages = mutableMapOf<Pair<String, Int>, PageHistory>()
  private var nextActionId = 1L
  private var nextGroupId = 1L
  private var nextTicketId = 1L

  fun record(projectId: String, page: Int, layer: Layer) {
    require(layer != Layer.COMPOSITE)
    record(projectId, page, ReaderEditAction.Single("reader-${nextActionId++}", layer))
  }

  fun record(projectId: String, page: Int, action: ReaderEditAction) {
    require(projectId.isNotBlank() && page >= 1)
    append(pages.getOrPut(projectId to page, ::PageHistory), Entry(action = action))
  }

  /** Reserves this gesture's chronological slot while its save is pending. */
  fun beginGroup(projectId: String, page: Int): Long {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages.getOrPut(projectId to page, ::PageHistory)
    val id = nextGroupId++
    val entry = Entry(groupId = id, groupChildren = mutableListOf())
    append(state, entry)
    state.groups[id] = entry
    return id
  }

  fun recordInGroup(projectId: String, page: Int, groupId: Long, layer: Layer): Boolean {
    require(layer != Layer.COMPOSITE)
    return recordInGroup(
      projectId,
      page,
      groupId,
      ReaderEditAction.Single("reader-${nextActionId++}", layer),
    )
  }

  fun recordInGroup(projectId: String, page: Int, groupId: Long, action: ReaderEditAction.Single): Boolean {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages[projectId to page] ?: return false
    val entry = state.groups[groupId] ?: return false
    entry.groupChildren?.add(action) ?: return false
    return true
  }

  /** Publishes a reserved action after its page save resolves. */
  fun endGroup(projectId: String, page: Int, groupId: Long): Boolean {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages[projectId to page] ?: return false
    val entry = state.groups.remove(groupId) ?: return false
    val children = entry.groupChildren.orEmpty()
    if (children.isEmpty()) {
      removeEntry(state, entry)
      return false
    }
    entry.action = ReaderEditAction.Group("reader-group-$groupId", children.toList())
    return true
  }

  fun nextUndo(projectId: String, page: Int): Layer? =
    actionAtUndo(pages[projectId to page])?.layer

  fun nextRedo(projectId: String, page: Int): Layer? =
    actionAtRedo(pages[projectId to page])?.layer

  fun hasPendingUndo(projectId: String, page: Int): Boolean =
    pages[projectId to page]?.let { state -> state.actions.getOrNull(state.current - 1)?.pending } == true

  fun layersForUndo(projectId: String, page: Int): List<Layer> =
    actionAtUndo(pages[projectId to page])?.operations?.map { it.layer }.orEmpty()

  fun layersForRedo(projectId: String, page: Int): List<Layer> =
    actionAtRedo(pages[projectId to page])?.operations?.map { it.layer }.orEmpty()

  fun beginUndo(projectId: String, page: Int): Ticket? =
    begin(projectId, page, Direction.UNDO)

  fun beginRedo(projectId: String, page: Int): Ticket? =
    begin(projectId, page, Direction.REDO)

  /** Completes the cursor move after the layer adapter accepts the operation. */
  fun completeUndo(ticket: Ticket, awaitPersistence: Boolean = false): Boolean =
    complete(ticket, Direction.UNDO, awaitPersistence)

  fun completeRedo(ticket: Ticket, awaitPersistence: Boolean = false): Boolean =
    complete(ticket, Direction.REDO, awaitPersistence)

  /** Releases a ticket whose async persistence succeeded. */
  fun finalize(ticket: Ticket): Boolean {
    val state = ticket.owner as? PageHistory ?: return false
    if (ticket.status != TicketStatus.AWAITING_FINALIZE) return false
    if (state.activeTicket !== ticket) return false
    state.activeTicket = null
    ticket.status = TicketStatus.FINALIZED
    return true
  }

  /** Restores the original cursor position after a rejected or failed undo. */
  fun rollbackUndo(ticket: Ticket): Boolean = rollback(ticket, Direction.UNDO)

  /** Restores the original cursor position after a rejected or failed redo. */
  fun rollbackRedo(ticket: Ticket): Boolean = rollback(ticket, Direction.REDO)

  fun remapPages(projectId: String, mapping: Map<Int, Int>) {
    val existing = pages.filterKeys { it.first == projectId }
    pages.keys.removeAll(existing.keys)
    existing.forEach { (key, state) -> mapping[key.second]?.let { pages[projectId to it] = state } }
  }

  private fun begin(projectId: String, page: Int, direction: Direction): Ticket? {
    require(projectId.isNotBlank() && page >= 1)
    val state = pages[projectId to page] ?: return null
    if (state.activeTicket != null) return null
    val index = if (direction == Direction.UNDO) state.current - 1 else state.current
    val entry = state.actions.getOrNull(index) ?: return null
    val action = entry.action ?: return null
    if (entry.pending) return null
    state.current = if (direction == Direction.UNDO) index else index + 1
    return Ticket(
      action = action,
      direction = direction,
      id = nextTicketId++,
      owner = state,
      entry = entry,
      originalIndex = index,
      appendRevision = state.appendRevision,
    ).also { state.activeTicket = it }
  }

  private fun complete(ticket: Ticket, direction: Direction, awaitPersistence: Boolean): Boolean {
    val state = ticket.owner as? PageHistory ?: return false
    if (ticket.direction != direction || ticket.status != TicketStatus.ACTIVE || state.activeTicket !== ticket) return false
    ticket.status = if (awaitPersistence) TicketStatus.AWAITING_FINALIZE else TicketStatus.COMPLETED
    if (!awaitPersistence) state.activeTicket = null
    return true
  }

  private fun rollback(ticket: Ticket, direction: Direction): Boolean {
    val state = ticket.owner as? PageHistory ?: return false
    if (ticket.direction != direction) return false
    if (ticket.status == TicketStatus.ACTIVE || ticket.status == TicketStatus.AWAITING_FINALIZE) {
      if (state.activeTicket !== ticket) return false
      state.activeTicket = null
    } else if (ticket.status != TicketStatus.COMPLETED) {
      if (ticket.status != TicketStatus.STALE || direction != Direction.UNDO) return false
    }

    val entry = ticket.entry as? Entry ?: return false
    val index = state.actions.indexOfFirst { it === entry }
    if (direction == Direction.UNDO) {
      if (index >= 0) {
        if (state.current <= index) state.current = index + 1
      } else {
        val insertion = ticket.originalIndex.coerceIn(0, state.actions.size)
        state.actions.add(insertion, entry)
        if (insertion <= state.current) state.current++
      }
    } else {
      if (index >= 0) {
        if (state.appendRevision != ticket.appendRevision) {
          removeAt(state, index)
          if (state.current > index) state.current--
        } else if (state.current > index) {
          state.current = index
        }
      } else {
        if (state.appendRevision == ticket.appendRevision) {
          val insertion = ticket.originalIndex.coerceIn(0, state.actions.size)
          state.actions.add(insertion, entry)
          if (insertion <= state.current) state.current++
        }
      }
    }
    ticket.status = TicketStatus.ROLLED_BACK
    trim(state)
    return true
  }

  private fun append(state: PageHistory, entry: Entry) {
    while (state.actions.size > state.current) {
      removeAt(state, state.actions.lastIndex)
    }
    state.actions.add(entry)
    state.current++
    state.appendRevision++
    trim(state)
  }

  private fun trim(state: PageHistory) {
    while (state.actions.size > maxDepth) {
      removeAt(state, 0)
      state.current = (state.current - 1).coerceAtLeast(0)
    }
  }

  private fun removeEntry(state: PageHistory, entry: Entry) {
    val index = state.actions.indexOfFirst { it === entry }
    if (index < 0) return
    removeAt(state, index)
    if (state.current > index) state.current--
  }

  private fun removeAt(state: PageHistory, index: Int) {
    val removed = state.actions.removeAt(index)
    removed.groupId?.let(state.groups::remove)
    if (removed === (state.activeTicket?.entry as? Entry)) {
      state.activeTicket?.status = TicketStatus.STALE
      state.activeTicket = null
    }
  }

  private fun actionAtUndo(state: PageHistory?): ReaderEditAction? =
    state?.let { it.actions.getOrNull(it.current - 1)?.takeUnless { entry -> entry.pending }?.action }

  private fun actionAtRedo(state: PageHistory?): ReaderEditAction? =
    state?.let { it.actions.getOrNull(it.current)?.takeUnless { entry -> entry.pending }?.action }
}

internal sealed interface ReaderEditAction {
  val id: String
  val operations: List<Single>
  val layer: ReaderActionStack.Layer
    get() = if (operations.size == 1) operations.single().layer else ReaderActionStack.Layer.COMPOSITE

  data class Single(
    override val id: String,
    override val layer: ReaderActionStack.Layer,
  ) : ReaderEditAction {
    init {
      require(layer != ReaderActionStack.Layer.COMPOSITE)
    }

    override val operations: List<Single> get() = listOf(this)
  }

  data class Group(
    override val id: String,
    override val operations: List<Single>,
  ) : ReaderEditAction
}
