package io.pagenote.android

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

internal fun existingProjectTestTreeUri(): Uri = DocumentsContract.buildTreeDocumentUri(
  ExistingProjectSafTestProvider.AUTHORITY,
  ExistingProjectSafTestProvider.ROOT_ID,
)

internal fun minimalReaderTestPdf(): ByteArray {
  val document = PdfDocument()
  return try {
    val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
    page.canvas.drawColor(android.graphics.Color.WHITE)
    page.canvas.drawText("pagenote reader test", 48f, 64f, Paint(Paint.ANTI_ALIAS_FLAG))
    document.finishPage(page)
    ByteArrayOutputStream().use { output ->
      document.writeTo(output)
      output.toByteArray()
    }
  } finally {
    document.close()
  }
}

/** Enters a fixture through the same bookshelf path that users use. */
internal fun openFixtureProjectFromBookshelf(
  composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
  treeUri: Uri,
  projectName: String,
) {
  val activity = composeRule.activity
  val alreadyInReader = composeRule.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty()
  val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
  activity.grantUriPermission(
    activity.packageName,
    treeUri,
    permissionFlags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
  )
  activity.contentResolver.takePersistableUriPermission(treeUri, permissionFlags)
  if (!alreadyInReader) {
    waitForTaggedNode(composeRule, "bookshelf_home", 10_000)
    runBlocking { BookshelfStore(activity).bind(treeUri.toString(), "Android test library") }

    val bookTag = "bookshelf_book_$projectName"
    waitForTaggedNode(composeRule, bookTag, 15_000)
    composeRule.onNodeWithTag(bookTag).performClick()
    waitForTaggedNode(composeRule, "reader_page_status", 30_000)
  }
  if (composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes().isNotEmpty()) {
    composeRule.onNodeWithTag("reader_menu_expand").performClick()
  } else if (composeRule.onAllNodesWithTag("reader_menu_trigger").fetchSemanticsNodes().isNotEmpty()) {
    composeRule.onNodeWithTag("reader_menu_trigger").performClick()
  }
}

internal fun revealReaderMenuCommand(
  composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
  group: String,
  command: String,
) {
  val commandTag = "reader_menu_command_$command"
  if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()) return

  val groupTag = "reader_menu_group_$group"
  if (composeRule.onAllNodesWithTag(groupTag).fetchSemanticsNodes().isNotEmpty()) {
    composeRule.onNodeWithTag("reader_menu_group_$group").performClick()
  } else if (composeRule.onAllNodesWithTag("reader_menu_trigger").fetchSemanticsNodes().isNotEmpty()) {
    composeRule.onNodeWithTag("reader_menu_trigger").performClick()
  }

  waitForTaggedNode(composeRule, commandTag, 10_000)
}

private fun waitForTaggedNode(
  composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
  tag: String,
  timeoutMillis: Long,
) {
  val deadline = SystemClock.uptimeMillis() + timeoutMillis
  do {
    if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
    SystemClock.sleep(50)
  } while (SystemClock.uptimeMillis() < deadline)
  error("Timed out waiting for Compose test tag '$tag' after ${timeoutMillis}ms")
}
