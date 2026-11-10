package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookshelfHomeTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun compactBookshelfNavigationCanOpenAndClose() {
    compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("bookshelf_home")).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_navigation_open").performClick()
    compose.onNodeWithTag("bookshelf_navigation_all").assertIsDisplayed().performClick()
    compose.onNodeWithTag("bookshelf_search").assertIsDisplayed()
  }

  @Test fun coldLaunchShowsBookshelfInsteadOfRestoringReader() {
    compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("bookshelf_home")).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_home").assertIsDisplayed()
    if (compose.activity.resources.configuration.screenWidthDp >= 840) {
      compose.onNodeWithTag("bookshelf_sidebar").assertIsDisplayed()
    }
    compose.onNodeWithTag("bookshelf_settings").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_search").assertIsDisplayed()
    val width = compose.onNodeWithTag("bookshelf_search").fetchSemanticsNode().boundsInRoot.width
    val density = compose.activity.resources.displayMetrics.density
    assertTrue("Search needs at least 160 dp on a phone, was ${width / density}", width / density >= 160f)
  }
}
