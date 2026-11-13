package io.pagenote.android

import android.widget.EditText
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange

internal class ReaderHomeNotePanelState(
  val page: Int,
  val editRequest: Int,
  val markdown: String,
  val dirty: Boolean,
  val writable: Boolean,
  val saving: Boolean,
  val historyEnabled: Boolean,
  val sourceDirectoryUri: Uri?,
  val animationEnabled: Boolean,
  val noteFontSize: Int,
  val markdownPalette: MarkdownContentPalette?,
)

internal class ReaderHomeNotePanelCallbacks(
  val onMarkdownChange: (String) -> Unit,
  val onMarkdownMutation: (String, String) -> Unit,
  val onEditorTextReset: (String) -> Unit,
  val onSave: () -> Unit,
  val onReload: () -> Unit,
  val onSelectionChanged: (TextRange?) -> Unit,
  val onEditorViewAttached: (EditText) -> Unit,
  val onEditorViewReleased: (EditText) -> Unit,
  val onHistory: () -> Unit,
)

@Composable
internal fun ReaderHomeNotePanel(
  state: ReaderHomeNotePanelState,
  callbacks: ReaderHomeNotePanelCallbacks,
  modifier: Modifier = Modifier,
) {
  NotePanel(
    page = state.page,
    editRequest = state.editRequest,
    markdown = state.markdown,
    dirty = state.dirty,
    writable = state.writable,
    saving = state.saving,
    status = null,
    onMarkdownChange = callbacks.onMarkdownChange,
    onMarkdownMutation = callbacks.onMarkdownMutation,
    onEditorTextReset = callbacks.onEditorTextReset,
    onSave = callbacks.onSave,
    onReload = callbacks.onReload,
    onSelectionChanged = callbacks.onSelectionChanged,
    onEditorViewAttached = callbacks.onEditorViewAttached,
    onEditorViewReleased = callbacks.onEditorViewReleased,
    historyEnabled = state.historyEnabled,
    onHistory = callbacks.onHistory,
    sourceDirectoryUri = state.sourceDirectoryUri,
    animationEnabled = state.animationEnabled,
    noteFontSize = state.noteFontSize,
    markdownPalette = state.markdownPalette,
    modifier = modifier,
  )
}
