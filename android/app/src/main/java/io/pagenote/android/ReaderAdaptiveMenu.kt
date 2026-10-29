package io.pagenote.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

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
  content: @Composable () -> Unit,
) {
  val menuColorScheme = desktopReaderMenuColorScheme(MaterialTheme.colorScheme)

  if (layout == ReaderMenuLayout.COMPACT_DRAWER) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val currentDrawerOpenChange by rememberUpdatedState(onDrawerOpenChange)

    LaunchedEffect(drawerOpen, drawerState) {
      if (drawerOpen && !drawerState.isOpen) drawerState.open()
      if (!drawerOpen && drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState) {
      snapshotFlow { drawerState.isOpen }.collect { isOpen ->
        currentDrawerOpenChange(isOpen)
      }
    }

    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = {
        MaterialTheme(colorScheme = menuColorScheme) {
          ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(304.dp)) {
            MenuGroupList(
              groups = groups,
              title = stringResource(R.string.reader_menu_title),
              onCommandSelected = { onDrawerOpenChange(false) },
            )
          }
        }
      },
      modifier = Modifier.fillMaxSize().testTag("reader_menu_drawer_host"),
    ) {
      content()
    }
    return
  }

  Row(modifier = Modifier.fillMaxSize().testTag("reader_menu_sidebar_host")) {
    if (expanded) {
      MaterialTheme(colorScheme = menuColorScheme) {
        ExpandedMenuSidebar(
          groups = groups,
          onCollapse = { onExpandedChange(false) },
        )
      }
    } else if (!collapseToTrigger) {
      MaterialTheme(colorScheme = menuColorScheme) {
        CollapsedMenuSidebar(
          groups = groups,
          onExpand = { onExpandedChange(true) },
        )
      }
    }
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
      content()
    }
  }
}

@Composable
private fun ExpandedMenuSidebar(
  groups: List<ReaderMenuSection>,
  onCollapse: () -> Unit,
) {
  Column(
    modifier = Modifier
      .width(264.dp)
      .fillMaxHeight()
      .background(MaterialTheme.colorScheme.surface)
      .navigationBarsPadding()
      .testTag("reader_menu_sidebar"),
  ) {
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
          imageVector = Icons.Outlined.ChevronLeft,
          contentDescription = stringResource(R.string.reader_menu_collapse),
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    MenuGroupList(groups = groups, title = null)
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
  title: String?,
  onCommandSelected: () -> Unit = {},
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
  ) {
    title?.let {
      Text(
        text = it,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleLarge,
      )
    }
    groups.forEachIndexed { index, section ->
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        Text(
          text = section.label,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
            .testTag(groupTag(section.group)),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        section.commands.forEach { command ->
          MenuTextCommand(command, onCommandSelected)
        }
      }
      if (index < groups.lastIndex) {
        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
      }
    }
  }
}

@Composable
private fun MenuTextCommand(
  command: ReaderMenuCommand,
  onCommandSelected: () -> Unit,
) {
  TextButton(
    onClick = {
      command.onSelect()
      onCommandSelected()
    },
    enabled = command.enabled,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { command.disabledReason?.let { stateDescription = it } }
      .testTag(commandTag(command.id)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(command.label, color = MaterialTheme.colorScheme.onSurface)
      command.disabledReason?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
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
