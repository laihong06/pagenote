package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PdfTextSelection
import io.pagenote.core.reader.PreparedCache
import io.pagenote.core.reader.PreparedManifest
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ReaderOcrMode { IDLE, SELECTING, RECOGNIZING, RESULT }

data class ReaderOcrState(
  val mode: ReaderOcrMode = ReaderOcrMode.IDLE,
  val pageIndex: Int? = null,
  val selection: NormalizedOcrRegion? = null,
  val selectionInvalid: Boolean = false,
) {
  val inputLocked: Boolean get() = mode != ReaderOcrMode.IDLE

  fun enterSelection(pageIndex: Int) = ReaderOcrState(
    mode = ReaderOcrMode.SELECTING,
    pageIndex = pageIndex,
  )

  fun withSelection(region: NormalizedOcrRegion?): ReaderOcrState = when {
    mode != ReaderOcrMode.SELECTING -> this
    region != null -> copy(selection = region, selectionInvalid = false)
    selection != null -> copy(selectionInvalid = false)
    else -> copy(selectionInvalid = true)
  }

  fun beginRecognition(): ReaderOcrState? =
    if (mode == ReaderOcrMode.SELECTING && selection != null) {
      copy(mode = ReaderOcrMode.RECOGNIZING)
    } else {
      null
    }

  fun showResult() = if (mode == ReaderOcrMode.RECOGNIZING) copy(mode = ReaderOcrMode.RESULT) else this
  fun dismissResult() = if (mode == ReaderOcrMode.RESULT) ReaderOcrState() else this
  fun cancel() = ReaderOcrState()
}

@Composable
internal fun ReaderOcrActions(
  state: ReaderOcrState,
  currentPage: Int,
  enabled: Boolean = true,
  onStateChange: (ReaderOcrState) -> Unit,
  onRecognize: (ReaderOcrState) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    when (state.mode) {
      ReaderOcrMode.IDLE -> Button(
        modifier = Modifier.testTag("region_ocr_action"),
        enabled = enabled,
        onClick = { onStateChange(ReaderOcrState().enterSelection(currentPage)) },
      ) { Text(stringResource(R.string.region_ocr_action)) }

      ReaderOcrMode.SELECTING -> {
        Text(stringResource(R.string.ocr_selection_hint))
        if (state.selectionInvalid) {
          Text(
            modifier = Modifier.testTag("ocr_selection_invalid"),
            text = stringResource(R.string.ocr_selection_invalid),
          )
        }
        Button(
          modifier = Modifier.testTag("recognize_region_action"),
          enabled = state.selection != null,
          onClick = {
            state.beginRecognition()?.let { running ->
              onStateChange(running)
              onRecognize(running)
            }
          },
        ) { Text(stringResource(R.string.recognize_region_action)) }
        TextButton(
          modifier = Modifier.testTag("cancel_region_selection"),
          onClick = { onStateChange(state.cancel()) },
        ) { Text(stringResource(R.string.cancel)) }
      }

      ReaderOcrMode.RECOGNIZING -> Text(
        modifier = Modifier.testTag("recognizing_region_status"),
        text = stringResource(R.string.recognizing_region),
      )

      ReaderOcrMode.RESULT -> Unit
    }
  }
}

@Composable
internal fun OcrSelectionOverlay(
  selection: NormalizedOcrRegion?,
  modifier: Modifier = Modifier,
) {
  if (selection == null) return
  val color = MaterialTheme.colorScheme.primary
  Canvas(modifier = modifier.fillMaxSize()) {
    val left = selection.left / 1000f * size.width
    val top = selection.top / 1000f * size.height
    val right = selection.right / 1000f * size.width
    val bottom = selection.bottom / 1000f * size.height
    drawRect(
      color = color.copy(alpha = 0.16f),
      topLeft = Offset(left, top),
      size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
    )
    drawRect(
      color = color,
      topLeft = Offset(left, top),
      size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
      style = Stroke(width = 2.dp.toPx()),
    )
  }
}

