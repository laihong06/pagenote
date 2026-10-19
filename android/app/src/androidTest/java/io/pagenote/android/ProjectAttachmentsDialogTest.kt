package io.pagenote.android

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectAttachmentsDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun imageCanBeInsertedAndDeleteRequiresConfirmation() {
    val image = ProjectAttachmentEntry(
      assetName = "01234567-89ab-cdef-0123-456789abcdef.png",
      uri = Uri.parse("content://project/assets/image.png"),
      mimeType = "image/png",
      sizeBytes = 128,
    )
    val inserted = mutableStateOf<ProjectAttachmentEntry?>(null)
    val deleted = mutableStateOf<ProjectAttachmentEntry?>(null)

    composeRule.setContent {
      MaterialTheme {
        ProjectAttachmentsDialog(
          entries = listOf(image),
          isLoading = false,
          isImporting = false,
          errorMessage = null,
          onImport = {},
          onInsertImage = { inserted.value = it },
          onDelete = { deleted.value = it },
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("attachment_insert_0").performClick()
    composeRule.runOnIdle { assertEquals(image, inserted.value) }
    composeRule.onNodeWithTag("attachment_delete_0").performClick()
    composeRule.onNodeWithTag("attachment_delete_confirmation").assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(null, deleted.value) }
    composeRule.onNodeWithTag("attachment_delete_confirm").performClick()
    composeRule.runOnIdle { assertEquals(image, deleted.value) }
    assertEquals(0, composeRule.onAllNodesWithTag("attachment_delete_confirmation").fetchSemanticsNodes().size)
  }
}
