package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

  @Test
  fun outsidePageInkSwitchSavesAndRestoresItsState() {
    val current = mutableStateOf(ReaderSettings())
    val saved = AtomicReference<ReaderSettings?>()
    composeRule.setContent {
      MaterialTheme {
        ReaderSettingsDialog(
          settings = current.value,
          onDismiss = {},
          onConfigureAgent = {},
          onSave = {
            saved.set(it)
            current.value = it
          },
        )
      }
    }

    composeRule.onNodeWithTag("allow_ink_outside_page_switch")
      .assertIsOff()
      .assertContentDescriptionEquals(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ink_outside_page_title),
      )
      .performSemanticsAction(SemanticsActions.OnClick) { onClick -> onClick() }
    composeRule.waitUntil(timeoutMillis = 5_000) {
      runCatching { composeRule.onNodeWithTag("allow_ink_outside_page_switch").assertIsOn() }.isSuccess
    }
    composeRule.onNodeWithTag("reader_settings_save").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { saved.get() != null }
    assertEquals(true, saved.get()?.allowInkOutsidePage)
    composeRule.waitUntil(timeoutMillis = 5_000) {
      runCatching { composeRule.onNodeWithTag("allow_ink_outside_page_switch").assertIsOn() }.isSuccess
    }
  }
}
