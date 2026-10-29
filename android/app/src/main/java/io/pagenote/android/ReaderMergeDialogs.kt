package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 笔记合并对话框（移植差距清单 #15）：页序 / 冲突策略 / 资产去重摘要。
 * 从 ReaderHome 抽出，避免主组合方法超出 JVM 方法大小上限。
 */
@Composable
internal fun NoteMergeStrategyDialog(
  summary: NoteMergePreview?,
  ordering: NoteMergePageOrdering,
  conflict: NoteMergeConflictResolution,
  markdownProject: Boolean,
  onOrderingChange: (NoteMergePageOrdering) -> Unit,
  onConflictChange: (NoteMergeConflictResolution) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_import_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reader_menu_import_confirm_message))
        if (summary != null) {
          Text(
            stringResource(
              R.string.note_merge_summary,
              summary.importedPageCount,
              summary.conflictPages.size,
              summary.uniqueImportedAssetRefs.size,
              summary.importedAssetRefCount,
            ),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        Text(
          stringResource(R.string.note_merge_ordering_label),
          style = MaterialTheme.typography.labelLarge,
        )
        listOf(
          NoteMergePageOrdering.PAGE_ALIGNED to R.string.note_merge_ordering_aligned,
          NoteMergePageOrdering.APPEND_AFTER_LAST to R.string.note_merge_ordering_append,
        ).forEach { (orderingOption, labelRes) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            RadioButton(
              selected = ordering == orderingOption,
              onClick = {
                onOrderingChange(orderingOption)
                if (orderingOption == NoteMergePageOrdering.APPEND_AFTER_LAST) {
                  onConflictChange(NoteMergeConflictResolution.OVERWRITE_WITH_IMPORTED)
                }
              },
              // 追加页序会把笔记接到源页之外，仅对 Markdown 项目开放。
              enabled = orderingOption == NoteMergePageOrdering.PAGE_ALIGNED || markdownProject,
            )
            Text(
              stringResource(labelRes),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
          }
        }
        Text(
          stringResource(R.string.note_merge_conflict_label),
          style = MaterialTheme.typography.labelLarge,
        )
        listOf(
          NoteMergeConflictResolution.OVERWRITE_WITH_IMPORTED to R.string.note_merge_conflict_overwrite,
          NoteMergeConflictResolution.KEEP_CURRENT to R.string.note_merge_conflict_keep,
          NoteMergeConflictResolution.COMBINE_CURRENT_FIRST to R.string.note_merge_conflict_combine,
        ).forEach { (resolution, labelRes) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            RadioButton(
              selected = conflict == resolution,
              onClick = { onConflictChange(resolution) },
              enabled = ordering == NoteMergePageOrdering.PAGE_ALIGNED,
            )
            Text(
              stringResource(labelRes),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(R.string.reader_menu_import_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

/** 合并导出 PDF 对话框：页码范围 + 是否附加笔记页（convert note）。 */
@Composable
internal fun PdfMergeExportDialog(
  rangeInput: String,
  includeNotes: Boolean,
  busy: Boolean,
  onRangeChange: (String) -> Unit,
  onIncludeNotesChange: (Boolean) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.pdf_merge_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pdf_merge_range_label))
        OutlinedTextField(
          value = rangeInput,
          onValueChange = onRangeChange,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pdf_merge_range_input"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = includeNotes,
            onCheckedChange = onIncludeNotesChange,
            modifier = Modifier.testTag("pdf_merge_include_notes"),
          )
          Text(
            stringResource(R.string.pdf_merge_include_notes),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        enabled = !busy,
        modifier = Modifier.testTag("pdf_merge_confirm"),
      ) {
        Text(stringResource(R.string.pdf_merge_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
