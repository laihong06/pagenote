package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.pagenote.feature.yuketang.RainLiveState

@Composable
internal fun RainLiveActions(
  live: RainLiveState,
  hasSelection: Boolean,
  canAppend: Boolean,
  consent: Boolean,
  busy: Boolean,
  onCreateProject: () -> Unit,
  onAppend: () -> Unit,
  onRetry: () -> Unit,
  onStop: () -> Unit,
) {
  val canStart = hasSelection && !busy && live.status in setOf("stopped", "finished") && live.pendingPages == 0
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Button(
      onClick = onCreateProject, enabled = canStart,
      modifier = Modifier.fillMaxWidth().testTag("rain_live_new_project"),
    ) { Text(stringResource(R.string.rain_new_live_project)) }
    TextButton(
      onClick = onAppend, enabled = canStart && canAppend && consent,
      modifier = Modifier.testTag("rain_live_append"),
    ) { Text(stringResource(R.string.rain_live_append)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = onRetry, enabled = !busy && live.pendingPages > 0, modifier = Modifier.testTag("rain_live_retry")) {
        Text(stringResource(R.string.rain_live_retry, live.pendingPages))
      }
      TextButton(onClick = onStop, enabled = !busy && live.status !in setOf("stopped", "finished"), modifier = Modifier.testTag("rain_live_stop")) {
        Text(stringResource(R.string.rain_live_stop))
      }
    }
  }
}
