package io.pagenote.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("agent_task_host")) {
      Box(Modifier.fillMaxSize().clickable(onClick = onDismiss).testTag("agent_task_scrim"))
      Surface(
        modifier = Modifier.align(Alignment.CenterEnd).width(minOf(400.dp, maxWidth - 44.dp))
          .fillMaxHeight().pointerInput(Unit) { detectTapGestures {} }.testTag("agent_task_drawer"),
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
      ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.agent_task_title, page), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("agent_task_dismiss")) {
              Text(stringResource(R.string.close))
            }
          }
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth()
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
          Spacer(Modifier.height(12.dp))
          Button(
            modifier = Modifier.testTag("agent_task_run"),
            enabled = !isRunning && instruction.isNotBlank(),
            onClick = { onRun(instruction, pageRange); onDismiss() },
          ) { Text(stringResource(R.string.agent_task_run)) }
        }
      }
    }
  }
}
