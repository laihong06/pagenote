package io.pagenote.feature.pen

/**
 * Adapter that turns [PenPageStore] mutations into unified [PenAction]s
 * (port batch 1). Each action runs the same validated mutation as the
 * store's public API but snapshots the page content before/after and
 * restores those snapshots on undo/redo — the action-shaped mirror of the
 * store's built-in page snapshots.
 *
 * Undo/redo for actions created here is driven by [PenActionStack]; the
 * store's built-in undo/redo stacks are bypassed. Pick ONE driver per
 * (side, page) scope — never mix both.
 *
 * Unlike the store API (whose no-op mutations return false and push nothing),
 * an executed action is recorded even when it turns out to be a content
 * no-op; undoing it simply restores the identical snapshot.
 */
class PenPageStoreActions(private val store: PenPageStore) {

  fun addStroke(side: PenSide, page: Int, stroke: PenStroke): PenAction =
    action(ADD_STROKE, "add stroke", side, page) { s, sd, pg ->
      s.appendStrokeWithoutSnapshot(sd, pg, stroke)
    }

  fun eraseAt(side: PenSide, page: Int, index: Int): PenAction =
    action(ERASE_AT, "erase stroke", side, page) { s, sd, pg ->
      s.eraseAtWithoutSnapshot(sd, pg, index)
    }

  fun eraseInRect(side: PenSide, page: Int, rect: PenRect): PenAction =
    action(ERASE_IN_RECT, "erase strokes in rect", side, page) { s, sd, pg ->
      s.eraseInRectWithoutSnapshot(sd, pg, rect)
    }

  fun updateStyle(
    side: PenSide,
    page: Int,
    index: Int,
    color: String? = null,
    width: Double? = null,
  ): PenAction =
    action(UPDATE_STYLE, "change style", side, page) { s, sd, pg ->
      s.updateStyleWithoutSnapshot(sd, pg, index, color, width)
    }

  fun clearPage(side: PenSide, page: Int): PenAction =
    action(CLEAR_PAGE, "clear page", side, page) { s, sd, pg ->
      if (s.liveSnapshot(sd, pg).isNotEmpty()) {
        s.replacePageWithoutSnapshot(sd, pg, emptyList())
      }
    }

  private fun action(
    actionId: String,
    label: String,
    side: PenSide,
    page: Int,
    mutate: (PenPageStore, PenSide, Int) -> Unit,
  ): PenAction = PenPageStoreAction(actionId, label, store, side, page, mutate)

  /** Snapshot-restore action over one (side, page) of a [PenPageStore]. */
  private class PenPageStoreAction(
    override val actionId: String,
    override val label: String,
    private val store: PenPageStore,
    private val side: PenSide,
    private val page: Int,
    private val mutate: (PenPageStore, PenSide, Int) -> Unit,
  ) : PenAction {
    override val scope: String = "${side.key}:$page"
    private var before: List<PenStroke> = emptyList()
    private var after: List<PenStroke> = emptyList()

    override fun doAction() {
      before = store.liveSnapshot(side, page)
      mutate(store, side, page)
      after = store.liveSnapshot(side, page)
    }

    override fun undo() = store.replacePageWithoutSnapshot(side, page, before)

    override fun redo() = store.replacePageWithoutSnapshot(side, page, after)

    /**
     * Only consecutive style edits of the same scope coalesce (e.g. one
     * slider drag); adds/erases/clears stay separate undo steps, matching
     * the store's built-in history granularity.
     */
    override fun merge(next: PenAction): PenAction? {
      if (next !is PenPageStoreAction) return null
      if (!mergeableWith(next)) return null
      return Merged(this, next)
    }

    private fun mergeableWith(next: PenPageStoreAction): Boolean =
      actionId == UPDATE_STYLE && next.actionId == UPDATE_STYLE && next.scope == scope

    /** Undo restores the first `before`, redo restores the last `after`. */
    private class Merged(
      private val original: PenPageStoreAction,
      private val successor: PenPageStoreAction,
    ) : PenAction {
      override val actionId = original.actionId
      override val scope = original.scope
      override val label = original.label

      override fun doAction() = successor.redo()

      override fun undo() = original.undo()

      override fun redo() = successor.redo()

      override fun merge(next: PenAction): PenAction? {
        if (next !is PenPageStoreAction) return null
        if (!original.mergeableWith(next)) return null
        return Merged(original, next)
      }
    }
  }

  companion object {
    const val ADD_STROKE = "pen.addStroke"
    const val ERASE_AT = "pen.eraseAt"
    const val ERASE_IN_RECT = "pen.eraseInRect"
    const val UPDATE_STYLE = "pen.updateStyle"
    const val CLEAR_PAGE = "pen.clearPage"

    init {
      PenActionRegistry.register(ADD_STROKE, "Append one stroke to a pen page")
      PenActionRegistry.register(ERASE_AT, "Erase one stroke from a pen page")
      PenActionRegistry.register(ERASE_IN_RECT, "Erase every stroke intersecting a rect")
      PenActionRegistry.register(UPDATE_STYLE, "Change color/width of one stroke")
      PenActionRegistry.register(CLEAR_PAGE, "Clear one pen page")
    }
  }
}
