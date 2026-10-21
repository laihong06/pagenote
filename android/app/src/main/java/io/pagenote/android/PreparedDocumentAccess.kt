package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface PreparedDocumentAccess {
  fun findChild(parent: String, name: String): String?
  fun createDirectory(parent: String, name: String): String
  fun createFile(parent: String, mimeType: String, name: String): String
  fun delete(uri: String): Boolean
  fun openInput(uri: String): InputStream?
  fun openOutput(uri: String): OutputStream?
}

internal fun <T : Any> requireChildQueryCursor(cursor: T?): T =
  cursor ?: throw IOException("SAF provider returned no child query cursor")

class SafPreparedDocumentAccess(private val resolver: ContentResolver) : PreparedDocumentAccess {
  override fun findChild(parent: String, name: String): String? {
    val parentUri = Uri.parse(parent)
    val parentId = DocumentsContract.getDocumentId(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
    val columns = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    )
    val cursor = requireChildQueryCursor(resolver.query(childrenUri, columns, null, null, null))
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) {
          return DocumentsContract.buildDocumentUriUsingTree(parentUri, it.getString(idColumn)).toString()
        }
      }
    }
    return null
  }

  override fun createDirectory(parent: String, name: String): String =
    DocumentsContract.createDocument(
      resolver, Uri.parse(parent), DocumentsContract.Document.MIME_TYPE_DIR, name,
    )?.toString() ?: throw IllegalStateException("SAF provider did not create directory $name")

  override fun createFile(parent: String, mimeType: String, name: String): String =
    DocumentsContract.createDocument(resolver, Uri.parse(parent), mimeType, name)?.toString()
      ?: throw IllegalStateException("SAF provider did not create file $name")

  override fun delete(uri: String): Boolean = DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
  override fun openInput(uri: String): InputStream? = resolver.openInputStream(Uri.parse(uri))
  override fun openOutput(uri: String): OutputStream? = resolver.openOutputStream(Uri.parse(uri), "wt")
}
