package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class NewProjectDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun createDialogOpensAndConfirms() {
    composeRule.setContent {
      MaterialTheme {
        var name by remember { mutableStateOf("") }
        var confirmed by remember { mutableStateOf(false) }
        NewProjectDialog(
          name = name,
          onNameChange = { name = it },
          hasDir = true,
          pdfDisplayName = null,
          error = null,
          creating = false,
          onChooseDir = {},
          onChoosePdf = {},
          onClearPdf = {},
          onConfirm = { confirmed = true },
          onDismiss = {},
        )
        if (confirmed) {
          androidx.compose.material3.Text("done", modifier = Modifier.testTag("done"))
        }
      }
    }

    composeRule.onNodeWithTag("new_project_confirm").performClick()
    composeRule.onNodeWithTag("done").assertIsDisplayed()
  }

  @Test
  fun cancelDismisses() {
    var dismissed = false
    composeRule.setContent {
      MaterialTheme {
        NewProjectDialog(
          name = "",
          onNameChange = {},
          hasDir = false,
          pdfDisplayName = null,
          error = null,
          creating = false,
          onChooseDir = {},
          onChoosePdf = {},
          onClearPdf = {},
          onConfirm = {},
          onDismiss = { dismissed = true },
        )
      }
    }

    composeRule.onNodeWithTag("new_project_cancel").performClick()
    composeRule.runOnIdle { assert(dismissed) }
  }

  @Test
  fun optionalPdfCanBeSelectedAndClearedWithoutLosingDialog() {
    composeRule.setContent {
      MaterialTheme {
        var pdfName by remember { mutableStateOf<String?>(null) }
        NewProjectDialog(
          name = "Blank worksheet",
          onNameChange = {},
          hasDir = true,
          pdfDisplayName = pdfName,
          error = null,
          creating = false,
          onChooseDir = {},
          onChoosePdf = { pdfName = "whiteboard.pdf" },
          onClearPdf = { pdfName = null },
          onConfirm = {},
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("new_project_choose_pdf").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("whiteboard.pdf").assertIsDisplayed()
    composeRule.onNodeWithTag("new_project_clear_pdf").performClick()
    composeRule.onNodeWithText("Choose PDF (optional)").assertIsDisplayed()
    composeRule.onNodeWithTag("new_project_confirm").assertIsDisplayed()
  }
}
