package io.pagenote.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class ReaderMenuCommand(
  val id: String,
  val label: String,
  val enabled: Boolean = true,
  val disabledReason: String? = null,
  val onSelect: () -> Unit,
)

internal data class ReaderMenuSection(
  val group: ReaderMenuGroup,
  val label: String,
  val commands: List<ReaderMenuCommand>,
)

internal fun desktopReaderMenuColorScheme(reader: ColorScheme): ColorScheme =
  if (reader.background.luminance() < 0.5f) {
    reader.copy(
      primary = Color(0xFF8BB8FF),
      onPrimary = Color(0xFF172233),
      primaryContainer = Color(0xFF293F5B),
      onPrimaryContainer = Color(0xFFE7EAF0),
      surface = Color(0xFF22252B),
      surfaceVariant = Color(0xFF22252B),
      onSurface = Color(0xFFE7EAF0),
      onSurfaceVariant = Color(0xFFAAB4C3),
      outlineVariant = Color(0xFF414854),
      surfaceTint = Color(0xFF8BB8FF),
    )
  } else {
    reader.copy(
      primary = Color(0xFF185ABD),
      onPrimary = Color.White,
      primaryContainer = Color(0xFFD9E7FA),
      onPrimaryContainer = Color(0xFF253247),
      surface = Color(0xFFF5F8FD),
      surfaceVariant = Color(0xFFF5F8FD),
      onSurface = Color(0xFF253247),
      onSurfaceVariant = Color(0xFF687587),
      outlineVariant = Color(0xFFDFE6F0),
      surfaceTint = Color(0xFF185ABD),
    )
  }

@Composable
internal fun ReaderMenuTrigger(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.testTag("reader_menu_trigger"),
  ) {
    Icon(
      imageVector = Icons.Outlined.Menu,
      contentDescription = stringResource(R.string.reader_menu_open),
    )
  }
}

@Composable
internal fun ReaderAdaptiveMenu(
  layout: ReaderMenuLayout,
  groups: List<ReaderMenuSection>,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  drawerOpen: Boolean,
  onDrawerOpenChange: (Boolean) -> Unit,
  collapseToTrigger: Boolean = false,
  preview: Boolean = false,
  onPreviewHand: () -> Unit = {},
  onPreviewTool: () -> Unit = {},
  onPreviewEdit: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  if (preview) {
    ReaderPreviewToolbar(groups, onPreviewHand, onPreviewTool, onPreviewEdit, content)
    return
  }
  val menuColorScheme = MaterialTheme.colorScheme

  if (layout == ReaderMenuLayout.COMPACT_DRAWER) {
    Box(Modifier.fillMaxSize().testTag("reader_menu_drawer_host")) {
      content()
      if (drawerOpen) {
        Dialog(onDismissRequest = { onDrawerOpenChange(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable { onDrawerOpenChange(false) })
            MaterialTheme(colorScheme = menuColorScheme) {
              ExpandedMenuSidebar(groups, { onDrawerOpenChange(false) },
                modifier = Modifier.align(Alignment.CenterEnd), onCommandSelected = { onDrawerOpenChange(false) })
            }
          }
        }
      }
    }
    return
  }

  Box(modifier = Modifier.fillMaxSize().testTag("reader_menu_sidebar_host")) {
    Row(Modifier.fillMaxSize()) {
      if (!expanded && !collapseToTrigger) {
        MaterialTheme(colorScheme = menuColorScheme) {
          CollapsedMenuSidebar(groups = groups, onExpand = { onExpandedChange(true) })
        }
      }
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
    }
    if (expanded) {
      BackHandler { onExpandedChange(false) }
      MaterialTheme(colorScheme = menuColorScheme) {
        ExpandedMenuSidebar(groups, { onExpandedChange(false) }, modifier = Modifier.align(Alignment.CenterEnd))
      }
    }
  }
}

@Composable
private fun ExpandedMenuSidebar(
  groups: List<ReaderMenuSection>,
  onCollapse: () -> Unit,
  modifier: Modifier = Modifier,
  onCommandSelected: () -> Unit = {},
) {
  Surface(
    modifier = modifier.width(320.dp)
      .fillMaxHeight()
      .navigationBarsPadding()
      .testTag("reader_menu_sidebar"),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
    shadowElevation = 6.dp,
  ) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.reader_menu_title),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
      )
      IconButton(
        onClick = onCollapse,
        modifier = Modifier.testTag("reader_menu_collapse"),
      ) {
        Icon(
          imageVector = Icons.Outlined.ChevronRight,
          contentDescription = stringResource(R.string.reader_menu_collapse),
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    MenuGroupList(groups = groups, onCommandSelected = onCommandSelected)
  }
  }
}

