package io.pagenote.android

import io.pagenote.feature.pen.PenAction
import io.pagenote.feature.pen.PenActionRegistry
import kotlinx.serialization.json.JsonArray

/**
 * Adapts the native PDF annotation snapshot history (`PdfAnnotationEditHistory`)
 * to the unified [PenAction] stack (port batch 1). Each action captures one
 * page's items before/after an edit and restores those snapshots through
 * [apply]; the sink persists the restored `JsonArray` exactly like the
 * results of `PdfAnnotationEditHistory.undo/redo`.
 *
 * The action-shaped flow for an edit is: caller mutates the page, then runs
 * `stack.execute(PdfAnnotationEditHistoryAction(projectId, page, before,
 * after) { items -> persist(items) })`. Undo/redo of the unified stack then
 * replays whole-page snapshots, mirroring `PdfAnnotationEditHistory.record`
 * granularity (one snapshot per edit, never merged).
 */
internal class PdfAnnotationEditHistoryAction(
  private val projectId: String,
  private val page: Int,
  private val before: JsonArray,
  private val after: JsonArray,
  private val apply: (JsonArray) -> Unit,
) : PenAction {
  override val actionId: String = ID
  override val scope: String = "pdf:$projectId:$page"
  override val label: String = "annotation page edit"

  override fun doAction() = apply(after)

  override fun undo() = apply(before)

  override fun redo() = apply(after)

  companion object {
    const val ID = "pdf.annotationSnapshot"

    init {
      PenActionRegistry.register(ID, "Restore one native PDF annotation page snapshot")
    }
  }
}
