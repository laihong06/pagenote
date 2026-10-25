package io.pagenote.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

internal enum class ReaderThemeMode { LIGHT, DARK, PAPER }

internal sealed interface ReaderSettingsLoadState {
  data object Loading : ReaderSettingsLoadState
  data class Loaded(val settings: ReaderSettings) : ReaderSettingsLoadState
  data class Error(val message: String, val cause: Throwable) : ReaderSettingsLoadState
}

internal fun Flow<ReaderSettings>.asReaderSettingsLoadStates(): Flow<ReaderSettingsLoadState> =
  map<ReaderSettings, ReaderSettingsLoadState> { ReaderSettingsLoadState.Loaded(it) }
    .catch { cause ->
      emit(
        ReaderSettingsLoadState.Error(
          message = cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName,
          cause = cause,
        ),
      )
    }

internal enum class NotePanelHeaderLayout { INLINE, STACKED_ACTIONS, STACKED_CONTROLS }

internal fun notePanelHeaderLayout(availableWidthDp: Float): NotePanelHeaderLayout = when {
  availableWidthDp < 220f -> NotePanelHeaderLayout.STACKED_CONTROLS
  availableWidthDp < 420f -> NotePanelHeaderLayout.STACKED_ACTIONS
  else -> NotePanelHeaderLayout.INLINE
}

internal fun resolveReaderTheme(theme: String, systemIsDark: Boolean): ReaderThemeMode = when (theme) {
  "system" -> if (systemIsDark) ReaderThemeMode.DARK else ReaderThemeMode.LIGHT
  "light" -> ReaderThemeMode.LIGHT
  "dark" -> ReaderThemeMode.DARK
  "paper" -> ReaderThemeMode.PAPER
  else -> if (systemIsDark) ReaderThemeMode.DARK else ReaderThemeMode.LIGHT
}

internal fun readerColorScheme(mode: ReaderThemeMode): ColorScheme = when (mode) {
  ReaderThemeMode.LIGHT -> lightColorScheme(
    primary = Color(0xFF245E59),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E8E1),
    onPrimaryContainer = Color(0xFF08201D),
    secondary = Color(0xFF765B30),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFFFFFFF),
  )
  ReaderThemeMode.DARK -> darkColorScheme(
    primary = Color(0xFF8DD3C5),
    onPrimary = Color(0xFF073731),
    primaryContainer = Color(0xFF17564F),
    onPrimaryContainer = Color(0xFFBCEDE4),
    secondary = Color(0xFFE4C68F),
    background = Color(0xFF111917),
    surface = Color(0xFF1A2421),
  )
  ReaderThemeMode.PAPER -> lightColorScheme(
    primary = Color(0xFF625A35),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE1BC),
    onPrimaryContainer = Color(0xFF211E0D),
    secondary = Color(0xFF5A6652),
    background = Color(0xFFF3F0E4),
    surface = Color(0xFFFAF8F0),
  )
}

internal fun resolveReaderLocale(language: String, systemLocale: Locale): Locale = when (language) {
  "zh-CN" -> Locale.SIMPLIFIED_CHINESE
  "en-US" -> Locale.US
  else -> systemLocale
}

internal fun readerPaneWeights(leftPercent: Int): Pair<Float, Float> {
  require(leftPercent in 20..80) { "bad split percent" }
  return leftPercent.toFloat() to (100 - leftPercent).toFloat()
}

internal fun notePageTransitionDurationMillis(enabled: Boolean): Int = if (enabled) 220 else 0

@Composable
internal fun ReaderSettingsLoadScreen(
  state: ReaderSettingsLoadState,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    when (state) {
      ReaderSettingsLoadState.Loading -> {
        CircularProgressIndicator()
        Text(
          stringResource(R.string.reader_settings_loading),
          modifier = Modifier.testTag("reader_settings_loading"),
        )
      }
      is ReaderSettingsLoadState.Error -> {
        Text(
          stringResource(R.string.reader_settings_load_error_title),
          modifier = Modifier.testTag("reader_settings_load_failed"),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          stringResource(R.string.reader_settings_load_error_detail, state.message),
          modifier = Modifier.testTag("reader_settings_load_error"),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
      }
      is ReaderSettingsLoadState.Loaded -> Unit
    }
  }
}

