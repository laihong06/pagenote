package io.pagenote.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderAdaptiveMenuTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun rightPanelSeparatesToolsFilesAndSettingsWithoutLosingCommands() {
    var selected = ""
    composeRule.setContent {
      ReaderAdaptiveMenu(
        layout = ReaderMenuLayout.PERMANENT_SIDEBAR,
        groups = listOf(
          ReaderMenuSection(ReaderMenuGroup.FILE, "File", listOf(ReaderMenuCommand("open_project", "Open project") { selected = "open" })),
          ReaderMenuSection(ReaderMenuGroup.HANDWRITING, "Handwriting", listOf(ReaderMenuCommand("pen", "Pen") { selected = "pen" })),
          ReaderMenuSection(ReaderMenuGroup.SETTINGS, "Settings", listOf(ReaderMenuCommand("settings", "Settings") { selected = "settings" })),
        ),
        expanded = true, onExpandedChange = {}, drawerOpen = false, onDrawerOpenChange = {},
      ) { Text("Canvas", Modifier.testTag("reader_canvas")) }
    }
    val panel = composeRule.onNodeWithTag("reader_menu_sidebar").fetchSemanticsNode().boundsInRoot
    val canvas = composeRule.onNodeWithTag("reader_canvas").fetchSemanticsNode().boundsInRoot
    assertTrue("Menu should open on the right", panel.left > canvas.left)
    composeRule.onNodeWithTag("reader_menu_command_pen").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_tab_tools").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    composeRule.onNodeWithTag("reader_menu_tab_file").performClick()
    composeRule.onNodeWithTag("reader_menu_tab_file").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    composeRule.onNodeWithTag("reader_menu_command_open_project").performClick()
    assertEquals("open", selected)
    composeRule.onNodeWithTag("reader_menu_tab_settings").performClick()
    composeRule.onNodeWithTag("reader_menu_command_settings").performClick()
    assertEquals("settings", selected)
  }

  @Test
  fun backClosesExpandedTabletMenuBeforeLeavingReader() {
    composeRule.setContent {
      var expanded by remember { mutableStateOf(true) }
      ReaderAdaptiveMenu(
        layout = ReaderMenuLayout.PERMANENT_SIDEBAR,
        groups = listOf(ReaderMenuSection(ReaderMenuGroup.FILE, "File", emptyList())),
        expanded = expanded, onExpandedChange = { expanded = it }, drawerOpen = false, onDrawerOpenChange = {},
      ) { Text(if (expanded) "open" else "closed", Modifier.testTag("menu_state")) }
    }
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    composeRule.onNodeWithTag("menu_state").assertTextEquals("closed")
  }

  @Test
  fun tabsShowOnlyTheSelectedCategoryCommands() {
    composeRule.setContent {
      ReaderAdaptiveMenu(
        layout = ReaderMenuLayout.PERMANENT_SIDEBAR,
        groups = listOf(
          ReaderMenuSection(ReaderMenuGroup.FILE, "File", listOf(ReaderMenuCommand("open_project", "Open", onSelect = {}))),
          ReaderMenuSection(ReaderMenuGroup.HANDWRITING, "Pen", listOf(ReaderMenuCommand("pen_undo", "Undo", onSelect = {}))),
        ),
        expanded = true, onExpandedChange = {}, drawerOpen = false, onDrawerOpenChange = {},
      ) { Text("Reader") }
    }
    composeRule.onNodeWithTag("reader_menu_command_open_project").assertDoesNotExist()
    composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_tab_file").performClick()
    composeRule.onNodeWithTag("reader_menu_command_open_project").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertDoesNotExist()
    composeRule.onNodeWithTag("reader_menu_tab_tools").performClick()
    composeRule.onNodeWithTag("reader_menu_command_open_project").assertDoesNotExist()
    composeRule.onNodeWithTag("reader_menu_command_pen_undo").assertIsDisplayed()
  }

  @Test
  fun compactDrawerRunsSelectedCommandAndCloses() {
    composeRule.setContent {
        var drawerOpen by remember { mutableStateOf(false) }
        var selectedCommand by remember { mutableStateOf("none") }
        ReaderAdaptiveMenu(
          layout = ReaderMenuLayout.COMPACT_DRAWER,
          groups = listOf(
            ReaderMenuSection(
              group = ReaderMenuGroup.FILE,
              label = "File",
              commands = listOf(
                ReaderMenuCommand(
                  id = "open_project",
                  label = "Open project",
                  onSelect = { selectedCommand = "open" },
                ),
              ),
            ),
          ),
          expanded = true,
          onExpandedChange = {},
          drawerOpen = drawerOpen,
          onDrawerOpenChange = { drawerOpen = it },
        ) {
          Column {
            ReaderMenuTrigger(onClick = { drawerOpen = true })
            Text(if (drawerOpen) "open" else "closed", Modifier.testTag("drawer_state"))
            Text(selectedCommand, Modifier.testTag("selected_command"))
          }
        }
    }

    composeRule.onNodeWithTag("reader_menu_trigger").performClick()
    composeRule.onNodeWithTag("reader_menu_tab_file").performClick()
    composeRule.onNodeWithTag("reader_menu_command_open_project").performClick()
    composeRule.onNodeWithTag("selected_command").assertTextEquals("open")
    composeRule.onNodeWithTag("drawer_state").assertTextEquals("closed")
  }

  @Test
  fun collapsedSidebarStillOpensSelectedGroupCommands() {
    composeRule.setContent {
      var expanded by remember { mutableStateOf(true) }
      var page by remember { mutableIntStateOf(6) }
      ReaderAdaptiveMenu(
        layout = ReaderMenuLayout.PERMANENT_SIDEBAR,
        groups = listOf(
          ReaderMenuSection(
            group = ReaderMenuGroup.FILE,
            label = "File",
            commands = listOf(
              ReaderMenuCommand(id = "new_project", label = "New project", onSelect = {}),
            ),
          ),
        ),
        expanded = expanded,
        onExpandedChange = { expanded = it },
        drawerOpen = false,
        onDrawerOpenChange = {},
      ) {
        Text(
          text = "page=$page",
          modifier = Modifier.testTag("reader_page_state").clickable { page += 1 },
        )
      }
    }

    composeRule.onNodeWithTag("reader_page_state").performClick()
    composeRule.onNodeWithTag("reader_page_state").assertTextEquals("page=7")
    composeRule.onNodeWithTag("reader_menu_collapse").performClick()
    composeRule.onNodeWithTag("reader_menu_sidebar_collapsed").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_page_state").assertTextEquals("page=7")
    composeRule.onNodeWithTag("reader_menu_expand").performClick()
    composeRule.onNodeWithTag("reader_page_state").assertTextEquals("page=7")
    composeRule.onNodeWithTag("reader_menu_tab_file").performClick()
    composeRule.onNodeWithTag("reader_menu_command_new_project").assertIsDisplayed()
  }

  @Test
  fun unavailableCommandShowsItsReasonAndCannotRun() {
    composeRule.setContent {
      ReaderAdaptiveMenu(
        layout = ReaderMenuLayout.PERMANENT_SIDEBAR,
        groups = listOf(
          ReaderMenuSection(
            group = ReaderMenuGroup.EDIT,
            label = "Edit",
            commands = listOf(
              ReaderMenuCommand(
                id = "undo",
                label = "Undo",
                enabled = false,
                disabledReason = "Text editing is not available yet.",
                onSelect = {},
              ),
            ),
          ),
        ),
        expanded = true,
        onExpandedChange = {},
        drawerOpen = false,
        onDrawerOpenChange = {},
      ) {
        Text("Reader content")
      }
    }

    composeRule.onNodeWithTag("reader_menu_tab_tools").performClick()
    composeRule.onNodeWithTag("reader_menu_command_undo")
      .assertIsNotEnabled()
      .assert(
        SemanticsMatcher.expectValue(
          SemanticsProperties.StateDescription,
          "Text editing is not available yet.",
        ),
      )
    composeRule.onNodeWithTag("reader_menu_reason_undo").performClick()
    composeRule.onNodeWithText("Text editing is not available yet.").assertIsDisplayed()
  }
}
