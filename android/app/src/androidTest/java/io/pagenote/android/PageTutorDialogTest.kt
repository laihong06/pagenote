package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.agent.PageTutorResult
import io.pagenote.agent.PageTutorFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTutorDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun showsInstructionAndGenerateAction() {
    setDialog()

    composeRule.onNodeWithTag("page_tutor_instruction").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_explain").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_dismiss").assertIsDisplayed()
  }

  @Test
  fun keepsModelOutputInPreviewUntilAppend() {
    var appendCalls = 0
    setDialog(
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "**AI preview**",
        warnings = emptyList(),
      ),
      onAppend = { appendCalls += 1 },
    )

    composeRule.onNodeWithTag("page_tutor_preview").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_append").assertIsDisplayed()
    assertEquals(0, appendCalls)
    composeRule.onNodeWithTag("page_tutor_append").performClick()
    assertEquals(1, appendCalls)
  }

  @Test
  fun disablesAppendForReadOnlyNote() {
    setDialog(
      noteWritable = false,
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "AI preview",
        warnings = emptyList(),
      ),
    )

    composeRule.onNodeWithTag("page_tutor_append").assertIsNotEnabled()
  }

  @Test
  fun disablesAppendWhenInstructionNoLongerMatchesPreview() {
    setDialog(
      instruction = "Use a different explanation style.",
      previewInstruction = "Explain this page.",
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "AI preview for the old instruction",
        warnings = emptyList(),
      ),
    )

    composeRule.onNodeWithTag("page_tutor_stale_preview").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_append").assertIsNotEnabled()
  }

  @Test
  fun displaysRequestErrorAndKeepsGenerateAvailableForRetry() {
    setDialog(errorMessage = "Request timed out.")

    composeRule.onNodeWithTag("page_tutor_error").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_explain").assertIsEnabled()
  }

  @Test
  fun displaysOcrReviewWarningsWithThePreview() {
    setDialog(
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "AI preview",
        warnings = listOf("OCR confidence is low; verify the original page."),
      ),
    )

    composeRule.onNodeWithText("OCR confidence is low; verify the original page.").assertIsDisplayed()
  }

  @Test
  fun showsSelectedRegionTextAndWarningsBeforeRequest() {
    setDialog(
      focus = PageTutorFocus(
        label = "Selected OCR region",
        text = "1. selected equation (91%)",
        warnings = listOf("OCR confidence is low; verify the original page."),
      ),
    )

    composeRule.onNodeWithTag("page_tutor_region_focus").assertIsDisplayed()
    composeRule.onNodeWithText("1. selected equation (91%)").assertIsDisplayed()
    composeRule.onNodeWithText("OCR confidence is low; verify the original page.").assertIsDisplayed()
  }

  @Test
  fun preventsDismissalWhileRequestIsRunning() {
    setDialog(isRunning = true)

    composeRule.onNodeWithTag("page_tutor_dismiss").assertIsNotEnabled()
    composeRule.onNodeWithTag("page_tutor_explain").assertIsNotEnabled()
  }

  @Test
  fun preventsDuplicateAppendWhileSaving() {
    var appendCalls = 0
    setDialog(
      isSaving = true,
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "AI preview",
        warnings = emptyList(),
      ),
      onAppend = { appendCalls += 1 },
    )

    composeRule.onNodeWithTag("page_tutor_append").assertIsNotEnabled()
    composeRule.onNodeWithTag("page_tutor_dismiss").assertIsNotEnabled()
    assertEquals(0, appendCalls)
  }

  private fun setDialog(
    isRunning: Boolean = false,
    isSaving: Boolean = false,
    instruction: String = "Explain this page.",
    noteWritable: Boolean = true,
    preview: PageTutorResult? = null,
    focus: PageTutorFocus? = null,
    previewInstruction: String? = if (preview == null) null else instruction,
    errorMessage: String? = null,
    onAppend: () -> Unit = {},
  ) {
    composeRule.setContent {
      MaterialTheme {
        PageTutorDialog(
          page = 2,
          focus = focus,
          instruction = instruction,
          previewInstruction = previewInstruction,
          isRunning = isRunning,
          isSaving = isSaving,
          errorMessage = errorMessage,
          preview = preview,
          noteWritable = noteWritable,
          onInstructionChange = {},
          onExplain = {},
          onAppend = onAppend,
          onDismiss = {},
        )
      }
    }
  }
}
