package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AgentModelSettingsLoadState {
  data object Loading : AgentModelSettingsLoadState
  data class Loaded(val settings: AgentModelSettingsView) : AgentModelSettingsLoadState
  data class Failed(val message: String) : AgentModelSettingsLoadState
}

@Composable
internal fun AgentModelSettingsDialog(
  store: AgentModelSettingsStore,
  onDismiss: () -> Unit,
) {
  var retry by remember { mutableIntStateOf(0) }
  var state by remember { mutableStateOf<AgentModelSettingsLoadState>(AgentModelSettingsLoadState.Loading) }
  val loadErrorTitle = stringResource(R.string.agent_model_settings_load_failed)

  LaunchedEffect(store, retry) {
    state = AgentModelSettingsLoadState.Loading
    state = try {
      AgentModelSettingsLoadState.Loaded(withContext(Dispatchers.IO) { store.loadForEditing() })
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      AgentModelSettingsLoadState.Failed(error.message?.takeIf(String::isNotBlank) ?: loadErrorTitle)
    }
  }

  when (val loadState = state) {
    AgentModelSettingsLoadState.Loading -> AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
          stringResource(R.string.agent_model_settings_title),
          modifier = Modifier.testTag("agent_model_settings_title"),
        )
      },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Text(
            stringResource(R.string.agent_model_settings_loading),
            modifier = Modifier.testTag("agent_model_settings_loading"),
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      },
    )
    is AgentModelSettingsLoadState.Failed -> AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(loadErrorTitle, modifier = Modifier.testTag("agent_model_settings_load_failed")) },
      text = {
        Text(
          loadState.message,
          modifier = Modifier.testTag("agent_model_settings_load_error"),
          color = MaterialTheme.colorScheme.error,
        )
      },
      confirmButton = {
        TextButton(onClick = { retry += 1 }) { Text(stringResource(R.string.retry)) }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      },
    )
    is AgentModelSettingsLoadState.Loaded -> AgentModelSettingsForm(
      settings = loadState.settings,
      store = store,
      onDismiss = onDismiss,
    )
  }
}

@Composable
private fun AgentModelSettingsForm(
  settings: AgentModelSettingsView,
  store: AgentModelSettingsStore,
  onDismiss: () -> Unit,
) {
  var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
  var model by remember(settings) { mutableStateOf(settings.model) }
  var apiKey by remember(settings) { mutableStateOf("") }
  var clearSavedKey by remember(settings) { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var saveError by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val invalidSettingsMessage = stringResource(R.string.agent_model_settings_invalid)
  val genericSaveError = stringResource(R.string.settings_save_failed)

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = {
      Text(
        stringResource(R.string.agent_model_settings_title),
        modifier = Modifier.testTag("agent_model_settings_title"),
      )
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = baseUrl,
          onValueChange = { baseUrl = it },
          label = { Text(stringResource(R.string.agent_model_settings_base_url)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          singleLine = true,
          enabled = !saving,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = model,
          onValueChange = { model = it },
          label = { Text(stringResource(R.string.agent_model_settings_model)) },
          singleLine = true,
          enabled = !saving,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = apiKey,
          onValueChange = {
            apiKey = it
            if (it.isNotEmpty()) clearSavedKey = false
          },
          label = { Text(stringResource(R.string.agent_model_settings_api_key)) },
          placeholder = {
            Text(
              stringResource(
                if (settings.hasApiKey) R.string.agent_model_settings_key_saved
                else R.string.agent_model_settings_key_not_saved,
              ),
            )
          },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
          enabled = !saving && !clearSavedKey,
          modifier = Modifier.fillMaxWidth(),
        )
        if (settings.hasApiKey) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = clearSavedKey,
              onCheckedChange = { clearSavedKey = it },
              enabled = !saving,
            )
            Text(stringResource(R.string.agent_model_settings_clear_key))
          }
        }
        Text(
          stringResource(R.string.agent_model_settings_network_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        saveError?.let {
          Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("save_agent_model_settings"),
        enabled = !saving,
        onClick = {
          scope.launch {
            saving = true
            saveError = null
            try {
              val keyChange = when {
                clearSavedKey -> AgentKeyChange.Clear
                apiKey.isNotBlank() -> AgentKeyChange.Replace(apiKey)
                else -> AgentKeyChange.Keep
              }
              withContext(Dispatchers.IO) {
                store.save(AgentModelSettingsDraft(baseUrl, model), keyChange)
              }
              onDismiss()
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (error: Exception) {
              saveError = if (error is IllegalArgumentException) {
                invalidSettingsMessage
              } else {
                error.message?.takeIf(String::isNotBlank) ?: genericSaveError
              }
            } finally {
              saving = false
            }
          }
        },
      ) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
    },
  )
}