@Composable
internal fun ReaderSettingsDialog(
  settings: ReaderSettings,
  onDismiss: () -> Unit,
  onConfigureAgent: () -> Unit,
  onConfigurePrompts: () -> Unit = {},
  onSave: suspend (ReaderSettings) -> Unit,
) {
  var draft by remember(settings) { mutableStateOf(settings) }
  var isSaving by remember { mutableStateOf(false) }
  var saveError by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val genericSaveError = stringResource(R.string.settings_save_failed)
  val inkOutsidePageLabel = stringResource(R.string.ink_outside_page_title)

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text(stringResource(R.string.reader_settings_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(R.string.reader_theme), style = MaterialTheme.typography.titleSmall)
        listOf(
          "system" to R.string.theme_system,
          "light" to R.string.theme_light,
          "dark" to R.string.theme_dark,
          "paper" to R.string.theme_paper,
        ).forEach { (value, label) ->
          SettingsChoice(
            label = stringResource(label),
            selected = draft.theme == value,
            enabled = !isSaving,
            onSelect = { draft = draft.copy(theme = value) },
          )
        }

        Text(
          stringResource(R.string.markdown_theme_title),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.titleSmall,
        )
        listOf(
          "classic" to R.string.markdown_theme_classic,
          "paper" to R.string.markdown_theme_paper,
        ).forEach { (value, label) ->
          SettingsChoice(
            label = stringResource(label),
            selected = draft.markdownTheme == value,
            enabled = !isSaving,
            testTag = "markdown_theme_$value",
            onSelect = { draft = draft.copy(markdownTheme = value) },
          )
        }

        Text(
          stringResource(R.string.note_background_title),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.titleSmall,
        )
        listOf(
          "plain" to R.string.note_background_plain,
          "four-line" to R.string.note_background_four_line,
          "grid" to R.string.note_background_grid,
          "dot" to R.string.note_background_dot,
          "restricted-margin" to R.string.note_background_restricted_margin,
        ).forEach { (value, label) ->
          SettingsChoice(
            label = stringResource(label),
            selected = draft.noteBackground == value,
            enabled = !isSaving,
            testTag = "note_background_$value",
            onSelect = { draft = draft.copy(noteBackground = value) },
          )
        }

        Text(
          stringResource(R.string.reader_language),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.titleSmall,
        )
        listOf(
          "system" to R.string.language_system,
          "zh-CN" to R.string.language_chinese,
          "en-US" to R.string.language_english,
        ).forEach { (value, label) ->
          SettingsChoice(
            label = stringResource(label),
            selected = draft.language == value,
            enabled = !isSaving,
            onSelect = { draft = draft.copy(language = value) },
          )
        }

        Text(
          stringResource(R.string.reader_split_ratio),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          stringResource(R.string.reader_split_summary, draft.leftPercent, 100 - draft.leftPercent),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
          value = draft.leftPercent.toFloat(),
          onValueChange = { draft = draft.copy(leftPercent = it.roundToInt()) },
          valueRange = 20f..80f,
          enabled = !isSaving,
          modifier = Modifier.fillMaxWidth(),
        )

        Text(
          stringResource(R.string.prepare_ahead_title),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          stringResource(R.string.prepare_ahead_summary, draft.prepareAheadPages),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
          value = draft.prepareAheadPages.toFloat(),
          onValueChange = { draft = draft.copy(prepareAheadPages = it.roundToInt()) },
          valueRange = 0f..10f,
          steps = 9,
          enabled = !isSaving,
          modifier = Modifier.fillMaxWidth().testTag("prepare_ahead_slider"),
        )

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.note_animation_title))
            Text(
              stringResource(R.string.note_animation_description),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = draft.noteAnimation,
            onCheckedChange = { draft = draft.copy(noteAnimation = it) },
            enabled = !isSaving,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.ink_outside_page_title))
            Text(
              stringResource(R.string.ink_outside_page_description),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = draft.allowInkOutsidePage,
            onCheckedChange = { draft = draft.copy(allowInkOutsidePage = it) },
            enabled = !isSaving,
            modifier = Modifier
              .testTag("allow_ink_outside_page_switch")
              .semantics {
                contentDescription = inkOutsidePageLabel
              },
          )
        }

        TextButton(
          modifier = Modifier.fillMaxWidth().testTag(
            if (isSaving) "reader_settings_configure_saving" else "configure_agent_model",
          ),
          enabled = !isSaving,
          onClick = {
            scope.launch {
              isSaving = true
              saveError = null
              try {
                ReaderSettingsPolicy.validate(draft)
                onSave(draft)
                onDismiss()
                onConfigureAgent()
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (error: Exception) {
                saveError = if (error is IllegalArgumentException) {
                  genericSaveError
                } else {
                  error.message?.takeIf(String::isNotBlank) ?: genericSaveError
                }
              } finally {
                isSaving = false
              }
            }
          },
        ) { Text(stringResource(R.string.agent_model_settings_action)) }

        TextButton(
          modifier = Modifier.fillMaxWidth().testTag("configure_agent_prompts"),
          enabled = !isSaving,
          onClick = {
            scope.launch {
              isSaving = true
              saveError = null
              try {
                ReaderSettingsPolicy.validate(draft)
                onSave(draft)
                onDismiss()
                onConfigurePrompts()
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (error: Exception) {
                saveError = if (error is IllegalArgumentException) {
                  genericSaveError
                } else {
                  error.message?.takeIf(String::isNotBlank) ?: genericSaveError
                }
              } finally {
                isSaving = false
              }
            }
          },
        ) { Text(stringResource(R.string.agent_prompt_settings_action)) }

        saveError?.let {
          Text(
            it,
            modifier = Modifier.testTag("settings_save_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("reader_settings_save"),
        enabled = !isSaving,
        onClick = {
          scope.launch {
            isSaving = true
            saveError = null
            try {
              ReaderSettingsPolicy.validate(draft)
              onSave(draft)
              onDismiss()
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (error: Exception) {
              saveError = if (error is IllegalArgumentException) {
                genericSaveError
              } else {
                error.message?.takeIf(String::isNotBlank) ?: genericSaveError
              }
            } finally {
              isSaving = false
            }
          }
        },
      ) { Text(stringResource(if (isSaving) R.string.saving else R.string.save)) }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("reader_settings_cancel"),
        onClick = onDismiss,
        enabled = !isSaving,
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
private fun SettingsChoice(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  testTag: String? = null,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .clickable(enabled = enabled, onClick = onSelect)
      .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, modifier = Modifier.weight(1f))
    RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
  }
}
