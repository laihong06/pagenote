package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookshelfTabletLayoutTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun tabletShowsSidebarAndCanOpenSettings() {
    assertTrue("Tablet test requires at least 840dp width", compose.activity.resources.configuration.screenWidthDp >= 840)
    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("bookshelf_home")).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_sidebar").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_sidebar_toggle").performClick()
    compose.onNodeWithTag("bookshelf_sidebar_collapsed").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_sidebar_toggle").performClick()
    compose.onNodeWithTag("bookshelf_sidebar").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.onNodeWithTag("reader_settings_cancel").assertIsDisplayed()
    compose.onNodeWithTag("reader_settings_page").assertIsDisplayed()
    android.os.SystemClock.sleep(700)
    val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
    val file = File(compose.activity.getExternalFilesDir(null), "tablet-settings.png")
    ui.takeScreenshot().also { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    ui.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/pagenote-tablet-settings.png").close()
    compose.onNodeWithTag("reader_settings_cancel").performClick()
    compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("reader_settings_cancel")).fetchSemanticsNodes().isEmpty() }
    compose.waitForIdle()
    android.os.SystemClock.sleep(500)
    val shelf = File(compose.activity.getExternalFilesDir(null), "tablet-bookshelf.png")
    ui.takeScreenshot().also { bitmap -> shelf.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    ui.executeShellCommand("cp ${shelf.absolutePath} /data/local/tmp/pagenote-tablet-bookshelf.png").close()
  }
}
