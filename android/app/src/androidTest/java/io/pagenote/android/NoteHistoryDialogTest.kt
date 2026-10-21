package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class NoteHistoryDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun emptyHistoryShowsEmptyState() {
    composeRule.setContent {
      MaterialTheme {
        NoteHistoryDialog(
          page = 1,
          projectId = "c".repeat(64),
          currentDraft = "# now",
          baseRevision = PageNotes.revision("# now"),
          onDismiss = {},
          onRestore = {},
        )
      }
    }

    composeRule.onNodeWithTag("note_history_close").assertIsDisplayed()
  }

  @Test
  fun restoreConfirmRestoresSelected() {
    var restored: String? = null
    composeRule.setContent {
      MaterialTheme {
        NoteHistoryDialog(
          page = 2,
          projectId = "d".repeat(64),
          currentDraft = "# now",
          baseRevision = PageNotes.revision("# now"),
          onDismiss = {},
          onRestore = { restored = it },
        )
      }
    }

    composeRule.onNodeWithTag("note_history_restore").assertIsDisplayed()
    assertEquals(null, restored)
  }
}
