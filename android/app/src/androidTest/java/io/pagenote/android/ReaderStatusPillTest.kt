package io.pagenote.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
class ReaderStatusPillTest {
  @get:Rule val compose = createComposeRule()

  @Test fun errorRetainsRetryAction() {
    var retried = 0
    compose.setContent {
      MaterialTheme {
        Box(Modifier.fillMaxSize()) {
          ReaderStatusPill("Save failed", persistent = true, onDismiss = {}, onRetry = { retried++ })
        }
      }
    }
    compose.onNodeWithText("Save failed").assertIsDisplayed()
    compose.onNodeWithTag("reader_pen_save_retry").performClick()
    compose.runOnIdle { assertEquals(1, retried) }
  }

  @Test fun ordinaryMessagesDismissAutomatically() {
    var dismissed = 0
    compose.mainClock.autoAdvance = false
    compose.setContent { MaterialTheme { ReaderStatusPill("Copied", persistent = false, onDismiss = { dismissed++ }) } }
    compose.onNodeWithText("Copied").assertIsDisplayed()
    compose.mainClock.advanceTimeBy(4_000)
    compose.runOnIdle { assertEquals(1, dismissed) }
  }
}
