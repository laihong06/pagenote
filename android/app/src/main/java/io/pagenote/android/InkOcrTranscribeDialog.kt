package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultPolicy

/**
 * 手写转写对话框（移植批次 6）：选区墨迹 → 离线 OCR → 可编辑文本。
 *
 * 识别结果整页拼为一段可编辑正文（行间以换行保留块序）；状态/告警显式
 * 呈现（低置信度等）；确认时以一个 OcrConvert 动作落盘（是否移除原笔迹
 * 由开关控制，缺省移除——对齐 touchnotes 的"笔迹转文字"语义）。
 */
@Composable
internal fun InkOcrTranscribeDialog(
  page: Int,
  result: OcrResult,
  onConfirm: (text: String, removeInk: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val initialText = remember(result) {
    result.blocks.joinToString("\n") { it.text }.trim()
  }
  var text by remember(result) { mutableStateOf(initialText) }
  var removeInk by remember { mutableStateOf(true) }
  val needsReview = OcrResultPolicy.needsVisualReview(result)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.ink_ocr_dialog_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(
            R.string.ocr_status,
            result.status,
            result.engine,
          ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (needsReview) {
          Text(
            modifier = Modifier.testTag("ink_ocr_review_warning"),
            text = stringResource(R.string.ocr_review),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          modifier = Modifier.fillMaxWidth().testTag("ink_ocr_text"),
          label = { Text(stringResource(R.string.ink_ocr_text_label)) },
          minLines = 3,
          maxLines = 8,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Switch(
            checked = removeInk,
            onCheckedChange = { removeInk = it },
            modifier = Modifier.testTag("ink_ocr_remove_ink"),
          )
          Text(
            text = stringResource(R.string.ink_ocr_remove_ink),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("ink_ocr_confirm"),
        enabled = text.isNotBlank(),
        onClick = { onConfirm(text, removeInk) },
      ) { Text(stringResource(R.string.ink_ocr_insert)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("ink_ocr_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

/**
 * 墨迹选区动作条（手写 OCR 与公式识别共用）：SELECTING 态显示提示、识
 * 别与取消；RECOGNIZING 态显示进行中状态。复用 ReaderOcrState 的选区
 * 状态机，交互与区域 OCR 一致。
 */
@Composable
internal fun InkSelectionActions(
  state: ReaderOcrState,
  hint: String,
  recognizeLabel: String,
  recognizeTag: String,
  enabled: Boolean = true,
  onStateChange: (ReaderOcrState) -> Unit,
  onRecognize: (ReaderOcrState) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    when (state.mode) {
      ReaderOcrMode.IDLE -> Unit
      ReaderOcrMode.SELECTING -> {
        Text(hint, style = MaterialTheme.typography.labelMedium)
        if (state.selectionInvalid) {
          Text(
            modifier = Modifier.testTag("ink_selection_invalid"),
            text = stringResource(R.string.ocr_selection_invalid),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
        Button(
          modifier = Modifier.testTag(recognizeTag),
          enabled = enabled && state.selection != null,
          onClick = {
            state.beginRecognition()?.let { running ->
              onStateChange(running)
              onRecognize(running)
            }
          },
        ) { Text(recognizeLabel) }
        TextButton(onClick = { onStateChange(state.cancel()) }) {
          Text(stringResource(R.string.cancel))
        }
      }
      ReaderOcrMode.RECOGNIZING -> Text(
        modifier = Modifier.testTag("ink_selection_recognizing"),
        text = stringResource(R.string.recognizing_region),
        style = MaterialTheme.typography.labelMedium,
      )
      ReaderOcrMode.RESULT -> Unit
    }
  }
}
