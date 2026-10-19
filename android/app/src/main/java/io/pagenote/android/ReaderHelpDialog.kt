package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal enum class ReaderHelpTopic(val titleRes: Int, val contentRes: Int) {
  ABOUT(R.string.reader_menu_about, R.string.help_about_content),
  SHORTCUTS(R.string.reader_menu_shortcuts, R.string.help_shortcuts_content),
  PRIVACY_LICENSE(R.string.reader_menu_privacy_license, R.string.help_privacy_license_content),
  DIAGNOSTICS(R.string.reader_menu_diagnostics, R.string.help_diagnostics_content),
}

@Composable
internal fun ReaderHelpDialog(
  topic: ReaderHelpTopic,
  appVersion: String,
  diagnostics: String,
  onCopyDiagnostics: () -> Unit,
  onShareDiagnostics: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    modifier = Modifier.testTag("reader_help_dialog"),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(topic.titleRes)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (topic == ReaderHelpTopic.DIAGNOSTICS) {
          Text(stringResource(topic.contentRes))
          Text(
            diagnostics,
            modifier = Modifier.testTag("help_diagnostics"),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          Text(stringResource(topic.contentRes, appVersion))
        }
      }
    },
    confirmButton = {
      if (topic == ReaderHelpTopic.DIAGNOSTICS) {
        Row {
          TextButton(
            modifier = Modifier.testTag("help_copy_diagnostics"),
            onClick = onCopyDiagnostics,
          ) { Text(stringResource(R.string.help_copy)) }
          TextButton(
            modifier = Modifier.testTag("help_share_diagnostics"),
            onClick = onShareDiagnostics,
          ) { Text(stringResource(R.string.help_share)) }
        }
      } else {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      }
    },
    dismissButton = {
      if (topic == ReaderHelpTopic.DIAGNOSTICS) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      }
    },
  )
}
