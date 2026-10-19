package io.pagenote.android

import io.pagenote.agent.PageTutorFocus
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultPolicy

internal data class PageTutorRegionContext(
  val projectId: String,
  val projectUri: String,
  val pageIndex: Int,
  val sourceRevision: String?,
  val region: NormalizedOcrRegion,
  val rendererIdentity: Any,
  val focus: PageTutorFocus,
) {
  fun matches(
    projectId: String,
    projectUri: String,
    pageIndex: Int,
    sourceRevision: String?,
    rendererIdentity: Any,
  ): Boolean = this.projectId == projectId && this.projectUri == projectUri &&
    this.pageIndex == pageIndex && this.sourceRevision == sourceRevision &&
    this.rendererIdentity === rendererIdentity

  companion object {
    fun fromResult(
      projectId: String,
      projectUri: String,
      pageIndex: Int,
      sourceRevision: String?,
      region: NormalizedOcrRegion,
      rendererIdentity: Any,
      result: OcrResult,
      label: String,
      reviewWarning: String,
    ): PageTutorRegionContext? {
      if (result.status !in setOf("ok", "partial")) return null
      val blocks = result.blocks.filter { it.text.isNotBlank() }
      if (blocks.isEmpty()) return null

      val warnings = result.warnings.filter(String::isNotBlank).toMutableList()
      if (OcrResultPolicy.needsVisualReview(result) && warnings.isEmpty()) {
        warnings += reviewWarning
      }
      val focusText = blocks.mapIndexed { index, block ->
        "${index + 1}. ${block.text.trim()} (${(block.confidence * 100).toInt()}%)"
      }.joinToString("\n")

      return PageTutorRegionContext(
        projectId = projectId,
        projectUri = projectUri,
        pageIndex = pageIndex,
        sourceRevision = sourceRevision,
        region = region,
        rendererIdentity = rendererIdentity,
        focus = PageTutorFocus(label = label, text = focusText, warnings = warnings.distinct()),
      )
    }

    fun isUsable(result: OcrResult): Boolean = result.status in setOf("ok", "partial") &&
      result.blocks.any { it.text.isNotBlank() }
  }
}
