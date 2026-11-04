package io.pagenote.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderWorkspaceToolbar(
  showHome: Boolean,
  homeEnabled: Boolean,
  showMenu: Boolean,
  title: String,
  zoomPercent: Int?,
  page: Int?,
  pageCount: Int,
  canChangePage: Boolean,
  onHome: () -> Unit,
  onMenu: () -> Unit,
  onAgent: () -> Unit,
  onPrevious: () -> Unit,
  onPage: () -> Unit,
  onNext: () -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
  val compact = maxWidth < 700.dp
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showHome) ShelfIcon(Icons.Default.Home, stringResource(R.string.bookshelf_return), "reader_bookshelf", homeEnabled, onHome)
    if (showMenu) ReaderMenuTrigger(onClick = onMenu)
    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (page != null) {
      if (compact) IconButton(onClick = onAgent, modifier = Modifier.testTag("reader_agent_toggle")) {
        Icon(Icons.Outlined.AutoAwesome, stringResource(R.string.agent_task_action))
      } else TextButton(onClick = onAgent, modifier = Modifier.testTag("reader_agent_toggle")) { Text("Agent") }
    }
    if (zoomPercent != null && !compact) Text("$zoomPercent%", Modifier.testTag("reader_zoom_status"), style = MaterialTheme.typography.labelLarge)
    if (page != null) {
      IconButton(onClick = onPrevious, enabled = canChangePage && page > 0, modifier = Modifier.testTag("reader_previous_page")) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous_page))
      }
      TextButton(onClick = onPage, enabled = canChangePage && pageCount > 0, modifier = Modifier.testTag("reader_page_status")) {
        Text(stringResource(R.string.page_count, page + 1, pageCount), style = MaterialTheme.typography.labelLarge)
      }
      IconButton(onClick = onNext, enabled = canChangePage && page < pageCount - 1, modifier = Modifier.testTag("reader_next_page")) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next_page))
      }
    }
  }
  }
}

@Composable
internal fun NarrowReaderModeSwitch(showNote: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth()) {
    TextButton(onClick = { onChange(false) }, modifier = Modifier.weight(1f).semantics { selected = !showNote }.testTag("reader_show_source"),
      colors = ButtonDefaults.textButtonColors(containerColor = if (!showNote) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
      Text(stringResource(R.string.reader_source_pane))
    }
    TextButton(onClick = { onChange(true) }, modifier = Modifier.weight(1f).semantics { selected = showNote }.testTag("reader_show_note"),
      colors = ButtonDefaults.textButtonColors(containerColor = if (showNote) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
      Text(stringResource(R.string.reader_note_pane))
    }
  }
}

@Composable
internal fun ObserveNarrowReader(editRequest: Int, hasError: Boolean, revealNote: () -> Unit) {
  androidx.compose.runtime.LaunchedEffect(editRequest) { if (editRequest > 0) revealNote() }
  androidx.compose.runtime.LaunchedEffect(hasError) { if (hasError) revealNote() }
}
