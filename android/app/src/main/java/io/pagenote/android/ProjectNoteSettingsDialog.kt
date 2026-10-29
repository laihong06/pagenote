package io.pagenote.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.pagenote.feature.pen.NoteBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pure validation rules for a new note passphrase (unit-tested). */
internal object NotePassphrasePolicy {
  const val MIN_LENGTH = 4
  const val MAX_LENGTH = 128

  fun isValidNewPassphrase(value: String): Boolean =
    value.length in MIN_LENGTH..MAX_LENGTH && value.none(Char::isISOControl)

  fun isConfirmed(newPassphrase: String, confirmation: String): Boolean =
    isValidNewPassphrase(newPassphrase) && newPassphrase == confirmation
}

/**
 * Modal passphrase check shown right after a password-protected project is
 * picked (port batch 3). The caller owns the stored hash and the verification
 * coroutine; this dialog only collects the attempt and renders the result.
 * Cancelling means "do not open the project" — the previous reader state stays
 * untouched.
 */
@Composable
internal fun NotePassphraseVerifyDialog(
  projectName: String,
  error: String?,
  isVerifying: Boolean,
  onVerify: (CharArray) -> Unit,
  onCancel: () -> Unit,
) {
  var input by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(R.string.note_passphrase_verify_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.note_passphrase_verify_message, projectName))
        OutlinedTextField(
          value = input,
          onValueChange = { input = it },
          enabled = !isVerifying,
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
          Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !isVerifying && input.isNotEmpty(),
        onClick = {
          val attempt = input.toCharArray()
          input = ""
          onVerify(attempt)
        },
      ) { Text(stringResource(R.string.note_passphrase_unlock)) }
    },
    dismissButton = {
      TextButton(enabled = !isVerifying, onClick = onCancel) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

/** Which passphrase step the project-settings editor is in. */
private enum class PassphraseStep { IDLE, VERIFY_CURRENT, ENTER_NEW }

private enum class PassphraseIntent { SET, CHANGE, CLEAR }

/**
 * Project-level note settings editor (paper background override + passphrase
 * management) opened from the reader menu. Verifying/hashing runs on
 * [Dispatchers.Default] because PBKDF2 is deliberately slow; the final save is
 * delegated to [onSave], which owns the SAF write and the session update.
 */
@Composable
internal fun ProjectNoteSettingsDialog(
  settings: ProjectNoteSettings,
  defaultBackground: NoteBackground,
  hasher: NotePassphraseHasher,
  onSave: suspend (ProjectNoteSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  var background by remember(settings) {
    mutableStateOf(settings.background ?: defaultBackground)
  }
  var useProjectOverride by remember(settings) { mutableStateOf(settings.background != null) }
  var step by remember(settings) { mutableStateOf(PassphraseStep.IDLE) }
  var intent by remember(settings) { mutableStateOf(PassphraseIntent.SET) }
  var currentInput by remember { mutableStateOf("") }
  var newInput by remember { mutableStateOf("") }
  var confirmInput by remember { mutableStateOf("") }
  var stagedHash by remember { mutableStateOf<NotePassphraseHash?>(null) }
  var clearedHash by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var isWorking by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val wrongPassphraseError = stringResource(R.string.note_passphrase_wrong)
  val mismatchError = stringResource(R.string.note_passphrase_mismatch)
  val unexpectedError = stringResource(R.string.note_passphrase_unexpected_error)

  val storedHash = settings.passphraseHash

  fun clearInputs() {
    currentInput = ""
    newInput = ""
    confirmInput = ""
    error = null
  }

  fun verifyCurrentThen(next: () -> Unit) {
    val attempt = currentInput.toCharArray()
    currentInput = ""
    isWorking = true
    error = null
    scope.launch {
      val ok = try {
        withContext(Dispatchers.Default) {
          storedHash != null && hasher.verify(attempt, storedHash)
        }
      } finally {
        attempt.fill('\u0000')
      }
      isWorking = false
      if (ok) next() else error = wrongPassphraseError
    }
  }

  AlertDialog(
    onDismissRequest = { if (!isWorking && !isSaving) onDismiss() },
    title = { Text(stringResource(R.string.note_settings_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(R.string.note_background_project_title),
          style = MaterialTheme.typography.titleSmall,
        )
        SettingsRadioRow(
          label = stringResource(R.string.note_background_follow_default),
          selected = !useProjectOverride,
          enabled = !isWorking && !isSaving,
        ) {
          useProjectOverride = false
        }
        NoteBackground.ALL.forEach { candidate ->
          SettingsRadioRow(
            label = noteBackgroundLabel(candidate),
            selected = useProjectOverride && background == candidate,
            enabled = !isWorking && !isSaving,
          ) {
            useProjectOverride = true
            background = candidate
          }
        }

        Text(
          stringResource(R.string.note_passphrase_title),
          style = MaterialTheme.typography.titleSmall,
        )
        when (step) {
          PassphraseStep.IDLE -> {
            if (stagedHash != null) {
              Text(stringResource(R.string.note_passphrase_staged_change))
            } else if (clearedHash) {
              Text(stringResource(R.string.note_passphrase_staged_clear))
            } else if (storedHash == null) {
              Text(stringResource(R.string.note_passphrase_none_set))
            } else {
              Text(stringResource(R.string.note_passphrase_set_info))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (storedHash != null || stagedHash != null || clearedHash) {
                TextButton(
                  enabled = !isWorking && !isSaving,
                  onClick = {
                    clearInputs()
                    intent = PassphraseIntent.CHANGE
                    step = PassphraseStep.VERIFY_CURRENT
                  },
                ) { Text(stringResource(R.string.note_passphrase_change_action)) }
                TextButton(
                  enabled = !isWorking && !isSaving,
                  onClick = {
                    clearInputs()
                    intent = PassphraseIntent.CLEAR
                    step = PassphraseStep.VERIFY_CURRENT
                  },
                ) { Text(stringResource(R.string.note_passphrase_clear_action)) }
              } else {
                TextButton(
                  enabled = !isWorking && !isSaving,
                  onClick = {
                    clearInputs()
                    intent = PassphraseIntent.SET
                    step = PassphraseStep.ENTER_NEW
                  },
                ) { Text(stringResource(R.string.note_passphrase_set_action)) }
              }
            }
          }
          PassphraseStep.VERIFY_CURRENT -> {
            Text(stringResource(R.string.note_passphrase_current_prompt))
            OutlinedTextField(
              value = currentInput,
              onValueChange = { currentInput = it },
              enabled = !isWorking && !isSaving,
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              isError = error != null,
              modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
              Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(
                enabled = !isWorking && !isSaving && currentInput.isNotEmpty(),
                onClick = {
                  verifyCurrentThen {
                    if (intent == PassphraseIntent.CLEAR) {
                      clearedHash = true
                      stagedHash = null
                      step = PassphraseStep.IDLE
                    } else {
                      step = PassphraseStep.ENTER_NEW
                    }
                  }
                },
              ) { Text(stringResource(R.string.note_passphrase_continue)) }
              TextButton(
                enabled = !isWorking && !isSaving,
                onClick = {
                  clearInputs()
                  step = PassphraseStep.IDLE
                },
              ) { Text(stringResource(R.string.cancel)) }
            }
          }
          PassphraseStep.ENTER_NEW -> {
            Text(stringResource(R.string.note_passphrase_new_prompt))
            OutlinedTextField(
              value = newInput,
              onValueChange = { newInput = it },
              enabled = !isWorking && !isSaving,
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = confirmInput,
              onValueChange = { confirmInput = it },
              enabled = !isWorking && !isSaving,
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              isError = error != null,
              modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
              Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(
                enabled = !isWorking && !isSaving && newInput.isNotEmpty() && confirmInput.isNotEmpty(),
                onClick = {
                  if (!NotePassphrasePolicy.isConfirmed(newInput, confirmInput)) {
                    error = mismatchError
                    return@TextButton
                  }
                  val passphrase = newInput.toCharArray()
                  clearInputs()
                  isWorking = true
                  scope.launch {
                    try {
                      stagedHash = withContext(Dispatchers.Default) { hasher.hash(passphrase) }
                      clearedHash = false
                      step = PassphraseStep.IDLE
                    } catch (cancelled: CancellationException) {
                      throw cancelled
                    } catch (throwable: Exception) {
                      error = unexpectedError
                    } finally {
                      passphrase.fill('\u0000')
                      isWorking = false
                    }
                  }
                },
              ) { Text(stringResource(R.string.note_passphrase_apply_action)) }
              TextButton(
                enabled = !isWorking && !isSaving,
                onClick = {
                  clearInputs()
                  step = PassphraseStep.IDLE
                },
              ) { Text(stringResource(R.string.cancel)) }
            }
          }
        }

        if (isWorking || isSaving) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
          ) { CircularProgressIndicator() }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !isWorking && !isSaving,
        onClick = {
          scope.launch {
            isSaving = true
            error = null
            try {
              val nextHash = when {
                stagedHash != null -> stagedHash
                clearedHash -> null
                else -> storedHash
              }
              onSave(
                ProjectNoteSettings(
                  background = background.takeIf { useProjectOverride },
                  passphraseHash = nextHash,
                ),
              )
              onDismiss()
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (throwable: Exception) {
              error = throwable.message?.takeIf(String::isNotBlank) ?: unexpectedError
            } finally {
              isSaving = false
            }
          }
        },
      ) { Text(stringResource(R.string.save)) }
    },
    dismissButton = {
      TextButton(enabled = !isWorking && !isSaving, onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
private fun SettingsRadioRow(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onSelect),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, modifier = Modifier.weight(1f))
    RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
  }
}

@Composable
internal fun noteBackgroundLabel(background: NoteBackground): String = when (background) {
  NoteBackground.PLAIN -> stringResource(R.string.note_background_plain)
  NoteBackground.FOUR_LINE -> stringResource(R.string.note_background_four_line)
  NoteBackground.GRID -> stringResource(R.string.note_background_grid)
  NoteBackground.DOT -> stringResource(R.string.note_background_dot)
  NoteBackground.RESTRICTED_MARGIN -> stringResource(R.string.note_background_restricted_margin)
}
