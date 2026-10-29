package io.pagenote.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/**
 * 标题级别对话框。从 ReaderHome 抽出以控制组合方法大小（纯搬移，行为不变）。
 */
@Composable
internal fun ReaderHeadingLevelDialog(
  enabled: Boolean,
  onSelect: (MarkdownParagraphPrefix) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_heading)) },
    text = {
      Column {
        listOf(
          MarkdownParagraphPrefix.HEADING_1 to R.string.reader_menu_heading_level_1,
          MarkdownParagraphPrefix.HEADING_2 to R.string.reader_menu_heading_level_2,
          MarkdownParagraphPrefix.HEADING_3 to R.string.reader_menu_heading_level_3,
        ).forEachIndexed { index, (prefix, labelRes) ->
          TextButton(
            modifier = Modifier.fillMaxWidth().testTag("reader_heading_level_${index + 1}"),
            enabled = enabled,
            onClick = { onSelect(prefix) },
          ) {
            Text(stringResource(labelRes))
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("reader_heading_cancel"),
        onClick = onDismiss,
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

/** 笔记导出格式选择对话框。从 ReaderHome 抽出（纯搬移，行为不变）。 */
@Composable
internal fun ReaderNoteExportDialog(
  onExportJson: () -> Unit,
  onExportCurrentPage: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_export_title)) },
    text = {
      Column {
        TextButton(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.reader_menu_export_json))
        }
        TextButton(onClick = onExportCurrentPage, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.reader_menu_export_current_page))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

/** 挂起的笔记导出恢复对话框。从 ReaderHome 抽出（纯搬移，行为不变）。 */
@Composable
internal fun ReaderPendingNoteExportRecoveryDialog(
  onResume: () -> Unit,
  onDiscard: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_export_recovery_title)) },
    text = { Text(stringResource(R.string.reader_menu_export_recovery_message)) },
    confirmButton = {
      TextButton(onClick = onResume) {
        Text(stringResource(R.string.reader_menu_export_recovery_retry))
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onDiscard) {
          Text(stringResource(R.string.reader_menu_export_recovery_discard))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.cancel))
        }
      }
    },
  )
}
