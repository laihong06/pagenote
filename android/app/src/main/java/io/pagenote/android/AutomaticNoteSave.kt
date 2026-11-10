package io.pagenote.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Schedules writes to the project; the existing recovery journal remains independent. */
@Composable
internal fun AutomaticNoteSave(
  projectKey: String?,
  page: Int,
  draft: String,
  savedText: String,
  visible: Boolean,
  writable: Boolean,
  saving: Boolean,
  onSave: suspend () -> Boolean,
) {
  var failed by remember(projectKey, page) { mutableStateOf(false) }
  val latestDraft by rememberUpdatedState(draft)
  val latestSaved by rememberUpdatedState(savedText)
  val latestWritable by rememberUpdatedState(writable)
  val latestSaving by rememberUpdatedState(saving)
  val latestSave by rememberUpdatedState(onSave)

  LaunchedEffect(projectKey, page, draft, visible) {
    failed = false
    if (visible && projectKey != null && writable && draft != savedText) {
      delay(700)
      if (!latestSaving) failed = !latestSave()
    }
  }
  LaunchedEffect(projectKey, page, visible) {
    if (!visible || projectKey == null) return@LaunchedEffect
    while (true) {
      delay(3_000)
      if (visible && projectKey != null && !failed && !latestSaving && latestWritable &&
        latestDraft != latestSaved
      ) failed = !latestSave()
    }
  }
}
