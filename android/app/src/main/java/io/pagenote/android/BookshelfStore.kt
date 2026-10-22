package io.pagenote.android

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private val Context.bookshelfDataStore by preferencesDataStore(name = "bookshelf")

internal data class BookshelfPreferences(
  val tree: String? = null,
  val name: String = "",
  val sort: String = "updated",
  val view: String = "grid",
)

internal class BookshelfStore(context: Context) {
  private val store = context.applicationContext.bookshelfDataStore
  private val treeKey = stringPreferencesKey("tree")
  private val nameKey = stringPreferencesKey("name")
  private val sortKey = stringPreferencesKey("sort")
  private val viewKey = stringPreferencesKey("view")
  val preferences = store.data.map {
    BookshelfPreferences(it[treeKey], it[nameKey].orEmpty(), it[sortKey] ?: "updated", it[viewKey] ?: "grid")
  }
  suspend fun bind(tree: String?, name: String) {
    store.edit { if (tree == null) it.remove(treeKey) else it[treeKey] = tree; it[nameKey] = name }
  }
  suspend fun presentation(sort: String, view: String) {
    require(sort in setOf("updated", "name") && view in setOf("grid", "list"))
    store.edit { it[sortKey] = sort; it[viewKey] = view }
  }

  suspend fun saveCheckpoint(checkpoint: ReaderSessionCheckpoint) {
    val position = BookReadingPosition(checkpoint.projectId, checkpoint.pageIndex,
      checkpoint.viewportBookmark.scale, checkpoint.viewportBookmark.centerX, checkpoint.viewportBookmark.centerY, checkpoint.penToolName)
    store.edit { it[positionKey(checkpoint.accessTreeUri, checkpoint.rootDocumentId)] = position.encode() }
  }

  suspend fun checkpoint(target: ProjectOpenTarget): ReaderSessionCheckpoint? {
    val text = store.data.first()[positionKey(target.accessTreeUri.toString(), target.rootDocumentId)] ?: return null
    return try {
      val position = BookReadingPosition.decode(text)
      ReaderSessionCheckpoint(target.accessTreeUri.toString(), target.rootDocumentId, position.projectId, position.page,
        PdfViewportBookmark(position.scale, position.x.coerceIn(0f, 1f), position.y.coerceIn(0f, 1f)), position.pen)
    } catch (_: IllegalArgumentException) { null }
  }

  private fun positionKey(tree: String, root: String) = stringPreferencesKey("position_" +
    MessageDigest.getInstance("SHA-256").digest("$tree|$root".toByteArray()).joinToString("") { "%02x".format(it) })
}
