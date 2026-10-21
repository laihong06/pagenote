package io.pagenote.android

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NoteFontSizeDialog(
  currentSize: Int,
  onDismiss: () -> Unit,
  onApply: suspend (Int) -> Unit,
) {
  var selectedSize by remember(currentSize) { mutableIntStateOf(currentSize) }
  var isSaving by remember { mutableStateOf(false) }
  var saveError by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val genericSaveError = stringResource(R.string.settings_save_failed)

  AlertDialog(
    modifier = Modifier.testTag("reader_note_font_size_dialog"),
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text(stringResource(R.string.reader_note_font_size_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          stringResource(R.string.reader_note_font_size_description),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
        )
        ReaderSettingsPolicy.NOTE_FONT_SIZES.forEach { size ->
          Row(
            modifier = Modifier.fillMaxWidth()
              .clickable(enabled = !isSaving) { selectedSize = size }
              .testTag("reader_note_font_size_option_$size"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = selectedSize == size,
              onClick = { selectedSize = size },
              enabled = !isSaving,
            )
            Text(text = size.toString(), modifier = Modifier.padding(start = 8.dp))
          }
        }
        saveError?.let { message ->
          Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("reader_note_font_size_error"),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("reader_note_font_size_apply"),
        enabled = !isSaving,
        onClick = {
          scope.launch {
            isSaving = true
            saveError = null
            try {
              ReaderSettingsPolicy.validate(ReaderSettings(noteFontSize = selectedSize))
              onApply(selectedSize)
              onDismiss()
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              saveError = genericSaveError
            } finally {
              isSaving = false
            }
          }
        },
      ) { Text(stringResource(R.string.reader_note_font_size_apply)) }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("reader_note_font_size_cancel"),
        enabled = !isSaving,
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}
