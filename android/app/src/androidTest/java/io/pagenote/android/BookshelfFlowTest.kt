package io.pagenote.android

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BookshelfFlowTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun narrowBookshelfKeepsSearchUsableAndLeavesSpaceForBooks() {
    org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp < 600)
    val store = BookshelfStore(compose.activity)
    val previous = runBlocking { store.preferences.first() }
    try {
      waitTag("bookshelf_home")
      val search = compose.onNodeWithTag("bookshelf_search").fetchSemanticsNode().boundsInRoot
      val density = compose.activity.resources.displayMetrics.density
      assertTrue("search field must have usable width, was ${search.width / density} dp", search.width / density >= 140f)
      assertTrue("search field must not stretch vertically, was ${search.height / density} dp", search.height / density < 100f)
      val title = compose.onNodeWithText(compose.activity.getString(R.string.bookshelf_title)).fetchSemanticsNode().boundsInRoot
      assertTrue("bookshelf title needs enough room, was ${title.width / density} dp", title.width / density >= 120f)
      compose.onNodeWithTag("bookshelf_more").performClick()
      compose.onNodeWithTag("bookshelf_view").assertIsDisplayed()
      compose.onNodeWithTag("bookshelf_refresh").assertIsDisplayed()
      compose.onNodeWithTag("bookshelf_view").performClick()
      compose.waitUntil(5_000) { runBlocking { store.preferences.first().view != previous.view } }
      compose.onNodeWithTag("bookshelf_more").performClick()
      compose.onNodeWithTag("bookshelf_refresh").assertIsDisplayed()
    } finally {
      runBlocking { store.presentation(previous.sort, previous.view) }
    }
  }

  @Test fun boundRootCreatesDiscoversCustomizesAndSavesOnReturn(): Unit = runBlocking {
    val activity = compose.activity
    val resolver = activity.contentResolver
    val store = BookshelfStore(activity)
    val previous = store.preferences.first()
    val access = SafPreparedDocumentAccess(resolver)
    val baseTree = ProjectCreateSafTestProvider.treeUri()
    val base = DocumentsContract.buildDocumentUriUsingTree(baseTree, "root")
    val folder = Uri.parse(access.createDirectory(base.toString(), "bookshelf-flow-${UUID.randomUUID()}"))
    val tree = DocumentsContract.buildTreeDocumentUri(baseTree.authority, DocumentsContract.getDocumentId(folder))
    activity.grantUriPermission(activity.packageName, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    try {
      store.presentation("updated", "grid")
      store.bind(tree.toString(), "Test library")
      waitTag("bookshelf_home")
      compose.waitUntil(10_000) { compose.onAllNodesWithText("Test library").fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithText("Test library").assertIsDisplayed()
      compose.onNodeWithTag("bookshelf_new_cover").assertDoesNotExist()
      compose.onNodeWithTag("bookshelf_create_menu").performClick()
      compose.onNodeWithTag("bookshelf_create_new").performClick()
      compose.onNodeWithTag("new_project_name_input").performTextInput("Shelf notebook")
      compose.onNodeWithTag("new_project_confirm").performClick()
      waitTag("reader_bookshelf")
      val session = ViewModelProvider(compose.activity)[ReaderSessionViewModel::class.java]
      compose.runOnUiThread { session.setNoteDraft("Saved from bookshelf navigation") }
      compose.onNodeWithTag("reader_bookshelf").performClick()
      waitTag("bookshelf_book_Shelf notebook")
      compose.onNodeWithText("Note").assertIsDisplayed()
      val book = BookshelfRepository(activity).scan(tree).books.single()
      assertEquals("Saved from bookshelf navigation", ProjectNoteStore(resolver, book.project.notesUri).read())
      compose.onNodeWithTag("bookshelf_customize_Shelf notebook").performScrollTo().performClick()
      compose.onNodeWithTag("bookshelf_display_title").performTextReplacement("Custom title")
      compose.onNodeWithTag("bookshelf_paper_rose").performClick()
      compose.onNodeWithTag("bookshelf_appearance_save").performClick()
      compose.waitUntil(10_000) { compose.onAllNodesWithText("Custom title").fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithTag("bookshelf_search").performTextInput("not-found")
      compose.onNodeWithTag("bookshelf_book_Shelf notebook").assertDoesNotExist()
      compose.onNodeWithTag("bookshelf_search").performTextClearance()
      if (compose.onAllNodesWithTag("bookshelf_view").fetchSemanticsNodes().isEmpty()) {
        compose.onNodeWithTag("bookshelf_more").performClick()
      }
      compose.onNodeWithTag("bookshelf_view").performClick()
      waitTag("bookshelf_list")
      waitTag("bookshelf_book_Shelf notebook")
      compose.onNodeWithTag("bookshelf_book_Shelf notebook").performClick()
      waitTag("reader_bookshelf")
      assertEquals("Saved from bookshelf navigation", session.noteDraft.value)
      compose.activityRule.scenario.recreate()
      waitTag("reader_bookshelf")
      compose.onNodeWithTag("reader_bookshelf").performClick()
      waitTag("bookshelf_book_Shelf notebook")
      assertEquals(tree.toString(), store.preferences.first().tree)
      val metadata = BookshelfRepository(activity).scan(tree).books.single().appearance
      assertEquals("Custom title", metadata.title)
      assertEquals("rose", metadata.paper)
      compose.onNodeWithTag("bookshelf_settings").performClick()
      if (compose.onAllNodesWithTag("settings_library_folder").fetchSemanticsNodes().isEmpty()) {
        compose.onNodeWithTag("settings_category_appearance").performClick()
      }
      compose.onNodeWithTag("settings_library_folder").assertIsDisplayed()
      if (compose.onAllNodesWithTag("settings_category_back").fetchSemanticsNodes().isNotEmpty()) {
        compose.onNodeWithTag("settings_category_back").performClick()
      }
      compose.onNodeWithTag("reader_settings_cancel").performClick()
    } finally {
      store.bind(previous.tree, previous.name)
      store.presentation(previous.sort, previous.view)
      resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      DocumentsContract.deleteDocument(resolver, folder)
    }
  }

  private fun waitTag(tag: String) {
    try { compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    catch (failure: Exception) {
      throw AssertionError("Missing $tag: ${compose.onRoot().printToString()}", failure)
    }
  }
}
