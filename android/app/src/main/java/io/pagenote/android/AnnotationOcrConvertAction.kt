package io.pagenote.android

import io.pagenote.feature.pen.PenAction
import io.pagenote.feature.pen.PenActionRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Unified-stack undo action for handwriting-OCR conversions (port batch 6):
 * one OcrConvert edit = remove the transcribed ink items + append the
 * transcription product (text item for handwriting OCR, formula image item
 * for LaTeX OCR). It captures the page before/after and restores those
 * snapshots through [apply] — the same snapshot semantics as the batch-1
 * [PdfAnnotationEditHistoryAction] adapter and the batch-4
 * [AnnotationUpdateTextStyleAction] (no coalescing: each conversion is one
 * undo step).
 *
 * The reader UI keeps driving the built-in `PdfAnnotationEditHistory` for page
 * undo/redo; this action exposes the conversion to the unified
 * [io.pagenote.feature.pen.PenActionStack].
 */
internal class AnnotationOcrConvertAction(
  private val projectId: String,
  private val page: Int,
  private val before: JsonArray,
  private val after: JsonArray,
  private val apply: (JsonArray) -> Unit,
) : PenAction {
  override val actionId: String = ID
  override val scope: String = "pdf:$projectId:$page"
  override val label: String = "ink OCR convert"

  override fun doAction() = apply(after)

  override fun undo() = apply(before)

  override fun redo() = apply(after)

  companion object {
    const val ID = "pdf.ocrConvert"

    init {
      PenActionRegistry.register(ID, "Restore one handwriting OCR conversion")
    }

    /**
     * Builds the action for one conversion. Returns null when the converted
     * page is identical to the input (nothing recorded).
     */
    fun create(
      projectId: String,
      page: Int,
      before: JsonArray,
      after: JsonArray,
      apply: (JsonArray) -> Unit,
    ): AnnotationOcrConvertAction? {
      if (after == before) return null
      return AnnotationOcrConvertAction(projectId, page, before, after, apply)
    }
  }
}