@Composable
internal fun PdfTextSelectionActions(
  selection: PdfTextSelection?,
  onCreateReview: () -> Unit,
  onCancel: () -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = stringResource(R.string.pdf_text_selection_hint),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    selection?.let {
      Text(
        text = it.quote,
        modifier = Modifier.widthIn(max = 220.dp).testTag("pdf_text_selection_quote"),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Button(
      modifier = Modifier.testTag("pdf_text_selection_review"),
      enabled = selection != null,
      onClick = onCreateReview,
    ) { Text(stringResource(R.string.reader_menu_selection_to_annotation)) }
    TextButton(
      modifier = Modifier.testTag("pdf_text_selection_cancel"),
      onClick = onCancel,
    ) { Text(stringResource(R.string.cancel)) }
  }
}

@Composable
internal fun PdfTextSelectionOverlay(
  pageText: PdfPageText?,
  selection: PdfTextSelection?,
  modifier: Modifier = Modifier,
) {
  if (pageText == null || selection == null || pageText.pageWidth <= 0.0 || pageText.pageHeight <= 0.0) return
  val color = MaterialTheme.colorScheme.primary
  Canvas(modifier = modifier.fillMaxSize()) {
    selection.glyphBounds.forEach { bounds ->
      val left = (bounds.left / pageText.pageWidth * size.width).toFloat()
      val top = (bounds.top / pageText.pageHeight * size.height).toFloat()
      val right = (bounds.right / pageText.pageWidth * size.width).toFloat()
      val bottom = (bounds.bottom / pageText.pageHeight * size.height).toFloat()
      drawRect(
        color = color.copy(alpha = 0.24f),
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
      )
    }
  }
}

data class PreparedOcrImages(val pagePng: ByteArray, val regionPng: ByteArray)

data class RegionOcrOutcome(val result: OcrResult, val fromCache: Boolean)

class RegionOcrProcessor(private val cache: PreparedRegionCacheStore) {
  suspend fun recognize(
    srcDirectoryUri: String,
    documentId: String,
    page: Int,
    sourceRevision: String?,
    selection: NormalizedOcrRegion,
    refresh: Boolean = false,
    imageWidth: Int,
    imageHeight: Int,
    renderScale: Double,
    engine: String,
    notCachedWarning: String,
    recognize: suspend () -> OcrResult,
    encodeImages: suspend () -> PreparedOcrImages,
  ): RegionOcrOutcome {
    require(renderScale.isFinite() && renderScale > 0) { "bad render scale" }
    val region = selection.toPageRegion()
    if (sourceRevision != null && !refresh) {
      val cached = try {
        withContext(Dispatchers.IO) {
          cache.read(srcDirectoryUri, documentId, page, sourceRevision, region, engine)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
      if (cached != null) return RegionOcrOutcome(cached.result, fromCache = true)
    }

    var result = recognize()
    if (result.status !in CACHEABLE_STATUSES) return RegionOcrOutcome(result, fromCache = false)
    if (sourceRevision == null) {
      return RegionOcrOutcome(result.withWarning(notCachedWarning), fromCache = false)
    }

    try {
      val images = withContext(Dispatchers.IO) { encodeImages() }
      val key = PreparedCache.cacheKey(sourceRevision, "region-ocr", region, engine)
      val ocrText = OcrResultPolicy.textOf(result)
      val manifest = PreparedManifest(
        documentId = documentId,
        page = page,
        sourceRevision = sourceRevision,
        kind = "pdf",
        region = region,
        cacheKey = key,
        width = imageWidth,
        height = imageHeight,
        scale = renderScale,
        ocrCharacters = ocrText.length,
        textCharacters = ocrText.length,
        blockCount = result.blocks.size,
        warnings = result.warnings,
        files = CACHE_FILES,
      )
      val writeResult = withContext(Dispatchers.IO) {
        cache.write(srcDirectoryUri, manifest, result, images.pagePng, images.regionPng)
      }
      if (writeResult is PreparedOcrCacheWriteResult.NotSaved) {
        result = result.withWarning(notCachedWarning)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      result = result.withWarning(notCachedWarning)
    }
    return RegionOcrOutcome(result, fromCache = false)
  }

  private fun OcrResult.withWarning(warning: String) = copy(warnings = (warnings + warning).distinct())

  private companion object {
    val CACHEABLE_STATUSES = setOf("ok", "partial")
    val CACHE_FILES = listOf("manifest.json", "page.png", "region.png", "ocr.txt", "blocks.json")
  }
}

fun NormalizedOcrRegion.toPageRegion(): PageRegion = PageRegion(
  x = left.toDouble(),
  y = top.toDouble(),
  width = (right - left).toDouble(),
  height = (bottom - top).toDouble(),
)

fun NormalizedOcrRegion.toEngineRegion() = io.pagenote.feature.ocr.NormalizedOcrRegion(
  left = left,
  top = top,
  right = right,
  bottom = bottom,
)

internal fun Bitmap.toPngBytes(): ByteArray {
  val output = java.io.ByteArrayOutputStream()
  check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode OCR image" }
  return output.toByteArray()
}
