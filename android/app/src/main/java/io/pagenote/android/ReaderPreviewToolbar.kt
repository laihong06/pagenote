package io.pagenote.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Preview-only presentation; commands still use the reader's existing save/undo logic. */
@Composable
internal fun ReaderPreviewToolbar(
  groups: List<ReaderMenuSection>,
  onHand: () -> Unit,
  onTool: () -> Unit,
  onEdit: () -> Unit,
  content: @Composable () -> Unit,
) {
  var openMenu by remember { mutableStateOf<String?>(null) }
  var writing by rememberSaveable { mutableStateOf(false) }
  var selectedTool by rememberSaveable { mutableStateOf("hand") }
  val commands = groups.flatMap { it.commands }.associateBy { it.id }
  val writingIds = listOf("pen", "highlighter", "eraser", "ink_color", "ink_width", "pen_undo", "pen_redo")
  fun invoke(command: ReaderMenuCommand) {
    // File/AI actions must not silently re-enable handwriting after selecting Hand.
    if (command.id in setOf("pen", "highlighter", "eraser", "select_tool", "line", "arrow", "shape", "ink_text", "annotate_mode", "pdf_text_selection", "region_ocr")) {
      onTool()
      selectedTool = command.id
    }
    command.onSelect()
    openMenu = null
  }
  @Composable
  fun menu(id: String, label: String, icon: ImageVector, items: List<ReaderMenuCommand>) {
    Box {
      TextButton(onClick = { openMenu = if (openMenu == id) null else id }, modifier = Modifier.testTag("preview_menu_$id")) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
      }
      DropdownMenu(expanded = openMenu == id, onDismissRequest = { openMenu = null }) {
        items.forEach { command ->
          DropdownMenuItem(
            text = { Text(command.label) },
            enabled = command.enabled,
            onClick = { invoke(command) },
            modifier = Modifier.testTag("preview_command_${command.id}"),
          )
        }
      }
    }
  }
  Column(Modifier.fillMaxSize()) {
    Surface(tonalElevation = 1.dp) {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        menu("project", stringResource(R.string.reader_menu_group_file), Icons.Outlined.FolderOpen,
          groups.filter { it.group == ReaderMenuGroup.FILE }.flatMap { it.commands })
        menu("select", stringResource(R.string.preview_select), Icons.Outlined.NearMe,
          listOfNotNull(commands["pdf_text_selection"], commands["select_tool"], commands["region_ocr"], commands["selection_to_annotation"]))
        TextButton(onClick = { onHand(); selectedTool = "hand"; writing = false; openMenu = null }, modifier = Modifier.testTag("preview_hand")) {
          Icon(Icons.Outlined.PanTool, null, Modifier.size(20.dp))
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.preview_hand))
        }
        TextButton(onClick = {
          writing = !writing
          openMenu = null
          if (writing) commands["pen"]?.takeIf { it.enabled }?.let { invoke(it) }
        }, modifier = Modifier.testTag("preview_write")) {
          Icon(Icons.Outlined.Draw, null, Modifier.size(20.dp))
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.preview_write))
        }
        Box {
          TextButton(onClick = { onEdit(); writing = false; openMenu = if (openMenu == "edit") null else "edit" }, modifier = Modifier.testTag("preview_menu_edit")) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.note_edit))
          }
          DropdownMenu(expanded = openMenu == "edit", onDismissRequest = { openMenu = null }) {
            groups.filter { it.group in setOf(ReaderMenuGroup.EDIT, ReaderMenuGroup.PARAGRAPH, ReaderMenuGroup.FORMAT, ReaderMenuGroup.INSERT) }.forEach { section ->
              Text(section.label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
              section.commands.forEach { command ->
                DropdownMenuItem(text = { Text(command.label) }, enabled = command.enabled, onClick = { invoke(command) })
              }
            }
          }
        }
        menu("ai", stringResource(R.string.preview_ai), Icons.Outlined.AutoAwesome,
          groups.filter { it.group == ReaderMenuGroup.AI_ANNOTATIONS }.flatMap { it.commands }.filterNot { it.id == "pdf_text_selection" })
        // Keep less frequent tools reachable without permanent categories or dead Office commands.
        menu("more", stringResource(R.string.preview_more), Icons.Outlined.MoreHoriz,
          groups.filter { it.group in setOf(ReaderMenuGroup.READING, ReaderMenuGroup.SETTINGS, ReaderMenuGroup.HELP, ReaderMenuGroup.HANDWRITING) }
            .flatMap { it.commands }.filterNot { it.id in writingIds || it.id == "select_tool" || it.id == "region_ocr" })
      }
    }
    if (writing) {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        writingIds.mapNotNull(commands::get).forEach { command ->
          val icon = when (command.id) {
            "pen" -> Icons.Outlined.Draw
            "highlighter" -> Icons.Outlined.BorderColor
            "eraser" -> Icons.Outlined.CleaningServices
            "ink_color" -> Icons.Outlined.Palette
            "ink_width" -> Icons.Outlined.LineWeight
            "pen_undo" -> Icons.Outlined.Undo
            else -> Icons.Outlined.Redo
          }
          FilledTonalIconButton(
            onClick = { invoke(command) }, enabled = command.enabled,
            modifier = Modifier.testTag("preview_command_${command.id}"),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (selectedTool == command.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
          ) { Icon(icon, contentDescription = command.label) }
        }
      }
    }
    HorizontalDivider()
    Box(Modifier.weight(1f)) { content() }
  }
}
