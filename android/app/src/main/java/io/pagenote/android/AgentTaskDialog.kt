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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.DialogProperties
import io.pagenote.agent.AgentEvent
import io.pagenote.agent.SerialPages

@Composable
internal fun AgentTaskDialog(
  page: Int,
  instruction: String,
  pageRange: String,
  report: SerialPages.Report?,
  isRunning: Boolean,
  errorMessage: String?,
  event: AgentEvent?,
  onInstructionChange: (String) -> Unit,
  onPageRangeChange: (String) -> Unit,
  onRun: (String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!isRunning) onDismiss() },
    properties = DialogProperties(
      dismissOnBackPress = !isRunning,
      dismissOnClickOutside = !isRunning,
    ),
    title = { Text(stringResource(R.string.agent_task_title, page)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = instruction,
          onValueChange = onInstructionChange,
          modifier = Modifier.fillMaxWidth().testTag("agent_task_instruction"),
          label = { Text(stringResource(R.string.agent_task_instruction)) },
          minLines = 2,
          maxLines = 4,
          enabled = !isRunning,
        )
        OutlinedTextField(
          value = pageRange,
          onValueChange = onPageRangeChange,
          modifier = Modifier.fillMaxWidth().testTag("agent_task_pages"),
          label = { Text(stringResource(R.string.agent_task_pages)) },
          singleLine = true,
          enabled = !isRunning,
        )
        if (isRunning) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.agent_task_running))
          }
        }
        errorMessage?.let { message ->
          Text(
            text = message,
            modifier = Modifier.testTag("agent_task_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        report?.let { batch ->
          Text(
            text = stringResource(
              R.string.agent_task_batch,
              batch.completed.joinToString(", ").ifBlank { "-" },
              batch.updated.joinToString(", ").ifBlank { "-" },
              batch.incomplete.joinToString(", ").ifBlank { "-" },
            ),
            modifier = Modifier.testTag("agent_task_batch"),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        when (event) {
          is AgentEvent.Done -> {
            Text(
              text = stringResource(
                R.string.agent_task_done,
                event.pages.joinToString(", ").ifBlank { "-" },
                event.updated.joinToString(", ").ifBlank { "-" },
              ),
              modifier = Modifier.testTag("agent_task_result"),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          is AgentEvent.Failed -> {
            Text(
              text = stringResource(R.string.agent_task_failed, event.reason),
              modifier = Modifier.testTag("agent_task_result"),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          is AgentEvent.ToolCall -> {
            Text(
              text = stringResource(R.string.agent_task_failed, event.name),
              modifier = Modifier.testTag("agent_task_result"),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          null -> Unit
        }
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("agent_task_run"),
        enabled = !isRunning && instruction.isNotBlank(),
        onClick = { onRun(instruction, pageRange) },
      ) {
        Text(stringResource(R.string.agent_task_run))
      }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("agent_task_dismiss"),
        enabled = !isRunning,
        onClick = onDismiss,
      ) {
        Text(stringResource(R.string.close))
      }
    },
  )
}
