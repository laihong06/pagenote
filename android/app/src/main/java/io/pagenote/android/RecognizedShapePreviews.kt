package io.pagenote.android

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

internal class RecognizedShapePreviews {
  private val previews = mutableStateMapOf<Long, Pair<Int, PdfAnnotationOverlayItem>>()
  private var nextId = 0L

  fun add(page: Int, item: PdfAnnotationOverlayItem): Long {
    val id = ++nextId
    previews[id] = page to item
    return id
  }

  fun remove(id: Long) { previews.remove(id) }

  fun forPage(page: Int): List<PdfAnnotationOverlayItem> =
    previews.values.filter { it.first == page }.map { it.second }
}

@Composable
internal fun rememberRecognizedShapePreviews(projectId: String?, sourceUri: Uri?): RecognizedShapePreviews =
  remember(projectId, sourceUri) { RecognizedShapePreviews() }
