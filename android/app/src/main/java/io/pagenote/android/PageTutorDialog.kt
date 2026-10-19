package io.pagenote.android

import android.widget.TextView
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.pagenote.agent.PageTutorFocus
import io.pagenote.agent.PageTutorResult

@Composable
internal fun PageTutorDialog(
  page: Int,
  focus: PageTutorFocus? = null,
  instruction: String,
  previewInstruction: String?,
  isRunning: Boolean,
  isSaving: Boolean,
  errorMessage: String?,
  preview: PageTutorResult?,
  noteWritable: Boolean,
  onInstructionChange: (String) -> Unit,
  onExplain: (String) -> Unit,
  onAppend: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isBusy = isRunning || isSaving
  val previewIsStale = preview != null && previewInstruction != instruction
  val context = LocalContext.current
  val markwon = remember(context) {
    Markwon.builder(context).usePlugin(TablePlugin.create(context)).build()
  }
  AlertDialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    properties = DialogProperties(
      dismissOnBackPress = !isBusy,
      dismissOnClickOutside = !isBusy,
    ),
    title = { Text(stringResource(R.string.page_tutor_title, page)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = instruction,
          onValueChange = onInstructionChange,
          modifier = Modifier.fillMaxWidth().testTag("page_tutor_instruction"),
          label = { Text(stringResource(R.string.page_tutor_instruction)) },
          minLines = 2,
          maxLines = 4,
          enabled = !isBusy,
        )
        focus?.let { selected ->
          Column(
            modifier = Modifier.fillMaxWidth().testTag("page_tutor_region_focus"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(selected.label, style = MaterialTheme.typography.titleSmall)
            Text(selected.text, style = MaterialTheme.typography.bodySmall)
            selected.warnings.forEach { warning ->
              Text(
                warning,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
        if (isBusy) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator()
            val status = if (isSaving) R.string.page_tutor_saving else R.string.page_tutor_loading
            Text(stringResource(status))
          }
        }
        errorMessage?.let { message ->
          Text(
            text = message,
            modifier = Modifier.testTag("page_tutor_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        preview?.let { result ->
          if (previewIsStale) {
            Text(
              text = stringResource(R.string.page_tutor_stale_preview),
              modifier = Modifier.testTag("page_tutor_stale_preview"),
              color = MaterialTheme.colorScheme.tertiary,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          if (result.warnings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              result.warnings.forEach { warning ->
                Text(
                  text = warning,
                  color = MaterialTheme.colorScheme.tertiary,
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
          Text(stringResource(R.string.page_tutor_preview), style = MaterialTheme.typography.titleSmall)
          AndroidView(
            factory = { viewContext ->
              TextView(viewContext).apply {
                val inset = (12 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                setTextIsSelectable(true)
              }
            },
            update = { view -> markwon.setMarkdown(view, result.markdown) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp, max = 240.dp)
              .testTag("page_tutor_preview"),
          )
        }
        if (!noteWritable) {
          Text(
            text = stringResource(R.string.page_tutor_read_only),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          modifier = Modifier.testTag("page_tutor_explain"),
          enabled = !isBusy && instruction.isNotBlank(),
          onClick = { onExplain(instruction) },
        ) {
          Text(stringResource(R.string.page_tutor_explain))
        }
        Button(
          modifier = Modifier.testTag("page_tutor_append"),
          enabled = !isBusy && noteWritable && preview != null && !previewIsStale,
          onClick = onAppend,
        ) {
          Text(stringResource(R.string.page_tutor_add_to_note))
        }
      }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("page_tutor_dismiss"),
        enabled = !isBusy,
        onClick = onDismiss,
      ) {
        Text(stringResource(R.string.page_tutor_close))
      }
    },
  )
}
