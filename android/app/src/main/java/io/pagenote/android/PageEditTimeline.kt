package io.pagenote.android

/**
 * Page-local ordering of ink and annotation gestures. This tracks ordering only:
 * callers own snapshots and must serialize each begin/finish with its mutation.
 * Each instance belongs to one project session and must be cleared on replacement.
 * Callers must acquire [startCommit] before modifying the underlying stack,
 * and call [resolve] after the modification or rollback has finished.
 * It is not yet connected to the reader's existing history stores.
 */
internal class PageEditTimeline(private val maxEntries: Int = 80) {
  init { require(maxEntries > 0) }
  enum class Kind { INK, ANNOTATION, MIXED }
  data class ActionId internal constructor(val sequence: Long, internal val owner: Any)
  data class Token internal constructor(val page: Int, val sequence: Long, val kind: Kind, internal val owner: Any) {
    val actionId: ActionId get() = ActionId(sequence, owner)
  }
  private data class Entry(val token: Token, var ready: Boolean = false)
  private enum class Direction { UNDO, REDO, COMMIT }
  private data class Operation(val token: Token, val direction: Direction)
  private data class Page(
    val entries: MutableList<Entry> = mutableListOf(),
    var cursor: Int = 0,
    var operation: Operation? = null,
    var redoBackup: List<Entry>? = null,
    var branchBase: Int = 0,
    var evictionBackup: Entry? = null,
  )

  private val pages = mutableMapOf<Int, Page>()
  private val owner = Any()
  private var nextSequence = 0L

  /** Reserve at gesture time, so an asynchronous save cannot reorder actions. */
  @Synchronized fun begin(page: Int, kind: Kind): Token? {
    require(page >= 1)
    val state = pages.getOrPut(page) { Page() }
    if (state.operation != null) return null
    if (state.cursor == state.entries.size && state.entries.size >= maxEntries &&
      (state.entries.any { !it.ready } || state.evictionBackup != null)) return null
    if (state.cursor < state.entries.size) {
      if (state.redoBackup == null) {
        state.redoBackup = state.entries.subList(state.cursor, state.entries.size).toList()
        state.branchBase = state.cursor
      }
      state.entries.subList(state.cursor, state.entries.size).clear()
    }
    if (state.entries.size >= maxEntries) {
      state.evictionBackup = state.entries.removeAt(0)
      state.cursor--
      if (state.redoBackup != null) state.branchBase--
    }
    val token = Token(page, ++nextSequence, kind, owner)
    state.entries.add(Entry(token))
    state.cursor++
    return token
  }

  /** Before recording a retained action, earlier same-kind actions must be resolved. */
  @Synchronized fun canCommit(token: Token): Boolean {
    if (token.owner !== owner) return false
    val state = pages[token.page] ?: return false
    val index = state.entries.indexOfFirst { it.token == token }
    return state.operation == null && index >= 0 && !state.entries[index].ready &&
      state.entries.take(index).none {
        !it.ready && (it.token.kind == token.kind || it.token.kind == Kind.MIXED || token.kind == Kind.MIXED)
      }
  }

  /** Hold the page operation slot until the backend stack has been updated. */
  @Synchronized fun startCommit(token: Token): Boolean {
    if (!canCommit(token)) return false
    pages.getValue(token.page).operation = Operation(token, Direction.COMMIT)
    return true
  }

  /** A retained edit is recorded even if disk saving failed; false means model rollback. */
  @Synchronized fun resolve(token: Token, retained: Boolean): Boolean {
    if (token.owner !== owner) return false
    val state = pages[token.page] ?: return false
    val active = state.operation
    if (active != null && active != Operation(token, Direction.COMMIT)) return false
    val index = state.entries.indexOfFirst { it.token == token }
    if (index < 0 || state.entries[index].ready) return false
    if (retained) {
      if (active != Operation(token, Direction.COMMIT)) return false
      state.entries[index].ready = true
      state.evictionBackup = null
      if (state.redoBackup != null && index >= state.branchBase) state.redoBackup = null
    }
    else {
      state.entries.removeAt(index)
      if (index < state.cursor) state.cursor--
      state.evictionBackup?.let { entry ->
        state.entries.add(0, entry)
        state.cursor++
        state.evictionBackup = null
      }
      if (state.redoBackup != null && index < state.branchBase) state.branchBase--
      val backup = state.redoBackup
      if (backup != null && state.entries.size == state.branchBase) {
        state.entries.addAll(backup)
        state.redoBackup = null
      }
    }
    if (active == Operation(token, Direction.COMMIT)) state.operation = null
    return true
  }

  @Synchronized fun startUndo(page: Int): Token? = start(page, Direction.UNDO)
  @Synchronized fun startRedo(page: Int): Token? = start(page, Direction.REDO)

  private fun start(page: Int, direction: Direction): Token? {
    val state = pages[page] ?: return null
    if (state.operation != null) return null
    val index = if (direction == Direction.UNDO) state.cursor - 1 else state.cursor
    val entry = state.entries.getOrNull(index) ?: return null
    if (!entry.ready) return null
    state.operation = Operation(entry.token, direction)
    return entry.token
  }

  @Synchronized fun finishUndo(token: Token, success: Boolean): Boolean = finish(token, Direction.UNDO, success)
  @Synchronized fun finishRedo(token: Token, success: Boolean): Boolean = finish(token, Direction.REDO, success)

  private fun finish(token: Token, direction: Direction, success: Boolean): Boolean {
    if (token.owner !== owner) return false
    val state = pages[token.page] ?: return false
    if (state.operation != Operation(token, direction)) return false
    if (success) state.cursor += if (direction == Direction.UNDO) -1 else 1
    state.operation = null
    return true
  }

  @Synchronized fun isWaiting(page: Int): Boolean {
    val state = pages[page] ?: return false
    return state.operation != null || state.entries.getOrNull(state.cursor - 1)?.ready == false
  }

  /** Remap pages only when no asynchronous operation still holds an old page token.
   * Snapshots must be keyed by [Token.actionId], which stays stable across remapping.
   */
  @Synchronized fun remapPages(mapping: Map<Int, Int>): Boolean {
    if (mapping.any { it.key < 1 || it.value < 1 }) return false
    if (pages.values.any { page -> page.operation != null || page.entries.any { !it.ready } }) return false
    val mapped = pages.keys.mapNotNull(mapping::get)
    if (mapped.toSet().size != mapped.size) return false
    val next = mutableMapOf<Int, Page>()
    pages.forEach { (oldPage, state) ->
      val newPage = mapping[oldPage] ?: return@forEach
      val entries = state.entries.map { entry -> Entry(entry.token.copy(page = newPage), entry.ready) }.toMutableList()
      next[newPage] = Page(entries = entries, cursor = state.cursor)
    }
    pages.clear()
    pages.putAll(next)
    return true
  }

  /** Clear only after callers have completed or rolled back every outstanding operation. */
  @Synchronized fun clear(): Boolean {
    if (pages.values.any { page -> page.operation != null || page.entries.any { !it.ready } }) return false
    pages.clear()
    return true
  }
}