@Composable
private fun CollapsedMenuSidebar(
  groups: List<ReaderMenuSection>,
  onExpand: () -> Unit,
) {
  var openGroup by remember { mutableStateOf<ReaderMenuGroup?>(null) }

  Column(
    modifier = Modifier
      .width(72.dp)
      .fillMaxHeight()
      .background(MaterialTheme.colorScheme.surface)
      .navigationBarsPadding()
      .testTag("reader_menu_sidebar_collapsed"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    IconButton(onClick = onExpand, modifier = Modifier.testTag("reader_menu_expand")) {
      Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = stringResource(R.string.reader_menu_expand),
      )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      groups.forEach { section ->
        Box {
          IconButton(
            onClick = {
              openGroup = if (openGroup == section.group) null else section.group
            },
            modifier = Modifier.testTag(groupTag(section.group)),
          ) {
            Icon(
              imageVector = section.group.icon(),
              contentDescription = section.label,
            )
          }
          DropdownMenu(
            expanded = openGroup == section.group,
            onDismissRequest = { openGroup = null },
          ) {
            Text(
              text = section.label,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              style = MaterialTheme.typography.titleSmall,
            )
            section.commands.forEach { command ->
              MenuDropdownCommand(command) {
                openGroup = null
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MenuGroupList(
  groups: List<ReaderMenuSection>,
  onCommandSelected: () -> Unit = {},
) {
  val tabs = ReaderMenuTab.entries.filter { tab -> groups.any { it.group.tab() == tab } }
  var selected by remember(groups.map { it.group }) { mutableStateOf(tabs.firstOrNull() ?: ReaderMenuTab.TOOLS) }
  Column(
    modifier = Modifier.fillMaxSize(),
  ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
      tabs.forEach { tab ->
        val isSelected = tab == selected
        TextButton(onClick = { selected = tab }, modifier = Modifier.weight(1f)
          .semantics { this.selected = isSelected; role = Role.Tab }
          .testTag("reader_menu_tab_${tab.name.lowercase()}")) {
          Text(stringResource(tab.label), maxLines = 1,
            color = if (tab == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal)
        }
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
    groups.filter { it.group.tab() == selected }.sortedBy { it.group.menuOrder() }.forEach { section ->
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Text(
          text = section.label,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
            .testTag(groupTag(section.group)),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
          Column {
            section.commands.forEachIndexed { index, command ->
              if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
              MenuTextCommand(command, command.icon(section.group), onCommandSelected)
            }
          }
        }
      }
    }
    }
  }
}

private enum class ReaderMenuTab(val label: Int) {
  TOOLS(R.string.reader_menu_tab_tools), FILE(R.string.reader_menu_tab_file), SETTINGS(R.string.reader_menu_tab_settings),
}

private fun ReaderMenuGroup.tab(): ReaderMenuTab = when (this) {
  ReaderMenuGroup.FILE -> ReaderMenuTab.FILE
  ReaderMenuGroup.SETTINGS, ReaderMenuGroup.HELP -> ReaderMenuTab.SETTINGS
  else -> ReaderMenuTab.TOOLS
}

private fun ReaderMenuGroup.menuOrder(): Int = when (this) {
  ReaderMenuGroup.AI_ANNOTATIONS -> 0
  ReaderMenuGroup.INSERT -> 1
  ReaderMenuGroup.READING -> 2
  ReaderMenuGroup.HANDWRITING -> 3
  ReaderMenuGroup.EDIT -> 4
  ReaderMenuGroup.PARAGRAPH -> 5
  ReaderMenuGroup.FORMAT -> 6
  ReaderMenuGroup.WORD_EDITING -> 7
  else -> 0
}

private fun ReaderMenuCommand.icon(group: ReaderMenuGroup): ImageVector = when (id) {
  "pdf_text_selection" -> Icons.Outlined.FindInPage
  "page_tutor" -> Icons.Outlined.ChatBubbleOutline
  "agent_task" -> Icons.Outlined.SmartToy
  "annotations" -> Icons.Outlined.NoteAlt
  "reviews" -> Icons.Outlined.RateReview
  "handoff" -> Icons.Outlined.AssignmentTurnedIn
  "code" -> Icons.Outlined.Code
  "formula" -> Icons.Outlined.Functions
  "table" -> Icons.Outlined.TableChart
  "link" -> Icons.Outlined.Link
  "search", "ocr", "region_ocr", "ink_ocr", "formula_ocr" -> Icons.Outlined.Search
  "image", "paste_image", "insert_ink_image" -> Icons.Outlined.Image
  "audio_record", "audio_transcripts" -> Icons.Outlined.Mic
  "ink_color" -> Icons.Outlined.Palette
  "ink_width", "settings_action", "model_settings" -> Icons.Outlined.Tune
  "note_undo", "pen_undo" -> Icons.Outlined.Undo
  "note_redo", "pen_redo" -> Icons.Outlined.Redo
  "clear_page", "delete_ink" -> Icons.Outlined.Delete
  "copy", "copy_ink" -> Icons.Outlined.ContentCopy
  "paste_ink", "paste_markdown" -> Icons.Outlined.ContentPaste
  else -> group.icon()
}

@Composable
private fun MenuTextCommand(
  command: ReaderMenuCommand,
  icon: ImageVector,
  onCommandSelected: () -> Unit,
) {
  var showReason by remember(command.id) { mutableStateOf(false) }
  val disabledReason = command.disabledReason?.takeIf { !command.enabled }
  Column(Modifier.fillMaxWidth()) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
  TextButton(
    onClick = {
      command.onSelect()
      onCommandSelected()
    },
    enabled = command.enabled,
    modifier = Modifier
      .weight(1f)
      .semantics { disabledReason?.let { stateDescription = it } }
      .testTag(commandTag(command.id)),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (command.enabled) 1f else 0.38f))
    Spacer(Modifier.width(12.dp))
    Text(command.label, modifier = Modifier.weight(1f),
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (command.enabled) 1f else 0.38f))
  }
  if (disabledReason != null) {
    IconButton(onClick = { showReason = !showReason }, modifier = Modifier.size(44.dp).testTag("reader_menu_reason_${command.id}")) {
      Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.reader_menu_explain_unavailable, command.label),
        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  }
  if (showReason && disabledReason != null) {
    Text(disabledReason, modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp, bottom = 8.dp),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
  }
}

@Composable
private fun MenuDropdownCommand(
  command: ReaderMenuCommand,
  onCommandSelected: () -> Unit,
) {
  DropdownMenuItem(
    text = {
      Column {
        Text(command.label)
        command.disabledReason?.let {
          Text(it, style = MaterialTheme.typography.labelSmall)
        }
      }
    },
    onClick = {
      command.onSelect()
      onCommandSelected()
    },
    enabled = command.enabled,
    modifier = Modifier
      .semantics { command.disabledReason?.let { stateDescription = it } }
      .testTag(commandTag(command.id)),
  )
}

private fun groupTag(group: ReaderMenuGroup): String =
  "reader_menu_group_${group.name.lowercase()}"

private fun commandTag(id: String): String = "reader_menu_command_$id"

private fun ReaderMenuGroup.icon(): ImageVector = when (this) {
  ReaderMenuGroup.FILE -> Icons.Outlined.FolderOpen
  ReaderMenuGroup.AI_ANNOTATIONS -> Icons.Outlined.AutoAwesome
  ReaderMenuGroup.EDIT -> Icons.Outlined.Edit
  ReaderMenuGroup.PARAGRAPH -> Icons.AutoMirrored.Outlined.FormatListBulleted
  ReaderMenuGroup.FORMAT -> Icons.Outlined.TextFields
  ReaderMenuGroup.INSERT -> Icons.Outlined.Add
  ReaderMenuGroup.READING -> Icons.AutoMirrored.Outlined.MenuBook
  ReaderMenuGroup.HANDWRITING -> Icons.Outlined.Brush
  ReaderMenuGroup.WORD_EDITING -> Icons.Outlined.Description
  ReaderMenuGroup.SETTINGS -> Icons.Outlined.Settings
  ReaderMenuGroup.HELP -> Icons.AutoMirrored.Outlined.HelpOutline
}
