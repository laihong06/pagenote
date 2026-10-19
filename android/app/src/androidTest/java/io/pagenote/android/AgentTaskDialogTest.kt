package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.agent.AgentEvent
import io.pagenote.agent.SerialPages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentTaskDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun showsInstructionAndRunAction() {
    setDialog()

    composeRule.onNodeWithTag("agent_task_instruction").assertIsDisplayed()
    composeRule.onNodeWithTag("agent_task_pages").assertIsDisplayed()
    composeRule.onNodeWithTag("agent_task_run").assertIsDisplayed()
    composeRule.onNodeWithTag("agent_task_dismiss").assertIsDisplayed()
  }

  @Test
  fun showsDoneScopeWithoutClaimingMore() {
    setDialog(event = AgentEvent.Done(pages = listOf(2), updated = listOf(2)))

    composeRule.onNodeWithTag("agent_task_result").assertIsDisplayed()
  }

  @Test
  fun showsFailedReason() {
    setDialog(event = AgentEvent.Failed("rate limited at step 3"))

    composeRule.onNodeWithTag("agent_task_result").assertIsDisplayed()
  }

  @Test
  fun showsBatchReport() {
    setDialog(
      report = SerialPages.Report(
        completed = listOf(2),
        updated = listOf(2),
        incomplete = listOf(3),
        stoppedByRateLimit = false,
      ),
      event = AgentEvent.Failed("Some pages incomplete: 3"),
    )

    composeRule.onNodeWithTag("agent_task_batch").assertIsDisplayed()
    composeRule.onNodeWithTag("agent_task_result").assertIsDisplayed()
  }

  @Test
  fun preventsDismissalWhileRunning() {
    setDialog(isRunning = true)

    composeRule.onNodeWithTag("agent_task_dismiss").assertIsNotEnabled()
    composeRule.onNodeWithTag("agent_task_run").assertIsNotEnabled()
  }

  @Test
  fun displaysErrorAndKeepsRunAvailableForRetry() {
    setDialog(errorMessage = "Request timed out.")

    composeRule.onNodeWithTag("agent_task_error").assertIsDisplayed()
    composeRule.onNodeWithTag("agent_task_run").assertIsEnabled()
  }

  private fun setDialog(
    instruction: String = "补个例子",
    isRunning: Boolean = false,
    errorMessage: String? = null,
    event: AgentEvent? = null,
    report: SerialPages.Report? = null,
  ) {
    composeRule.setContent {
      MaterialTheme {
        AgentTaskDialog(
          page = 2,
          instruction = instruction,
          pageRange = "",
          report = report,
          isRunning = isRunning,
          errorMessage = errorMessage,
          event = event,
          onInstructionChange = {},
          onPageRangeChange = {},
          onRun = { _, _ -> },
          onDismiss = {},
        )
      }
    }
  }
}
