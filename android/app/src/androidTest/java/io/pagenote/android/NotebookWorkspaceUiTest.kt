package io.pagenote.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class NotebookWorkspaceUiTest {
  @get:Rule val compose = createComposeRule()

  @Test fun floatingPaletteCanBeCollapsedAndReopened() {
    compose.setContent {
      MaterialTheme { Box(Modifier.fillMaxSize()) { FloatingPenPalette { Text("Palette contents") } } }
    }
    compose.onNodeWithText("Palette contents").assertDoesNotExist()
    compose.onNodeWithTag("floating_pen_toggle").performClick()
    compose.onNodeWithText("Palette contents").assertIsDisplayed()
    compose.onNodeWithTag("floating_pen_toggle").performClick()
    compose.onNodeWithText("Palette contents").assertDoesNotExist()
    compose.onNodeWithTag("floating_pen_toggle").performClick()
    compose.onNodeWithText("Palette contents").assertIsDisplayed()
  }

  @Test fun narrowReaderSwitchesBetweenSourceAndNote() {
    var showNote by mutableStateOf(false)
    compose.setContent { MaterialTheme { NarrowReaderModeSwitch(showNote) { showNote = it } } }
    compose.onNodeWithTag("reader_show_note").performClick()
    compose.runOnIdle { assertTrue(showNote) }
    compose.onNodeWithTag("reader_show_source").performClick()
    compose.runOnIdle { assertFalse(showNote) }
  }

  @Test fun agentUsesAClosableSidePanel() {
    compose.setContent {
      MaterialTheme {
        AgentTaskDialog(1, "", "", null, false, null, null, {}, {}, { _, _ -> }, {})
      }
    }
    compose.onNodeWithTag("agent_task_drawer").assertIsDisplayed()
    compose.onNodeWithTag("agent_task_dismiss").assertIsDisplayed()
  }
}
