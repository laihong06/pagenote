package io.pagenote.android

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import io.pagenote.feature.pen.PenCanvasTool

data class ReaderSessionCheckpoint(
  val accessTreeUri: String,
  val rootDocumentId: String,
  val projectId: String,
  val pageIndex: Int,
  val viewportBookmark: PdfViewportBookmark,
  val penToolName: String,
) {
  fun saveTo(handle: SavedStateHandle) {
    handle[KEY_ACCESS_URI] = accessTreeUri
    handle[KEY_ROOT_DOCUMENT_ID] = rootDocumentId
    handle[KEY_PROJECT_ID] = projectId
    handle[KEY_PAGE_INDEX] = pageIndex
    handle[KEY_VIEWPORT_SCALE] = viewportBookmark.scale
    handle[KEY_VIEWPORT_CENTER_X] = viewportBookmark.centerX
    handle[KEY_VIEWPORT_CENTER_Y] = viewportBookmark.centerY
    handle[KEY_PEN_TOOL] = penToolName
  }

  fun resolvedPenTool(): PenCanvasTool =
    PenCanvasTool.entries.firstOrNull { it.name == penToolName } ?: PenCanvasTool.PEN

  fun toProjectOpenTarget() = ProjectOpenTarget(
    accessTreeUri = Uri.parse(accessTreeUri),
    rootDocumentId = rootDocumentId,
    needsPersistablePermission = false,
  )

  companion object {
    private const val KEY_ACCESS_URI = "reader.accessTreeUri"
    private const val KEY_ROOT_DOCUMENT_ID = "reader.rootDocumentId"
    private const val KEY_PROJECT_ID = "reader.projectId"
    private const val KEY_PAGE_INDEX = "reader.pageIndex"
    private const val KEY_VIEWPORT_SCALE = "reader.viewportScale"
    private const val KEY_VIEWPORT_CENTER_X = "reader.viewportCenterX"
    private const val KEY_VIEWPORT_CENTER_Y = "reader.viewportCenterY"
    private const val KEY_PEN_TOOL = "reader.penTool"

    fun restoreFrom(handle: SavedStateHandle): ReaderSessionCheckpoint? {
      val accessUri = handle.get<String>(KEY_ACCESS_URI)?.takeIf(String::isNotBlank) ?: return null
      val rootId = handle.get<String>(KEY_ROOT_DOCUMENT_ID)?.takeIf(String::isNotBlank) ?: return null
      val projectId = handle.get<String>(KEY_PROJECT_ID)?.takeIf(String::isNotBlank) ?: return null
      val scale = handle.get<Float>(KEY_VIEWPORT_SCALE) ?: PdfViewportTransform.DEFAULT.scale
      val centerX = handle.get<Float>(KEY_VIEWPORT_CENTER_X) ?: 0.5f
      val centerY = handle.get<Float>(KEY_VIEWPORT_CENTER_Y) ?: 0.5f
      val bookmark = if (scale.isFinite() && scale > 0f && centerX.isFinite() && centerY.isFinite()) {
        PdfViewportBookmark(scale, centerX.coerceIn(0f, 1f), centerY.coerceIn(0f, 1f))
      } else {
        PdfViewportBookmark.DEFAULT
      }
      return ReaderSessionCheckpoint(
        accessTreeUri = accessUri,
        rootDocumentId = rootId,
        projectId = projectId,
        pageIndex = (handle.get<Int>(KEY_PAGE_INDEX) ?: 0).coerceAtLeast(0),
        viewportBookmark = bookmark,
        penToolName = handle.get<String>(KEY_PEN_TOOL) ?: PenCanvasTool.PEN.name,
      )
    }
  }
}
