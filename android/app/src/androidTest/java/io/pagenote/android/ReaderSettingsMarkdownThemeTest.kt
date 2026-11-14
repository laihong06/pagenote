package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsMarkdownThemeTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun settingsCategoryNavigationReachesAudioOptions() {
    composeRule.setContent {
      MaterialTheme {
        ReaderSettingsScreen(settings = ReaderSettings(), onDismiss = {}, onConfigureAgent = {}, onSave = {})
      }
    }
    showSettingsCategory("recognition")
    composeRule.onNodeWithTag("audio_quota_slider").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun settingsCategoriesReachReaderNightAndModelOptions() {
    composeRule.setContent {
      MaterialTheme {
        ReaderSettingsScreen(settings = ReaderSettings(), onDismiss = {}, onConfigureAgent = {}, onSave = {})
      }
    }
    showSettingsCategory("reading")
    composeRule.onNodeWithTag("prepare_ahead_slider").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("night_filter_switch").performScrollTo().assertIsDisplayed()
    showSettingsCategory("agent")
    composeRule.onNodeWithTag("configure_agent_model").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun savesMarkdownThemeSeparatelyFromReaderAppearance() {
    val saved = AtomicReference<ReaderSettings?>()
    composeRule.setContent {
      MaterialTheme {
        ReaderSettingsScreen(
          settings = ReaderSettings(theme = "dark"),
          onDismiss = {},
          onConfigureAgent = {},
          onSave = { saved.set(it) },
        )
      }
    }

    showAppearanceIfNeeded()
    composeRule.onNodeWithTag("markdown_theme_paper").performScrollTo().performClick()
    composeRule.onNodeWithTag("reader_settings_save").performClick()
    composeRule.waitUntil(5_000) { saved.get() != null }

    assertEquals("paper", saved.get()?.markdownTheme)
    assertEquals("dark", saved.get()?.theme)
  }

  @Test
  fun cancelDiscardsMarkdownThemeDraft() {
    val saved = AtomicReference<ReaderSettings?>()
    val dismissed = AtomicBoolean(false)
    composeRule.setContent {
      MaterialTheme {
        ReaderSettingsScreen(
          settings = ReaderSettings(),
          onDismiss = { dismissed.set(true) },
          onConfigureAgent = {},
          onSave = { saved.set(it) },
        )
      }
    }

    showAppearanceIfNeeded()
    composeRule.onNodeWithTag("markdown_theme_paper").performScrollTo().performClick()
    if (composeRule.onAllNodesWithTag("settings_category_back").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("settings_category_back").performClick()
    }
    composeRule.onNodeWithTag("reader_settings_cancel").performClick()
    composeRule.waitUntil(5_000) { dismissed.get() }

    assertFalse("cancel must not persist the draft", saved.get() != null)
  }

  private fun showSettingsCategory(category: String) {
    if (composeRule.onAllNodesWithTag("settings_category_back").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("settings_category_back").performClick()
    }
    composeRule.onNodeWithTag("settings_category_$category").performScrollTo().performClick()
  }

  private fun showAppearanceIfNeeded() {
    if (composeRule.onAllNodesWithTag("settings_detail").fetchSemanticsNodes().isEmpty()) {
      composeRule.onNodeWithTag("settings_category_appearance").performClick()
    }
  }
}
