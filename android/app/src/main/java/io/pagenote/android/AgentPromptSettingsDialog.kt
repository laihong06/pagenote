package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AgentPromptSettingsDialog(
  store: AgentPromptSettingsStore,
  onDismiss: () -> Unit,
) {
  var selectedId by rememberSaveable { mutableStateOf("system") }
  var isModuleMenuExpanded by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(true) }
  var isSaving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var status by remember { mutableStateOf<String?>(null) }
  val drafts = remember { mutableStateMapOf<String, String>() }
  val scope = rememberCoroutineScope()
  val genericError = stringResource(R.string.agent_prompt_save_failed)
  val invalidError = stringResource(R.string.agent_prompt_invalid)
  val savedStatus = stringResource(R.string.agent_prompt_saved)
  val defaultStatus = stringResource(R.string.agent_prompt_default_restored)

  LaunchedEffect(store, selectedId) {
    if (selectedId !in drafts) {
      loading = true
      error = null
      try {
        val prompt = withContext(Dispatchers.IO) { store.loadForEditing(selectedId) }
        drafts[selectedId] = prompt.text
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        error = failure.message?.takeIf(String::isNotBlank) ?: genericError
      } finally {
        loading = false
      }
    } else {
      loading = false
    }
  }

  val selectedModule = AgentPromptCatalog.module(selectedId)
  val selectedTitle = stringResource(selectedModule.titleResource)
  val currentText = drafts[selectedId].orEmpty()

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = {
      Text(stringResource(R.string.agent_prompt_settings_title), modifier = Modifier.testTag("agent_prompt_title"))
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box {
          OutlinedButton(
            modifier = Modifier.fillMaxWidth().testTag("agent_prompt_module_picker"),
            enabled = !isSaving,
            onClick = { isModuleMenuExpanded = true },
          ) { Text(selectedTitle) }
          DropdownMenu(
            expanded = isModuleMenuExpanded,
            onDismissRequest = { isModuleMenuExpanded = false },
          ) {
            AgentPromptCatalog.modules.forEach { module ->
              DropdownMenuItem(
                modifier = Modifier.testTag("agent_prompt_option_${module.id}"),
                text = { Text(stringResource(module.titleResource)) },
                onClick = {
                  selectedId = module.id
                  isModuleMenuExpanded = false
                  error = null
                  status = null
                },
              )
            }
          }
        }
        Text(
          stringResource(R.string.agent_prompt_settings_description),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loading) {
          Text(stringResource(R.string.agent_prompt_loading), modifier = Modifier.testTag("agent_prompt_loading"))
        } else {
          OutlinedTextField(
            value = currentText,
            onValueChange = { value ->
              if (value.length <= AgentPromptCatalog.MAX_PROMPT_LENGTH) {
                drafts[selectedId] = value
                error = null
                status = null
              }
            },
            label = { Text(stringResource(R.string.agent_prompt_content)) },
            supportingText = {
              Text("${currentText.length}/${AgentPromptCatalog.MAX_PROMPT_LENGTH}")
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 12,
            maxLines = 18,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().testTag("agent_prompt_text"),
          )
        }
        error?.let {
          Text(
            it,
            modifier = Modifier.testTag("agent_prompt_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        status?.let {
          Text(it, modifier = Modifier.testTag("agent_prompt_status"), color = MaterialTheme.colorScheme.primary)
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
          modifier = Modifier.testTag("agent_prompt_reset"),
          enabled = !loading && !isSaving,
          onClick = {
            scope.launch {
              isSaving = true
              error = null
              status = null
              try {
                withContext(Dispatchers.IO) { store.reset(selectedId) }
                val prompt = withContext(Dispatchers.IO) { store.loadForEditing(selectedId) }
                drafts[selectedId] = prompt.text
                status = defaultStatus
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (failure: Exception) {
                error = failure.message?.takeIf(String::isNotBlank) ?: genericError
              } finally {
                isSaving = false
              }
            }
          },
        ) { Text(stringResource(R.string.agent_prompt_reset)) }
        TextButton(
          modifier = Modifier.testTag("agent_prompt_save"),
          enabled = !loading && !isSaving,
          onClick = {
            scope.launch {
              isSaving = true
              error = null
              status = null
              try {
                AgentPromptCatalog.validate(selectedId, currentText)
                withContext(Dispatchers.IO) { store.save(selectedId, currentText) }
                status = savedStatus
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (failure: Exception) {
                error = if (failure is IllegalArgumentException) invalidError
                else failure.message?.takeIf(String::isNotBlank) ?: genericError
              } finally {
                isSaving = false
              }
            }
          },
        ) { Text(stringResource(if (isSaving) R.string.saving else R.string.save)) }
      }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("agent_prompt_cancel"),
        enabled = !isSaving,
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}
