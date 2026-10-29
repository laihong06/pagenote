package io.pagenote.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class BookshelfBook(
  val target: ProjectOpenTarget,
  val project: OpenedProject,
  val appearance: BookAppearance,
  val updated: Long,
  val revision: String,
  val warning: String? = null,
) {
  val key: String get() = "${target.accessTreeUri}|${target.rootDocumentId}"
  val title: String get() = appearance.titleOr(project.name)
}

internal data class BookshelfScan(val books: List<BookshelfBook>, val warnings: List<String>)

internal class BookshelfRepository(private val context: Context) {
  private val resolver = context.contentResolver
  private val access = SafPreparedDocumentAccess(resolver)
  private data class Entry(val id: String, val name: String, val directory: Boolean, val modified: Long)

  suspend fun scan(tree: Uri): BookshelfScan = withContext(Dispatchers.IO) {
    directoryName(tree)
    val books = mutableListOf<BookshelfBook>()
    val warnings = mutableListOf<String>()
    val queue = ArrayDeque<Pair<String, Int>>()
    queue.add(DocumentsContract.getTreeDocumentId(tree) to 0)
    val visited = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val (id, depth) = queue.removeFirst()
      if (!visited.add(id)) continue
      if (visited.size > 2000) {
        warnings.add(context.getString(R.string.bookshelf_scan_limit)); break
      }
      try {
        val children = children(tree, id)
        if (children.any { it.name == "project.pagenote" && !it.directory }) {
          val opened = ProjectDocumentOpener(resolver).open(tree, id)
          var appearanceWarning: String? = null
          val appearance = try {
            access.findChild(opened.sourceDirectoryUri.toString(), "bookshelf.json")?.let { uri ->
              val text = access.openInput(uri)?.bufferedReader()?.use { it.readTextBounded(32_768) }
                ?: throw IOException("Cannot read bookshelf.json")
              BookAppearance.decode(text)
            } ?: BookAppearance()
          } catch (error: Exception) {
            appearanceWarning = error.message ?: "bookshelf.json"
            BookAppearance()
          }
          val sourceRevision = metadata(opened.sourceUri)
          val notesRevision = metadata(opened.notesUri)
          books.add(BookshelfBook(
            ProjectOpenTarget(tree, id, false), opened, appearance,
            maxOf(children.maxOfOrNull { it.modified } ?: 0, sourceRevision.first, notesRevision.first),
            "${sourceRevision.first}:${sourceRevision.second}" +
              if (sourceRevision.first == 0L) ":${System.nanoTime()}" else "",
            appearanceWarning,
          ))
        } else {
          val directories = children.filter { it.directory }
          if (depth >= 8 && directories.isNotEmpty()) warnings.add(context.getString(R.string.bookshelf_scan_limit))
          else directories.forEach { if (queue.size < 2000) queue.add(it.id to depth + 1) else warnings.add(context.getString(R.string.bookshelf_scan_limit)) }
        }
      } catch (error: kotlinx.coroutines.CancellationException) { throw error
      } catch (error: Exception) {
        if (depth == 0) throw error
        warnings.add("${id.substringAfterLast('/')}: ${error.message}")
      }
    }
    BookshelfScan(books, warnings.distinct())
  }

  suspend fun saveAppearance(book: BookshelfBook, appearance: BookAppearance) = withContext(Dispatchers.IO) {
    val text = appearance.encode()
    if (appearance.resolvedCover(book.project.sourceExtension) == "pdf") {
      withBookshelfPdf(context, book.project.sourceUri) { pdf ->
        require((if (appearance.cover == "default") 0 else appearance.page) < pdf.pageCount) { context.getString(R.string.bookshelf_bad_page, pdf.pageCount) }
      }
    }
    val parent = book.project.sourceDirectoryUri.toString()
    val existing = access.findChild(parent, "bookshelf.json")
    val backup = existing?.let { access.openInput(it)?.use { input ->
      input.readNBytes(32_769).also { require(it.size <= 32_768) { "Bookshelf metadata too large" } }
    } }
    val destination = existing ?: access.createFile(parent, "application/json", "bookshelf.json")
    try {
      access.openOutput(destination)?.bufferedWriter()?.use { it.write(text) }
        ?: throw IOException("Cannot write bookshelf.json")
    } catch (error: Exception) {
      runCatching {
        if (existing == null) access.delete(destination)
        else if (backup != null) access.openOutput(destination)?.use { it.write(backup) }
      }
      throw error
    }
  }

  suspend fun importCover(book: BookshelfBook, source: Uri, appearance: BookAppearance) = withContext(Dispatchers.IO) {
    val importer = ProjectImageImporter(resolver, context.cacheDir, book.project.sourceDirectoryUri)
    val imported = importer.import(source)
    try { saveAppearance(book, appearance.copy(cover = "image", asset = imported.assetName)) }
    catch (error: Exception) { runCatching { importer.deleteImported(imported.documentUri) }; throw error }
  }

  fun directoryName(tree: Uri): String {
    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
      if (it.moveToFirst() && it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)) == DocumentsContract.Document.MIME_TYPE_DIR)
        it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) else null
    } ?: throw IOException("Cannot access library directory")
  }

  private suspend fun children(tree: Uri, id: String): List<Entry> {
    val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
    return (resolver.query(uri, arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    ), null, null, null) ?: throw IOException("Cannot list library directory")).use { cursor ->
      val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
      buildList {
        while (cursor.moveToNext()) {
          currentCoroutineContext().ensureActive()
          if (size >= 10_000) throw IOException(context.getString(R.string.bookshelf_scan_limit))
          add(Entry(cursor.getString(idColumn), cursor.getString(nameColumn), cursor.getString(typeColumn) == DocumentsContract.Document.MIME_TYPE_DIR, cursor.getLong(modifiedColumn)))
        }
      }
    }
  }

  private fun metadata(uri: Uri): Pair<Long, Long> = resolver.query(uri, arrayOf(
    DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE,
  ), null, null, null)?.use {
    if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)) to
      it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)) else 0L to 0L
  } ?: (0L to 0L)
}

private fun java.io.Reader.readTextBounded(limit: Int): String {
  val text = StringBuilder()
  val buffer = CharArray(2048)
  while (true) {
    val count = read(buffer)
    if (count < 0) return text.toString()
    require(text.length + count <= limit) { "Bookshelf metadata too large" }
    text.append(buffer, 0, count)
  }
}
