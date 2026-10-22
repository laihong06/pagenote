package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.pagenote.feature.yuketang.RainCourse
import io.pagenote.feature.yuketang.RainLesson

internal enum class RainProjectMode { IMPORT, LIVE }

internal data class RainProjectRequest(val course: RainCourse, val lesson: RainLesson, val mode: RainProjectMode)

@Composable
internal fun RainProjectDialog(
  mode: RainProjectMode,
  name: String,
  folder: String?,
  consent: Boolean,
  busy: Boolean,
  error: String?,
  onNameChange: (String) -> Unit,
  onChooseFolder: () -> Unit,
  onConsentChange: (Boolean) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  folderLocked: Boolean = false,
) {
  val live = mode == RainProjectMode.LIVE
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(stringResource(if (live) R.string.rain_new_live_project else R.string.rain_import_button)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name, onValueChange = onNameChange, enabled = !busy, singleLine = true,
          label = { Text(stringResource(R.string.new_project_name)) },
          modifier = Modifier.fillMaxWidth().testTag("rain_project_name"),
        )
        TextButton(onClick = onChooseFolder, enabled = !busy && !folderLocked, modifier = Modifier.testTag("rain_project_folder")) {
          Text(folder ?: stringResource(R.string.new_project_choose_dir), maxLines = 2)
        }
        if (live) {
          Text(stringResource(R.string.rain_live_warning), color = MaterialTheme.colorScheme.error)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = onConsentChange, enabled = !busy, modifier = Modifier.testTag("rain_project_consent"))
            Text(stringResource(R.string.rain_live_consent), modifier = Modifier.weight(1f))
          }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        enabled = !busy && name.isNotBlank() && folder != null && (!live || consent),
        modifier = Modifier.testTag("rain_project_confirm"),
      ) { Text(stringResource(if (busy) R.string.new_project_creating else if (live) R.string.rain_new_live_project else R.string.rain_import_button)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("rain_project_cancel")) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
