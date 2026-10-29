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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
  translationStore: TranslationProviderSettingsStore?,
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
      translationStore = translationStore,
      onDismiss = onDismiss,
    )
  }
}

@Composable
private fun AgentModelSettingsForm(
  settings: AgentModelSettingsView,
  store: AgentModelSettingsStore,
  translationStore: TranslationProviderSettingsStore?,
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
  val loadErrorTitle = stringResource(R.string.agent_model_settings_load_failed)

  // 批次 10：翻译服务商（可选直连百度/有道）并入 Agent 设置。
  var providerState by remember { mutableStateOf<TranslationProviderLoadState>(TranslationProviderLoadState.Loading) }
  LaunchedEffect(translationStore) {
    providerState = if (translationStore == null) {
      TranslationProviderLoadState.Unavailable
    } else {
      try {
        TranslationProviderLoadState.Loaded(
          view = withContext(Dispatchers.IO) { translationStore.loadForEditing() },
          draft = TranslationProviderDraft(),
          keyChange = TranslationKeyChange.Keep,
        )
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        TranslationProviderLoadState.Failed(error.message?.takeIf(String::isNotBlank) ?: loadErrorTitle)
      }
    }
  }

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
        TranslationProviderSection(
          state = providerState,
          saving = saving,
          onStateChange = { providerState = it },
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
                (providerState as? TranslationProviderLoadState.Loaded)?.let { loaded ->
                  translationStore?.save(loaded.draft, loaded.keyChange)
                }
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

private sealed interface TranslationProviderLoadState {
  data object Loading : TranslationProviderLoadState
  data object Unavailable : TranslationProviderLoadState
  data class Failed(val message: String) : TranslationProviderLoadState
  data class Loaded(
    val view: TranslationProviderView,
    val draft: TranslationProviderDraft,
    val keyChange: TranslationKeyChange,
  ) : TranslationProviderLoadState
}

@Composable
private fun TranslationProviderSection(
  state: TranslationProviderLoadState,
  saving: Boolean,
  onStateChange: (TranslationProviderLoadState) -> Unit,
) {
  when (state) {
    TranslationProviderLoadState.Loading -> Text(
      stringResource(R.string.translation_provider_loading),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TranslationProviderLoadState.Unavailable -> Unit
    is TranslationProviderLoadState.Failed -> Text(
      state.message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
    is TranslationProviderLoadState.Loaded -> TranslationProviderFields(state, saving, onStateChange)
  }
}

@Composable
private fun TranslationProviderFields(
  state: TranslationProviderLoadState.Loaded,
  saving: Boolean,
  onStateChange: (TranslationProviderLoadState) -> Unit,
) {
  var providerMenuExpanded by remember { mutableStateOf(false) }
  var secretKey by remember(state.view) { mutableStateOf("") }
  var clearSavedSecret by remember(state.view) { mutableStateOf(false) }
  val draft = state.draft
  Text(
    stringResource(R.string.translation_provider_section_title),
    style = MaterialTheme.typography.titleSmall,
  )
  Text(
    stringResource(R.string.translation_provider_section_help),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Box {
    OutlinedButton(
      modifier = Modifier.fillMaxWidth().testTag("translation_provider_picker"),
      enabled = !saving,
      onClick = { providerMenuExpanded = true },
    ) {
      Text(
        stringResource(
          when (draft.provider) {
            TranslationProvider.NONE -> R.string.translation_provider_none
            TranslationProvider.BAIDU -> R.string.translation_provider_baidu
            TranslationProvider.YOUDAO -> R.string.translation_provider_youdao
          },
        ),
      )
    }
    DropdownMenu(
      expanded = providerMenuExpanded,
      onDismissRequest = { providerMenuExpanded = false },
    ) {
      TranslationProvider.entries.forEach { provider ->
        DropdownMenuItem(
          modifier = Modifier.testTag("translation_provider_option_${provider.id}"),
          text = {
            Text(
              stringResource(
                when (provider) {
                  TranslationProvider.NONE -> R.string.translation_provider_none
                  TranslationProvider.BAIDU -> R.string.translation_provider_baidu
                  TranslationProvider.YOUDAO -> R.string.translation_provider_youdao
                },
              ),
            )
          },
          onClick = {
            providerMenuExpanded = false
            onStateChange(
              TranslationProviderLoadState.Loaded(
                view = state.view,
                draft = draft.copy(provider = provider, appId = draft.appId),
                keyChange = if (provider == TranslationProvider.NONE) TranslationKeyChange.Clear else state.keyChange,
              ),
            )
          },
        )
      }
    }
  }
  if (draft.provider != TranslationProvider.NONE) {
    OutlinedTextField(
      value = draft.appId,
      onValueChange = { value ->
        onStateChange(
          TranslationProviderLoadState.Loaded(
            view = state.view,
            draft = draft.copy(appId = value),
            keyChange = state.keyChange,
          ),
        )
      },
      label = { Text(stringResource(R.string.translation_provider_app_id)) },
      singleLine = true,
      enabled = !saving,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = secretKey,
      onValueChange = {
        secretKey = it
        if (it.isNotEmpty()) clearSavedSecret = false
        onStateChange(
          TranslationProviderLoadState.Loaded(
            view = state.view,
            draft = draft,
            keyChange = if (clearSavedSecret) TranslationKeyChange.Clear
            else if (it.isNotBlank()) TranslationKeyChange.Replace(it)
            else TranslationKeyChange.Keep,
          ),
        )
      },
      label = { Text(stringResource(R.string.translation_provider_secret_key)) },
      placeholder = {
        Text(
          stringResource(
            if (state.view.hasSecretKey) R.string.agent_model_settings_key_saved
            else R.string.agent_model_settings_key_not_saved,
          ),
        )
      },
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      singleLine = true,
      enabled = !saving && !clearSavedSecret,
      modifier = Modifier.fillMaxWidth(),
    )
    if (state.view.hasSecretKey) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = clearSavedSecret,
          onCheckedChange = { checked ->
            clearSavedSecret = checked
            onStateChange(
              TranslationProviderLoadState.Loaded(
                view = state.view,
                draft = draft,
                keyChange = if (checked) TranslationKeyChange.Clear else TranslationKeyChange.Keep,
              ),
            )
          },
          enabled = !saving,
        )
        Text(stringResource(R.string.translation_provider_clear_key))
      }
    }
  }
}
