package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.pagenote.feature.audio.AudioRecordingPolicy

/**
 * 录音进行中模态（移植批次 8）：计时 + 电平条 + 停止落点/取消。模态形式
 * 避免与画布手势/菜单状态机交织；PREPARING 阶段同样展示（按钮禁用）。
 */
@Composable
internal fun AudioRecordingDialog(
  preparing: Boolean,
  elapsedMs: Long,
  level: Float,
  onStop: () -> Unit,
  onCancel: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { /* 模态：必须显式停止或取消 */ },
    title = { Text(stringResource(R.string.audio_recording_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = if (preparing) stringResource(R.string.audio_recording_preparing)
          else AudioRecordingPolicy.elapsedLabel(elapsedMs),
          modifier = Modifier.testTag("audio_recording_elapsed"),
          style = MaterialTheme.typography.headlineSmall,
        )
        LinearProgressIndicator(
          progress = { level.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().testTag("audio_recording_level"),
        )
        Text(
          text = stringResource(R.string.audio_recording_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onStop,
        enabled = !preparing,
        modifier = Modifier.testTag("audio_recording_stop"),
      ) { Text(stringResource(R.string.audio_recording_stop)) }
    },
    dismissButton = {
      Row(modifier = Modifier.padding(end = 8.dp)) {
        TextButton(
          onClick = onCancel,
          enabled = !preparing,
          modifier = Modifier.testTag("audio_recording_cancel"),
        ) { Text(stringResource(R.string.audio_recording_cancel)) }
      }
    },
  )
}
