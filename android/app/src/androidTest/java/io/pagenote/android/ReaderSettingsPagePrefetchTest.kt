package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsPagePrefetchTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun preparationWindowSliderValueIsIncludedWhenSettingsAreSaved() {
    val saved = AtomicReference<ReaderSettings?>()
    composeRule.setContent {
      MaterialTheme {
        ReaderSettingsDialog(
          settings = ReaderSettings(),
          onDismiss = {},
          onConfigureAgent = {},
          onSave = { saved.set(it) },
        )
      }
    }

    composeRule.onNodeWithTag("prepare_ahead_slider")
      .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
        assertTrue(setProgress(7f))
      }
    composeRule.onNodeWithTag("reader_settings_save").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { saved.get() != null }

    assertEquals(7, saved.get()?.prepareAheadPages)
  }
}
