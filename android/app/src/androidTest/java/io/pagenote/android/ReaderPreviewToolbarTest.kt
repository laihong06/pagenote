package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPreviewToolbarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun penToolsAreContextualAndHandReturnsToNavigation() {
    rule.setContent {
      var mode by remember { mutableStateOf("hand") }
      MaterialTheme {
        ReaderPreviewToolbar(
          groups = listOf(ReaderMenuSection(ReaderMenuGroup.HANDWRITING, "Ink", listOf(
            ReaderMenuCommand("pen", "Pen", onSelect = { mode = "pen" }),
            ReaderMenuCommand("eraser", "Eraser", onSelect = { mode = "eraser" }),
          ))),
          onHand = { mode = "hand" }, onTool = {}, onEdit = {},
        ) { Text(mode, Modifier.testTag("mode")) }
      }
    }
    rule.onNodeWithTag("preview_command_pen").assertDoesNotExist()
    rule.onNodeWithTag("preview_write").performClick()
    rule.onNodeWithTag("mode").assertTextEquals("pen")
    rule.onNodeWithTag("preview_command_eraser").performClick()
    rule.onNodeWithTag("mode").assertTextEquals("eraser")
    rule.onNodeWithTag("preview_hand").performClick()
    rule.onNodeWithTag("mode").assertTextEquals("hand")
    rule.onNodeWithTag("preview_command_pen").assertDoesNotExist()
  }

  @Test fun selectingProjectCommandClosesPopupWithoutEnablingInk() {
    rule.setContent {
      var result by remember { mutableStateOf("idle") }
      MaterialTheme {
        ReaderPreviewToolbar(
          groups = listOf(ReaderMenuSection(ReaderMenuGroup.FILE, "Project", listOf(
            ReaderMenuCommand("open_project", "Open", onSelect = { result = "opened" }),
          ))),
          onHand = {}, onTool = { result = "unexpected-ink" }, onEdit = {},
        ) { Text(result, Modifier.testTag("result")) }
      }
    }
    rule.onNodeWithTag("preview_menu_project").performClick()
    rule.onNodeWithTag("preview_command_open_project").performClick()
    rule.onNodeWithTag("result").assertTextEquals("opened")
    rule.onNodeWithTag("preview_command_open_project").assertDoesNotExist()
  }
}
