package io.pagenote.android

import io.pagenote.feature.annotations.AnnotationTextAlign
import io.pagenote.feature.annotations.AnnotationTextStyleEditor
import io.pagenote.feature.pen.PenAction
import io.pagenote.feature.pen.PenActionRegistry
import kotlinx.serialization.json.JsonArray

/**
 * Unified-stack undo action for text-tool style edits (port batch 4), the
 * UpdateTextStyle counterpart of the batch-1 [PdfAnnotationEditHistoryAction]
 * adapter: it captures one page's items before/after a text style edit and
 * restores those snapshots through [apply].
 *
 * Unlike the generic snapshot action, consecutive text style edits of the same
 * scope coalesce into one undo step (mirroring [io.pagenote.feature.pen.PenPageStoreActions]
 * UPDATE_STYLE merging): undo restores the first `before`, redo restores the
 * last `after`.
 *
 * The reader UI keeps driving the built-in `PdfAnnotationEditHistory` for page
 * undo/redo; this action exposes the same edit to the unified [io.pagenote.feature.pen.PenActionStack]
 * with style-edit granularity.
 */
internal class AnnotationUpdateTextStyleAction(
  private val projectId: String,
  private val page: Int,
  private val before: JsonArray,
  private val after: JsonArray,
  private val apply: (JsonArray) -> Unit,
) : PenAction {
  override val actionId: String = ID
  override val scope: String = "pdf:$projectId:$page"
  override val label: String = "annotation text style"

  override fun doAction() = apply(after)

  override fun undo() = apply(before)

  override fun redo() = apply(after)

  override fun merge(next: PenAction): PenAction? {
    if (next !is AnnotationUpdateTextStyleAction || next.scope != scope) return null
    return Merged(this, next)
  }

  /** Undo restores the first `before`, redo restores the last `after`. */
  private class Merged(
    private val original: AnnotationUpdateTextStyleAction,
    private val successor: AnnotationUpdateTextStyleAction,
  ) : PenAction {
    override val actionId = original.actionId
    override val scope = original.scope
    override val label = original.label

    override fun doAction() = successor.redo()

    override fun undo() = original.undo()

    override fun redo() = successor.redo()

    override fun merge(next: PenAction): PenAction? {
      if (next !is AnnotationUpdateTextStyleAction || next.scope != scope) return null
      return Merged(original, next)
    }
  }

  companion object {
    const val ID = "pdf.annotationTextStyle"

    init {
      PenActionRegistry.register(ID, "Restore one text annotation style edit")
    }

    /**
     * Builds the action for updating the text style of item [index] on one
     * page via [AnnotationTextStyleEditor.updateTextStyle]. Returns null when
     * the target is not a text item, a requested value is invalid, or the
     * edit is a content no-op (nothing recorded — unlike add/erase actions,
     * the builder can detect the no-op before executing).
     */
    fun create(
      projectId: String,
      page: Int,
      items: JsonArray,
      index: Int,
      color: String? = null,
      fontId: String? = null,
      fontSize: Double? = null,
      align: AnnotationTextAlign? = null,
      apply: (JsonArray) -> Unit,
    ): AnnotationUpdateTextStyleAction? {
      val after = AnnotationTextStyleEditor.updateTextStyle(
        page = items,
        index = index,
        color = color,
        fontId = fontId,
        fontSize = fontSize,
        align = align,
      ) ?: return null
      if (after == items) return null
      return AnnotationUpdateTextStyleAction(projectId, page, items, after, apply)
    }
  }
}
