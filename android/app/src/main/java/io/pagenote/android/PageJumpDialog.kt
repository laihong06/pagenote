package io.pagenote.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun PageJumpDialog(
  input: String,
  totalPages: Int,
  isRunning: Boolean,
  onInputChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  val target = PageJumpPolicy.parseTarget(input, totalPages)
  AlertDialog(
    modifier = Modifier.testTag("page_jump_dialog"),
    onDismissRequest = { if (!isRunning) onDismiss() },
    properties = DialogProperties(
      dismissOnBackPress = !isRunning,
      dismissOnClickOutside = !isRunning,
    ),
    title = { Text(stringResource(R.string.page_jump_title)) },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = input,
          onValueChange = onInputChange,
          modifier = Modifier.fillMaxWidth().testTag("page_jump_input"),
          label = { Text(stringResource(R.string.page_jump_label)) },
          singleLine = true,
          enabled = !isRunning,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (target == null) {
          Text(
            text = stringResource(R.string.page_jump_invalid, totalPages),
            modifier = Modifier.testTag("page_jump_error").padding(top = 8.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("page_jump_confirm"),
        enabled = target != null && !isRunning,
        onClick = { target?.let(onConfirm) },
      ) {
        Text(stringResource(if (isRunning) R.string.page_jump_saving else R.string.page_jump_go))
      }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("page_jump_cancel"),
        enabled = !isRunning,
        onClick = onDismiss,
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
