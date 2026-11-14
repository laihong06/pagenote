package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun ReaderStatusPill(
  message: String,
  persistent: Boolean,
  onDismiss: () -> Unit,
  onRetry: (() -> Unit)? = null,
  retryEnabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val dismiss by rememberUpdatedState(onDismiss)
  LaunchedEffect(message, persistent) {
    if (!persistent) { delay(3500); dismiss() }
  }
  Surface(
    modifier = modifier.widthIn(max = 480.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("reader_status_pill"),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.inverseSurface,
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    shadowElevation = 6.dp,
  ) {
    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(message, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
      if (onRetry != null) TextButton(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.testTag("reader_pen_save_retry")) {
        Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.inversePrimary)
      }
      if (persistent && onRetry == null) IconButton(onClick = onDismiss) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
      }
    }
  }
}
