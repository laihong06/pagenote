package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.pagenote.feature.yuketang.RainLiveState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RainCaptureFlowTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun noPdfCanCreateLiveProjectButJoiningRequiresFolderAndConsent() {
    var started = false
    composeRule.setContent {
      MaterialTheme {
        var creating by remember { mutableStateOf(false) }
        var folder by remember { mutableStateOf<String?>(null) }
        var consent by remember { mutableStateOf(false) }
        if (!creating) {
          RainLiveActions(
            live = RainLiveState(), hasSelection = true, canAppend = false, consent = false, busy = false,
            onCreateProject = { creating = true }, onAppend = { error("No PDF is open") }, onRetry = {}, onStop = {},
          )
        } else {
          RainProjectDialog(
            mode = RainProjectMode.LIVE, name = "Live lecture", folder = folder, consent = consent, busy = false, error = null,
            onNameChange = {}, onChooseFolder = { folder = "Selected folder" }, onConsentChange = { consent = it },
            onConfirm = { started = true }, onDismiss = { creating = false },
          )
        }
      }
    }
    composeRule.onNodeWithTag("rain_live_append").assertIsNotEnabled()
    composeRule.onNodeWithTag("rain_live_new_project").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("rain_project_confirm").assertIsNotEnabled()
    composeRule.onNodeWithTag("rain_project_folder").performClick()
    composeRule.onNodeWithTag("rain_project_confirm").assertIsNotEnabled()
    composeRule.runOnIdle { assertFalse(started) }
    composeRule.onNodeWithTag("rain_project_consent").performClick()
    composeRule.onNodeWithTag("rain_project_confirm").assertIsEnabled().performClick()
    composeRule.runOnIdle { assertTrue(started) }
  }

  @Test
  fun pendingSlidesPreventStartingAnotherProject() {
    composeRule.setContent {
      MaterialTheme {
        RainLiveActions(
          live = RainLiveState(status = "stopped", pendingPages = 1), hasSelection = true, canAppend = true, consent = true, busy = false,
          onCreateProject = { error("Pending pages must be written first") }, onAppend = {}, onRetry = {}, onStop = {},
        )
      }
    }
    composeRule.onNodeWithTag("rain_live_new_project").assertIsNotEnabled()
    composeRule.onNodeWithTag("rain_live_append").assertIsNotEnabled()
    composeRule.onNodeWithTag("rain_live_retry").assertIsEnabled()
  }

  @Test
  fun cancellingLiveProjectDoesNotStartCapture() {
    var started = false
    var dismissed = false
    composeRule.setContent {
      MaterialTheme {
        RainProjectDialog(
          mode = RainProjectMode.LIVE, name = "Lecture", folder = "Folder", consent = true, busy = false, error = null,
          onNameChange = {}, onChooseFolder = {}, onConsentChange = {}, onConfirm = { started = true }, onDismiss = { dismissed = true },
        )
      }
    }
    composeRule.onNodeWithTag("rain_project_cancel").performClick()
    composeRule.runOnIdle { assertTrue(dismissed); assertFalse(started) }
  }
}
