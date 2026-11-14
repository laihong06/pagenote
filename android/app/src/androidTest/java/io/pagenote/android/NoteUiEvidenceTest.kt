package io.pagenote.android

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteUiEvidenceTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun captureReaderAndToolbox() {
    val name = "UI evidence"
    InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0).edit().clear().commit()
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val manifest = """{"format":"pagenote-project","version":1,"id":"${"d".repeat(64)}","name":"$name","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}"""
    resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_INSTALL, null, Bundle().apply {
      putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest.toByteArray())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalReaderTestPdf())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, "Page note".toByteArray())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
    })
    openFixtureProjectFromBookshelf(compose, existingProjectTestTreeUri(), name)
    compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("reader_pen_pen")).fetchSemanticsNodes().isNotEmpty() }
    if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("reader_menu_collapse")).fetchSemanticsNodes().isNotEmpty()) {
      compose.onNodeWithTag("reader_menu_collapse").performClick()
    } else {
      android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input tap 1000 1000")).use { it.readBytes() }
    }
    android.os.SystemClock.sleep(700)
    compose.waitForIdle()
    compose.onNodeWithTag("floating_pen_box").assertExists()
    capture("pen-box")
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.waitForIdle()
    capture("pen-groups")
    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("reader_pen_pen").performClick()
    compose.onNodeWithTag("reader_pen_style_menu").assertExists()
    compose.waitForIdle()
    capture("pen-style")
    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("floating_pen_box_handle").performClick()
    compose.waitForIdle()
    capture("pen-collapsed")
    compose.onNodeWithTag("floating_pen_box_handle").performClick()
    compose.waitForIdle()
    capture("reader")
    compose.onNodeWithTag("reader_menu_trigger").performClick()
    compose.waitForIdle()
    android.os.SystemClock.sleep(700)
    capture("reader-menu")
    listOf("file", "settings").forEach { tab ->
      if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("reader_menu_tab_$tab")).fetchSemanticsNodes().isNotEmpty()) {
        compose.onNodeWithTag("reader_menu_tab_$tab").performClick()
        compose.waitForIdle()
        capture("reader-menu-$tab")
      }
    }
    if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("reader_menu_collapse")).fetchSemanticsNodes().isNotEmpty()) {
      compose.onNodeWithTag("reader_menu_collapse").performClick()
    } else {
      InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }
    compose.waitForIdle()
    compose.onNodeWithTag("reader_canvas_focus").performClick()
    compose.waitForIdle()
    capture("canvas-focus")
    compose.onNodeWithTag("reader_canvas_focus").performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("reader_bookshelf").performClick()
    compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("bookshelf_book_$name")).fetchSemanticsNodes().isNotEmpty() }
    compose.waitForIdle()
    android.os.SystemClock.sleep(1000)
    capture("bookshelf")
    if (compose.activity.resources.configuration.screenWidthDp < 840) {
      compose.onNodeWithTag("bookshelf_navigation_open").performClick()
      compose.waitForIdle()
      android.os.SystemClock.sleep(700)
      capture("bookshelf-navigation")
      compose.onNodeWithTag("bookshelf_navigation_all").performClick()
      compose.waitForIdle()
    }
    compose.onNodeWithTag("bookshelf_create_menu").performClick()
    compose.waitForIdle()
    capture("bookshelf-create")
    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("bookshelf_settings").performClick()
    compose.waitForIdle()
    android.os.SystemClock.sleep(700)
    capture("settings")
  }

  private fun capture(name: String) {
    val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
    val file = File(compose.activity.getExternalFilesDir(null), "ui-$name.png")
    ui.takeScreenshot().also { image -> file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle() }
    ui.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/pagenote-ui-$name.png").close()
  }
}
