package io.pagenote.feature.pen

/**
 * Unified undo/redo action abstraction (port batch 1).
 *
 * An action captures one user-visible mutation so it can be re-applied
 * ([redo]) and reversed ([undo]) through a [PenActionStack]. Every action
 * belongs to a [scope] (e.g. `"document:1"` or `"pdf:<projectId>:<page>"`);
 * undo/redo never crosses scopes, mirroring the page-scoped semantics of
 * [PenPageStore] and `PdfAnnotationEditHistory`.
 *
 * `do` is a Kotlin keyword, so the first apply is [doAction].
 */
interface PenAction {
  /** Registry id, e.g. `"pen.addStroke"`. Must be registered in [PenActionRegistry]. */
  val actionId: String

  /** Stack scope this action belongs to. */
  val scope: String

  /** Short human-readable label for undo/redo UI. */
  val label: String

  /** Applies the mutation the first time, when the action is recorded. */
  fun doAction()

  /** Reverses the mutation. */
  fun undo()

  /** Re-applies the mutation after an [undo]. */
  fun redo()

  /**
   * Attempts to coalesce [next] — the immediately following action recorded
   * in the same scope — into this action. Returns the merged action, or null
   * when the two actions must stay separate undo steps. The default never
   * merges; only actions that opt in (e.g. consecutive style edits) do.
   */
  fun merge(next: PenAction): PenAction? = null
}
