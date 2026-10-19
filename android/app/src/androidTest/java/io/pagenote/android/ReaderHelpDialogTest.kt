package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderHelpDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun diagnosticsCopyAndShareRequireExplicitActions() {
    var copyCount = 0
    var shareCount = 0
    val report = "pagenote 1.0.0\nAndroid API 34\nSource: PDF\nPages: 69"

    composeRule.setContent {
      MaterialTheme {
        ReaderHelpDialog(
          topic = ReaderHelpTopic.DIAGNOSTICS,
          appVersion = "1.0.0",
          diagnostics = report,
          onCopyDiagnostics = { copyCount++ },
          onShareDiagnostics = { shareCount++ },
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("help_diagnostics").assertIsDisplayed()
    composeRule.onNodeWithText(report).assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(0, copyCount)
      assertEquals(0, shareCount)
    }
    composeRule.onNodeWithTag("help_copy_diagnostics").performClick()
    composeRule.onNodeWithTag("help_share_diagnostics").performClick()
    composeRule.runOnIdle {
      assertEquals(1, copyCount)
      assertEquals(1, shareCount)
    }
  }
}
