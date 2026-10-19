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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecentProjectsDialog(
  projects: List<RecentProjectEntry>,
  onOpen: (RecentProjectEntry) -> Unit,
  onRemove: (RecentProjectEntry) -> Unit,
  onOpenProjectPicker: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    modifier = Modifier.testTag("recent_projects_dialog"),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.recent_projects_title)) },
    text = {
      if (projects.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("recent_projects_empty"),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(stringResource(R.string.recent_projects_empty_message))
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        ) {
          projects.forEachIndexed { index, project ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .clickable { onOpen(project) }
                  .testTag("recent_project_open_$index")
                  .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = project.name,
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                val timestamp = DateFormat.getDateTimeInstance(
                  DateFormat.SHORT,
                  DateFormat.SHORT,
                  Locale.getDefault(),
                ).format(Date(project.lastOpenedAtMillis))
                Text(
                  text = stringResource(R.string.recent_projects_last_opened, timestamp),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              TextButton(
                modifier = Modifier.testTag("recent_project_remove_$index"),
                onClick = { onRemove(project) },
              ) {
                Text(stringResource(R.string.recent_projects_remove))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("recent_projects_open_picker"),
        onClick = onOpenProjectPicker,
      ) {
        Text(stringResource(R.string.open_project))
      }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("recent_projects_cancel"),
        onClick = onDismiss,
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
