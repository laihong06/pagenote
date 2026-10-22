package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class BookshelfHomeTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun coldLaunchShowsBookshelfInsteadOfRestoringReader() {
    compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("bookshelf_home")).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_home").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_settings").assertIsDisplayed()
    compose.onNodeWithTag("bookshelf_search").assertIsDisplayed()
  }
}
