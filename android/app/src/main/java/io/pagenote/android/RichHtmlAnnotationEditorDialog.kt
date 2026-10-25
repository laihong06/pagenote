package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.pagenote.feature.annotations.TextFontCatalog
import io.pagenote.feature.pen.PenStyle

/**
 * Rich text editing window for canvas HTML annotations (port batch 4).
 *
 * Hosts the offline contenteditable editor WebView ([RichHtmlEditorController])
 * behind a native style toolbar. Every toolbar action goes through the
 * validated [RichHtmlEditorCommand] whitelist; the editor page sanitizes HTML
 * with DOMPurify in both directions (load and read-out) and keeps its own
 * undoable action stack (style actions and typing, coalesced). Confirming
 * hands the sanitized HTML to [onConfirm] — the caller persists it as a
 * `type: "html"` canvas annotation.
 */
@Composable
internal fun RichHtmlAnnotationEditorDialog(
  controller: RichHtmlEditorController,
  initialHtml: String,
  editable: Boolean,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var editorState by remember { mutableStateOf<RichHtmlEditorState?>(null) }
  var failure by remember { mutableStateOf<String?>(null) }
  var blankContent by remember { mutableStateOf(false) }
  var fontMenuOpen by remember { mutableStateOf(false) }

  LaunchedEffect(initialHtml, editable) {
    blankContent = false
    controller.loadHtml(initialHtml, editable) { result ->
      result.fold(
        onSuccess = { editorState = it },
        onFailure = { failure = it.message ?: "editor failed" },
      )
    }
  }

  fun applyCommand(command: RichHtmlEditorCommand) {
    if (!editable) return
    controller.runCommand(command) { result ->
      result.fold(
        onSuccess = { editorState = it },
        onFailure = { failure = it.message ?: "editor failed" },
      )
    }
  }

  fun editorResult(result: Result<RichHtmlEditorState>) {
    result.fold(
      onSuccess = { editorState = it },
      onFailure = { failure = it.message ?: "editor failed" },
    )
  }

  val zhLocale = LocalConfiguration.current.locales[0]?.language?.startsWith("zh") == true

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.rich_editor_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.rich_editor_hint),
          style = MaterialTheme.typography.bodySmall,
        )
        if (editable) {
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorToolButton("B", R.string.rich_editor_bold, "rich_editor_bold") { applyCommand(RichHtmlEditorCommand.Bold) }
            EditorToolButton("I", R.string.rich_editor_italic, "rich_editor_italic") { applyCommand(RichHtmlEditorCommand.Italic) }
            EditorToolButton("U", R.string.rich_editor_underline, "rich_editor_underline") { applyCommand(RichHtmlEditorCommand.Underline) }
            EditorToolButton("S", R.string.rich_editor_strikethrough, "rich_editor_strike") { applyCommand(RichHtmlEditorCommand.StrikeThrough) }
            EditorToolButton("⌫", R.string.rich_editor_remove_format, "rich_editor_remove_format") { applyCommand(RichHtmlEditorCommand.RemoveFormat) }
            EditorToolButton("⯇", R.string.undo, "rich_editor_undo") { controller.undo(::editorResult) }
            EditorToolButton("⯈", R.string.redo, "rich_editor_redo") { controller.redo(::editorResult) }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorToolButton("≔", R.string.rich_editor_align_left, "rich_editor_align_left") { applyCommand(RichHtmlEditorCommand.JustifyLeft) }
            EditorToolButton("≡", R.string.rich_editor_align_center, "rich_editor_align_center") { applyCommand(RichHtmlEditorCommand.JustifyCenter) }
            EditorToolButton("≕", R.string.rich_editor_align_right, "rich_editor_align_right") { applyCommand(RichHtmlEditorCommand.JustifyRight) }
            EditorToolButton("•", R.string.rich_editor_bullet_list, "rich_editor_bullet_list") { applyCommand(RichHtmlEditorCommand.BulletList) }
            EditorToolButton("1.", R.string.rich_editor_ordered_list, "rich_editor_ordered_list") { applyCommand(RichHtmlEditorCommand.OrderedList) }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorToolButton("P", R.string.rich_editor_paragraph, "rich_editor_paragraph") {
              applyCommand(RichHtmlEditorCommand.FormatBlock(RichHtmlEditorCommand.Block.PARAGRAPH))
            }
            EditorToolButton("H1", R.string.rich_editor_heading_1, "rich_editor_h1") {
              applyCommand(RichHtmlEditorCommand.FormatBlock(RichHtmlEditorCommand.Block.HEADING_1))
            }
            EditorToolButton("H2", R.string.rich_editor_heading_2, "rich_editor_h2") {
              applyCommand(RichHtmlEditorCommand.FormatBlock(RichHtmlEditorCommand.Block.HEADING_2))
            }
            EditorToolButton("H3", R.string.rich_editor_heading_3, "rich_editor_h3") {
              applyCommand(RichHtmlEditorCommand.FormatBlock(RichHtmlEditorCommand.Block.HEADING_3))
            }
            EditorToolButton("❝", R.string.rich_editor_quote, "rich_editor_quote") {
              applyCommand(RichHtmlEditorCommand.FormatBlock(RichHtmlEditorCommand.Block.QUOTE))
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
              modifier = Modifier.testTag("rich_editor_font"),
              onClick = { fontMenuOpen = true },
            ) { Text(stringResource(R.string.rich_editor_font)) }
            DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
              TextFontCatalog.FONTS.forEach { font ->
                DropdownMenuItem(
                  text = { Text(if (zhLocale) font.labelZh else font.labelEn) },
                  onClick = {
                    fontMenuOpen = false
                    RichHtmlEditorCommand.FontName.create(font.id)?.let(::applyCommand)
                  },
                )
              }
            }
            PenStyle.COLOR_SWATCHES.forEach { (name, color) ->
              Button(
                modifier = Modifier
                  .size(24.dp)
                  .testTag("rich_editor_color_$name")
                  .semantics { contentDescription = color },
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(android.graphics.Color.parseColor(color)),
                ),
                onClick = { RichHtmlEditorCommand.ForeColor.create(color)?.let(::applyCommand) },
              ) {}
            }
          }
        }
        AndroidView(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .testTag("rich_editor_webview"),
          factory = { _ ->
            RichHtmlEditorController.prepareForAttach(controller.webView)
            controller.webView
          },
          update = {},
        )
        if (blankContent) {
          Text(
            text = stringResource(R.string.rich_editor_empty),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        failure?.let {
          Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("rich_editor_error"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("rich_editor_confirm"),
        enabled = editable && editorState != null,
        onClick = {
          controller.confirmHtml { result ->
            result.fold(
              onSuccess = { state ->
                if (RichHtmlEditorPolicy.hasVisibleContent(state.html)) {
                  onConfirm(state.html)
                } else {
                  blankContent = true
                }
              },
              onFailure = { failure = it.message ?: "editor failed" },
            )
          }
        },
      ) { Text(stringResource(R.string.rich_editor_apply)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("rich_editor_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
private fun EditorToolButton(label: String, labelRes: Int, tag: String, onClick: () -> Unit) {
  val description = stringResource(labelRes)
  TextButton(
    modifier = Modifier
      .testTag(tag)
      .semantics { contentDescription = description },
    onClick = onClick,
  ) { Text(label) }
}
