package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsNavigationActivityTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun settingsAreOrganizedIntoCategories() {
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("bookshelf_settings").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.waitUntil(10_000) { compose.onAllNodesWithTag("reader_settings_save").fetchSemanticsNodes().isNotEmpty() }
    screenshot("reader-settings-appearance.png")
    compose.onNodeWithTag("settings_category_reading").assertIsDisplayed().performClick()
    compose.onNodeWithTag("prepare_ahead_slider").assertExists()
    screenshot("reader-settings-reading.png")
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
