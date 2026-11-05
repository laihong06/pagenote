package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsNavigationActivityTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun tabletSettingsFillThePageWithCategoryAndDetailSideBySide() {
    org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 600)
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_home").assertDoesNotExist()
    val categories = compose.onNodeWithTag("settings_category_list").fetchSemanticsNode().boundsInRoot
    val detail = compose.onNodeWithTag("settings_detail").fetchSemanticsNode().boundsInRoot
    assertTrue("categories and detail should be side by side", categories.right <= detail.left)
    compose.onNodeWithTag("settings_category_reading").performClick()
    compose.onNodeWithTag("prepare_ahead_slider").assertExists()
    compose.onNodeWithTag("settings_category_agent").assertIsDisplayed()
    screenshot("settings-tablet-page.png")
    compose.onNodeWithTag("reader_settings_cancel").performClick()
    compose.onNodeWithTag("bookshelf_home").assertIsDisplayed()
  }

  @Test fun phoneSettingsNavigateBetweenListAndDetailsWithoutClosingThePage() {
    org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp < 600)
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    compose.onNodeWithTag("settings_category_list").assertIsDisplayed()
    compose.onNodeWithTag("settings_detail").assertDoesNotExist()
    screenshot("settings-phone-list.png")
    compose.onNodeWithTag("settings_category_reading").performClick()
    compose.onNodeWithTag("settings_detail").assertIsDisplayed()
    compose.onNodeWithTag("settings_category_list").assertDoesNotExist()
    screenshot("settings-phone-detail.png")
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithTag("settings_category_list").assertIsDisplayed()
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithTag("bookshelf_home").assertIsDisplayed()
  }

  @Test fun phoneSettingsKeepDraftAcrossCategoriesAndSaveTogether() {
    org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp < 600)
    val store = readerSettingsStore(compose.activity.applicationContext)
    val original = runBlocking { store.settings.first() }
    val targetTheme = if (original.markdownTheme == "paper") "classic" else "paper"
    val targetPreparation = if (original.prepareAheadPages == 7) 6 else 7
    try {
      compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithTag("bookshelf_settings").performClick()
      compose.onNodeWithTag("settings_category_appearance").performClick()
      compose.onNodeWithTag("markdown_theme_$targetTheme").performScrollTo().performClick()
      compose.onNodeWithTag("settings_category_back").performClick()
      compose.onNodeWithTag("settings_category_reading").performClick()
      compose.onNodeWithTag("prepare_ahead_slider").performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> assertTrue(setProgress(targetPreparation.toFloat())) }
      compose.onNodeWithTag("settings_category_back").performClick()
      compose.onNodeWithTag("reader_settings_save").performClick()
      compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isNotEmpty() }
      val saved = runBlocking { store.settings.first() }
      assertEquals(targetTheme, saved.markdownTheme)
      assertEquals(targetPreparation, saved.prepareAheadPages)
    } finally {
      runBlocking { store.save(original) }
    }
  }

  @Test fun settingsDraftAndCurrentCategorySurviveActivityRecreation() {
    val store = readerSettingsStore(compose.activity.applicationContext)
    val original = runBlocking { store.settings.first() }
    val targetTheme = if (original.markdownTheme == "paper") "classic" else "paper"
    val narrow = compose.activity.resources.configuration.screenWidthDp < 600
    try {
      compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithTag("bookshelf_settings").performClick()
      if (narrow) compose.onNodeWithTag("settings_category_appearance").performClick()
      compose.onNodeWithTag("markdown_theme_$targetTheme").performScrollTo().performClick()
      if (narrow) compose.onNodeWithTag("settings_category_back").performClick()
      compose.onNodeWithTag("settings_category_reading").performClick()
      compose.activityRule.scenario.recreate()
      compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader_settings_page").fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithTag("prepare_ahead_slider").assertExists()
      compose.onNodeWithTag("reader_settings_save").performClick()
      compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_home").fetchSemanticsNodes().isNotEmpty() }
      assertEquals(targetTheme, runBlocking { store.settings.first() }.markdownTheme)
    } finally {
      runBlocking { store.save(original) }
    }
  }

  @Test fun tabletAgentConfigurationStaysInsideSettingsPage() {
    org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 600)
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.onNodeWithTag("settings_category_agent").performClick()
    compose.onNodeWithTag("configure_agent_model").performClick()
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("save_agent_model_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    compose.onNodeWithTag("settings_category_list").assertIsDisplayed()
    compose.onNodeWithTag("settings_detail").assertIsDisplayed()
    screenshot("settings-tablet-agent-model.png")
    compose.onNodeWithTag("settings_subpage_back").performClick()
    compose.onNodeWithTag("configure_agent_prompts").performClick()
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("agent_prompt_title").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    compose.onNodeWithTag("settings_subpage_back").performClick()
    compose.onNodeWithTag("configure_agent_model").assertIsDisplayed()
  }

  @Test fun phoneAgentConfigurationReturnsThroughSettingsLevels() {
    org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp < 600)
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.onNodeWithTag("settings_category_agent").performClick()
    compose.onNodeWithTag("configure_agent_model").performClick()
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("save_agent_model_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    compose.onNodeWithTag("settings_detail").assertIsDisplayed()
    screenshot("settings-phone-agent-model.png")
    compose.onNodeWithTag("settings_subpage_back").performClick()
    compose.onNodeWithTag("configure_agent_model").assertIsDisplayed()
    compose.onNodeWithTag("settings_category_back").performClick()
    compose.onNodeWithTag("settings_category_list").assertIsDisplayed()
  }

  @Test fun settingsAreOrganizedIntoCategories() {
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader_settings_save").fetchSemanticsNodes().isNotEmpty() }
    val narrow = compose.activity.resources.configuration.screenWidthDp < 600
    if (narrow) compose.onNodeWithTag("settings_category_appearance").performClick()
    screenshot("reader-settings-appearance.png")
    if (narrow) compose.onNodeWithTag("settings_category_back").performClick()
    compose.onNodeWithTag("settings_category_reading").assertIsDisplayed().performClick()
    compose.onNodeWithTag("prepare_ahead_slider").assertExists()
    screenshot("reader-settings-reading.png")
    if (narrow) compose.onNodeWithTag("settings_category_back").performClick()
    compose.onNodeWithTag("settings_category_agent").performClick()
    compose.onNodeWithTag("configure_agent_model").assertExists()
    screenshot("reader-settings-agent.png")
  }

  private fun screenshot(name: String) {
    compose.waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.waitForIdle(300, 5_000)
    android.os.SystemClock.sleep(600)
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    try {
      val output = File(instrumentation.targetContext.getExternalFilesDir(null), name)
      output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/pagenote-menu-evidence")).use { it.readBytes() }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/pagenote-menu-evidence/$name")).use { it.readBytes() }
    } finally { bitmap.recycle() }
  }
}
