package io.pagenote.android

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun localizedReaderHome_remainsAvailableAfterSettingsLoad() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_menu_command_open_project").assertIsDisplayed()
  }

  @Test
  fun readerCommandsAreVisibleUnderDesktopMenuGroups() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_group_file").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_menu_group_file").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_command_open_project").assertIsEnabled()
    composeRule.onNodeWithTag("reader_menu_group_ai_annotations").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_group_reading").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_group_handwriting").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_group_settings").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("reader_menu_command_page_tutor")
      .performScrollTo()
      .assertIsDisplayed()
      .assertIsNotEnabled()
      .assertTextContains(composeRule.activity.getString(R.string.reader_menu_project_required))
    composeRule.onNodeWithTag("reader_menu_command_office_edit")
      .performScrollTo()
      .assertIsNotEnabled()
      .assertTextContains(composeRule.activity.getString(R.string.reader_menu_office_source_required))
    composeRule.onNodeWithTag("reader_menu_command_attachments")
      .performScrollTo()
      .assertIsNotEnabled()
      .assertTextContains(composeRule.activity.getString(R.string.reader_menu_attachments_unavailable))
    composeRule.onNodeWithTag("reader_menu_command_save_return")
      .performScrollTo()
      .assertIsNotEnabled()
      .assertTextContains(composeRule.activity.getString(R.string.reader_menu_office_source_required))
    composeRule.onNodeWithTag("reader_menu_command_about")
      .performScrollTo()
      .assertIsNotEnabled()
      .assertTextContains(composeRule.activity.getString(R.string.reader_menu_unavailable_reason))
    assertTrue(
      "a disabled menu item must not claim that the target Android 14 OS version is unsupported",
      !composeRule.activity.getString(R.string.reader_menu_unavailable_reason).contains("Android", ignoreCase = true),
    )
    val chineseConfiguration = Configuration(composeRule.activity.resources.configuration).apply {
      setLocale(Locale.SIMPLIFIED_CHINESE)
    }
    val chineseResources = composeRule.activity.createConfigurationContext(chineseConfiguration).resources
    assertEquals("此命令尚未接入应用。", chineseResources.getString(R.string.reader_menu_unavailable_reason))
    assertEquals("附件浏览与管理尚未接入。", chineseResources.getString(R.string.reader_menu_attachments_unavailable))
    assertEquals("当前项目源不是 Office 文档。", chineseResources.getString(R.string.reader_menu_office_source_required))
    assertEquals("本地 Office 引擎尚未接入，暂时无法在设备上转换或编辑 Office 文档。", chineseResources.getString(R.string.reader_menu_office_engine_unavailable))
  }

  @Test
  fun readerSettingsCanOpenOpenAiModelSettings() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_settings_action").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_menu_command_settings_action").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("configure_agent_model").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("configure_agent_model")
      .performScrollTo()
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("agent_model_settings_title").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("agent_model_settings_load_failed").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("settings_save_error").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("reader_settings_load_failed").fetchSemanticsNodes().isNotEmpty()
    }
    val settingsErrors = composeRule.onAllNodesWithTag("settings_save_error").fetchSemanticsNodes()
    assertTrue("reader settings could not be saved: ${settingsErrors.firstOrNull()?.config}", settingsErrors.isEmpty())
    try {
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("save_agent_model_settings").fetchSemanticsNodes().isNotEmpty() ||
          composeRule.onAllNodesWithTag("agent_model_settings_load_error").fetchSemanticsNodes().isNotEmpty()
      }
    } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
      val saving = composeRule.onAllNodesWithTag("reader_settings_configure_saving").fetchSemanticsNodes().isNotEmpty()
      val configureAction = composeRule.onAllNodesWithTag("configure_agent_model").fetchSemanticsNodes().isNotEmpty()
      val readerLoading = composeRule.onAllNodesWithTag("reader_settings_loading").fetchSemanticsNodes().isNotEmpty()
      throw AssertionError(
        "settings transition timed out (saving=$saving, actionVisible=$configureAction, readerLoading=$readerLoading)",
        error,
      )
    }
    val loadErrors = composeRule.onAllNodesWithTag("agent_model_settings_load_error").fetchSemanticsNodes()
    assertTrue("model settings could not load: ${loadErrors.firstOrNull()?.config}", loadErrors.isEmpty())
    composeRule.onNodeWithTag("save_agent_model_settings").assertIsDisplayed()
  }
}
