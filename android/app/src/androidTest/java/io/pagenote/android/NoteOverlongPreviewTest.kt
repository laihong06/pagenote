package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteOverlongPreviewTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun overlongMarkdownSource_showsTruncationHint() {
    composeRule.setContent {
      MaterialTheme {
        MarkdownSourcePane(
          markdown = "x".repeat(9000),
          sourceName = "left.md",
          page = 1,
          sourceDirectoryUri = null,
        )
      }
    }

    composeRule.onNodeWithTag("markdown_source_pane").assertIsDisplayed()
    composeRule.onNodeWithTag("note_preview_truncated").assertIsDisplayed()
  }

  @Test
  fun shortMarkdownSource_hidesTruncationHint() {
    composeRule.setContent {
      MaterialTheme {
        MarkdownSourcePane(
          markdown = "# short",
          sourceName = "left.md",
          page = 1,
          sourceDirectoryUri = null,
        )
      }
    }

    composeRule.onNodeWithTag("markdown_source_pane").assertIsDisplayed()
    composeRule.onAllNodesWithTag("note_preview_truncated").fetchSemanticsNodes().let {
      assert(it.isEmpty()) { "short page must not show truncation hint" }
    }
  }
}
