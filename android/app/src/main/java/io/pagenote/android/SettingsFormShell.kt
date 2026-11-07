package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Reuses existing settings forms inside a settings pane or as a standalone dialog. */
@Composable
internal fun SettingsFormShell(
  embedded: Boolean,
  onDismissRequest: () -> Unit,
  title: @Composable () -> Unit,
  text: @Composable () -> Unit,
  confirmButton: @Composable () -> Unit,
  dismissButton: @Composable () -> Unit,
) {
  if (!embedded) {
    AlertDialog(onDismissRequest = onDismissRequest, title = title, text = text,
      confirmButton = confirmButton, dismissButton = dismissButton)
    return
  }
  Column(Modifier.fillMaxWidth().testTag("embedded_settings_form")) {
    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
    Spacer(Modifier.height(16.dp))
    text()
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
      dismissButton()
      confirmButton()
    }
  }
}
