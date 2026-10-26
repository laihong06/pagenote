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
      compose.onNodeWithTag("bookshelf_new").performClick()
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
      compose.onNodeWithTag("bookshelf_customize_Shelf notebook").performClick()
      compose.onNodeWithTag("bookshelf_display_title").performTextReplacement("Custom title")
      compose.onNodeWithTag("bookshelf_paper_rose").performClick()
      compose.onNodeWithTag("bookshelf_appearance_save").performClick()
      compose.waitUntil(10_000) { compose.onAllNodesWithText("Custom title").fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithTag("bookshelf_search").performTextInput("not-found")
      compose.onNodeWithTag("bookshelf_book_Shelf notebook").assertDoesNotExist()
      compose.onNodeWithTag("bookshelf_search").performTextClearance()
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
      compose.onNodeWithTag("settings_library_folder").assertIsDisplayed()
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
