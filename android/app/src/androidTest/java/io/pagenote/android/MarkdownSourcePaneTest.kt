package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownSourcePaneTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun markdownSourcePane_rendersPagedPreview() {
    composeRule.setContent {
      MarkdownSourcePane(
        markdown = "# one\n\n<!-- pagebreak -->\n\n# two\n",
        sourceName = "left.md",
        page = 2,
        sourceDirectoryUri = null,
      )
    }

    composeRule.onNodeWithTag("markdown_source_pane").assertIsDisplayed()
  }

  @Test
  fun markdownSourcePane_showsSafeLocalizedSyntaxDiagnostics() {
    composeRule.setContent {
      MarkdownSourcePane(
        markdown = "Formula: \$privateFormula\n\n```kotlin\nprivateCode\n",
        sourceName = "source.md",
        page = 1,
        sourceDirectoryUri = null,
      )
    }

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeRule.onNodeWithTag("note_rich_diagnostic_UNTERMINATED_FORMULA")
      .assertIsDisplayed()
      .assertTextEquals(context.getString(R.string.note_rich_formula_unclosed))
    composeRule.onNodeWithTag("note_rich_diagnostic_UNCLOSED_CODE_FENCE")
      .assertIsDisplayed()
      .assertTextEquals(context.getString(R.string.note_rich_unclosed))
  }
}
