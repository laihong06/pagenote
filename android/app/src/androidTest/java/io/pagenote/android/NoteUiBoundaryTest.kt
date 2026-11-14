package io.pagenote.android

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteUiBoundaryTest {
  @get:Rule val compose = createComposeRule()

  @Test fun busyBookshelfCannotChangeFolderFromDrawer() {
    compose.setContent {
      val repository = BookshelfRepository(LocalContext.current)
      MaterialTheme {
        BookshelfScreen(null, emptyList(), false, null, emptyList(), true,
          {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, repository)
      }
    }
    compose.onNodeWithTag("bookshelf_navigation_open").performClick()
    compose.onNodeWithTag("bookshelf_navigation_change").assertIsNotEnabled()
  }

  @Test fun settingsPageDismissesFromBackButton() {
    val dismissed = AtomicBoolean(false)
    compose.setContent {
      val wide = Configuration(LocalConfiguration.current).apply { screenWidthDp = 1000 }
      CompositionLocalProvider(LocalConfiguration provides wide) {
        MaterialTheme { ReaderSettingsScreen(settings = ReaderSettings(), onDismiss = { dismissed.set(true) }, onConfigureAgent = {}, onSave = {}) }
      }
    }
    compose.onNodeWithTag("reader_settings_cancel").performClick()
    assertTrue(dismissed.get())
  }

  @Test fun settingsPageDoesNotDismissWhileSaving() {
    val dismissed = AtomicBoolean(false)
    compose.setContent {
      val wide = Configuration(LocalConfiguration.current).apply { screenWidthDp = 1000 }
      CompositionLocalProvider(LocalConfiguration provides wide) {
        MaterialTheme { ReaderSettingsScreen(settings = ReaderSettings(), onDismiss = { dismissed.set(true) }, onConfigureAgent = {}, onSave = { awaitCancellation() }) }
      }
    }
    compose.onNodeWithTag("reader_settings_save").performClick()
    compose.onNodeWithTag("reader_settings_cancel").assertIsNotEnabled()
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    compose.waitForIdle()
    assertFalse(dismissed.get())
  }
}
