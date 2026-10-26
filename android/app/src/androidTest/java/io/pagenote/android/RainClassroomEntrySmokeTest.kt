package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class RainClassroomEntrySmokeTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun actualReaderOpensNewLiveProjectSettingsWithoutJoining() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("rainEntrySmoke") == "true")
    composeRule.waitUntil(20_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_rain_classroom").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_menu_command_rain_classroom").performScrollTo().performClick()
    composeRule.waitUntil(45_000) {
      val nodes = composeRule.onAllNodesWithTag("rain_live_new_project").fetchSemanticsNodes()
      nodes.singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false
    }
    composeRule.onNodeWithTag("rain_live_new_project").performScrollTo().performClick()
    composeRule.onNodeWithTag("rain_project_name").assertIsDisplayed()
    composeRule.onNodeWithTag("rain_project_folder").assertIsDisplayed()
    composeRule.onNodeWithTag("rain_project_confirm").assertIsNotEnabled()
    val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    try {
      File(composeRule.activity.cacheDir, "rain-live-project-entry.png").outputStream().use {
        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
      }
    } finally { screenshot.recycle() }
    composeRule.onNodeWithTag("rain_project_cancel").performClick()
    println("RAIN_ENTRY_SMOKE newProjectDialog=true joined=false")
  }
}
