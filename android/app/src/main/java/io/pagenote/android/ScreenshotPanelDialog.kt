package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.pagenote.feature.ocr.OcrResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 系统截屏面板（移植差距清单 #23）：预览 MediaProjection 抓到的帧，
 * 提供 OCR（复用 feature-ocr 离线引擎）与取色两类动作；结果可插回笔记。
 * 取色在未选色状态下点按预览生效；OCR 与取色结果互相独立展示。
 */
@Composable
internal fun ScreenshotPanelDialog(
  frame: ScreenCaptureFrame,
  runOcr: suspend (Bitmap) -> OcrResult,
  onInsertText: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val ocrEmptyMessage = stringResource(R.string.screen_capture_ocr_empty)
  var isOcrRunning by remember { mutableStateOf(false) }
  var ocrText by remember { mutableStateOf<String?>(null) }
  var ocrError by remember { mutableStateOf<String?>(null) }
  var picked by remember { mutableStateOf<PickedColor?>(null) }
  var status by remember { mutableStateOf<String?>(null) }
  var previewSize by remember { mutableStateOf(IntSize.Zero) }

  AlertDialog(
    onDismissRequest = { if (!isOcrRunning) onDismiss() },
    title = { Text(stringResource(R.string.screen_capture_panel_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_capture_preview")
            .onSizeChanged { previewSize = it }
            .pointerInput(frame.capturedAtMillis) {
              detectTapGestures { offset ->
                val bitmap = frame.bitmap
                if (previewSize.width > 0 && previewSize.height > 0 && bitmap.width > 0) {
                  val (x, y) = ColorPicking.pixelAt(
                    normalizedX = offset.x / previewSize.width.toFloat(),
                    normalizedY = offset.y / previewSize.height.toFloat(),
                    width = bitmap.width,
                    height = bitmap.height,
                  )
                  picked = ColorPicking.pick(bitmap.getPixel(x, y))
                }
              }
            },
        ) {
          Image(
            bitmap = frame.bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.screen_capture_preview_description),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Text(
          stringResource(R.string.screen_capture_tap_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        picked?.let { color ->
          Text(
            stringResource(
              R.string.screen_capture_picked_color,
              color.hex,
              color.nearest.nameZh,
              color.nearest.nameEn,
            ),
            modifier = Modifier.testTag("screen_capture_picked_color"),
            style = MaterialTheme.typography.bodyMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
              status = null
              onInsertText(color.hex)
            }) { Text(stringResource(R.string.screen_capture_insert)) }
            TextButton(onClick = { picked = null }) {
              Text(stringResource(R.string.screen_capture_pick_again))
            }
          }
        }
        when {
          isOcrRunning -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            Text(stringResource(R.string.screen_capture_ocr_running))
          }
          ocrError != null -> Text(
            ocrError.orEmpty(),
            modifier = Modifier.testTag("screen_capture_ocr_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
          ocrText != null -> {
            Text(
              ocrText.orEmpty(),
              modifier = Modifier.testTag("screen_capture_ocr_text"),
              style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(onClick = { onInsertText(ocrText.orEmpty()) }) {
                Text(stringResource(R.string.screen_capture_insert))
              }
            }
          }
        }
        status?.let {
          Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        if (!isOcrRunning) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              modifier = Modifier.testTag("screen_capture_ocr"),
              onClick = {
                scope.launch {
                  isOcrRunning = true
                  ocrError = null
                  ocrText = null
                  status = null
                  try {
                    val result = runOcr(frame.bitmap)
                    val text = result.blocks.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text }
                    ocrText = text?.takeIf(String::isNotBlank)
                    if (ocrText == null) {
                      ocrError = result.warnings.firstOrNull() ?: ocrEmptyMessage
                    }
                  } catch (cancelled: CancellationException) {
                    throw cancelled
                  } catch (error: Exception) {
                    ocrError = error.message ?: ocrEmptyMessage
                  } finally {
                    isOcrRunning = false
                  }
                }
              },
            ) { Text(stringResource(R.string.screen_capture_run_ocr)) }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !isOcrRunning,
        onClick = onDismiss,
      ) { Text(stringResource(R.string.close)) }
    },
  )
}
