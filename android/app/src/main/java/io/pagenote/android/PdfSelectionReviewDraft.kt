package io.pagenote.android

import io.pagenote.core.project.PageNotes
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PdfTextSelection
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal class PdfSelectionReviewDraft private constructor(
  val page: Int,
  val quote: String,
  val context: String,
  val revision: String,
  val targetNoteRevision: String,
  val anchor: kotlinx.serialization.json.JsonObject,
) {
  fun requireCurrentNote(noteText: String) {
    require(PageNotes.revision(noteText) == targetNoteRevision) {
      "note page changed since PDF selection capture"
    }
  }

  companion object {
    fun capture(
      page: Int,
      source: PdfPageText,
      selection: PdfTextSelection,
      targetNoteRevision: String,
    ): PdfSelectionReviewDraft {
      require(page >= 1) { "page must be one-based" }
      require(source.text.length <= 1_000_000) { "PDF page context is too large" }
      require(selection.quote.length <= 100_000) { "PDF selection is too large" }
      require(selection.start >= 0 && selection.start < selection.end && selection.end <= source.text.length) {
        "PDF selection is outside page text"
      }
      require(source.text.substring(selection.start, selection.end) == selection.quote) {
        "PDF selection quote does not match page text"
      }
      require(!selection.quote.isBlank()) { "PDF selection must contain text" }
      require(targetNoteRevision.isNotBlank()) { "target note revision is blank" }
      require(source.revision == PageNotes.revision(source.text)) { "PDF page revision does not match its text" }

      val bounds = selection.toWidth1000Bounds(source.pageWidth)
      val anchor = buildJsonObject {
        put("coordinateSystem", JsonPrimitive("width-1000"))
        put("text", JsonPrimitive(selection.quote))
        put("shape", buildJsonObject {
          put("type", JsonPrimitive("rect"))
          put("points", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.buildJsonArray {
              add(JsonPrimitive(bounds.left))
              add(JsonPrimitive(bounds.top))
            })
            add(kotlinx.serialization.json.buildJsonArray {
              add(JsonPrimitive(bounds.right))
              add(JsonPrimitive(bounds.bottom))
            })
          })
          put("color", JsonPrimitive("#f5d870"))
          put("width", JsonPrimitive(1))
        })
      }
      return PdfSelectionReviewDraft(
        page = page,
        quote = selection.quote,
        context = source.text,
        revision = source.revision,
        targetNoteRevision = targetNoteRevision,
        anchor = anchor,
      )
    }
  }
}
