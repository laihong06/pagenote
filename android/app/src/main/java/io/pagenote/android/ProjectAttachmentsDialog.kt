package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ProjectAttachmentsDialog(
  entries: List<ProjectAttachmentEntry>,
  isLoading: Boolean,
  isImporting: Boolean,
  isDeleting: Boolean = false,
  errorMessage: String?,
  onImport: () -> Unit,
  onInsertImage: (ProjectAttachmentEntry) -> Unit,
  onDelete: (ProjectAttachmentEntry) -> Unit,
  onDismiss: () -> Unit,
) {
  var pendingDelete by remember { mutableStateOf<ProjectAttachmentEntry?>(null) }

  AlertDialog(
    modifier = Modifier.testTag("project_attachments_dialog"),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.project_attachments_title)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isLoading) Text(stringResource(R.string.project_attachments_loading))
        if (isImporting) Text(stringResource(R.string.project_attachments_importing))
        if (isDeleting) Text(stringResource(R.string.project_attachments_deleting))
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!isLoading && entries.isEmpty() && errorMessage == null) {
          Text(stringResource(R.string.project_attachments_empty))
        }
        entries.forEachIndexed { index, entry ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                entry.assetName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
              )
              entry.sizeBytes?.let { size ->
                Text(
                  stringResource(R.string.project_attachments_size_bytes, size),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (entry.canInsertImage) {
              TextButton(
                modifier = Modifier.testTag("attachment_insert_$index"),
                enabled = !isLoading && !isImporting && !isDeleting,
                onClick = { onInsertImage(entry) },
              ) { Text(stringResource(R.string.project_attachments_insert)) }
            }
            TextButton(
              modifier = Modifier.testTag("attachment_delete_$index"),
              enabled = !isLoading && !isImporting && !isDeleting,
              onClick = { pendingDelete = entry },
            ) { Text(stringResource(R.string.project_attachments_delete)) }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("attachment_import"),
        enabled = !isLoading && !isImporting && !isDeleting,
        onClick = onImport,
      ) { Text(stringResource(R.string.project_attachments_import)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )

  pendingDelete?.let { entry ->
    AlertDialog(
      modifier = Modifier.testTag("attachment_delete_confirmation"),
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.project_attachments_delete_title)) },
      text = { Text(stringResource(R.string.project_attachments_delete_message, entry.assetName)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("attachment_delete_confirm"),
          onClick = {
            pendingDelete = null
            onDelete(entry)
          },
        ) { Text(stringResource(R.string.project_attachments_delete)) }
      },
      dismissButton = {
        TextButton(
          modifier = Modifier.testTag("attachment_delete_cancel"),
          onClick = { pendingDelete = null },
        ) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}
