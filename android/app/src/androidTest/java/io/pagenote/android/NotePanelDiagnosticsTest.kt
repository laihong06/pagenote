package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotePanelDiagnosticsTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun notePreviewShowsDiagnosticsWithoutChangingMarkdown() {
    val markdown = "Formula: \$privateFormula\n\n```kotlin\nprivateCode\n"
    var changedMarkdownCount = 0
    composeRule.setContent {
      MaterialTheme {
        NotePanel(
          page = 1,
          markdown = markdown,
          dirty = false,
          writable = true,
          saving = false,
          status = null,
          onMarkdownChange = { changedMarkdownCount += 1 },
          onSave = {},
          onReload = {},
          animationEnabled = false,
        )
      }
    }

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeRule.onNodeWithText(context.getString(R.string.note_preview)).performClick()
    composeRule.onNodeWithTag("note_rich_diagnostic_UNTERMINATED_FORMULA")
      .assertIsDisplayed()
      .assertTextEquals(context.getString(R.string.note_rich_formula_unclosed))
    composeRule.onNodeWithTag("note_rich_diagnostic_UNCLOSED_CODE_FENCE")
      .assertIsDisplayed()
      .assertTextEquals(context.getString(R.string.note_rich_unclosed))
    assertEquals(0, changedMarkdownCount)
    assertEquals("Formula: \$privateFormula\n\n```kotlin\nprivateCode\n", markdown)
  }
}
