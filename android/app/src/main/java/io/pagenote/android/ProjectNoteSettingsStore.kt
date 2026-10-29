package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * Reads and writes the project-level note settings file
 * (`src/note-settings.json`) through SAF, mirroring [ProjectPenStore]'s
 * directory-resolution and recoverable-write patterns (port batch 3).
 *
 * Semantics:
 * - [read] returns [ProjectNoteSettings.DEFAULT] when the project never
 *   created the file; a structurally invalid file throws
 *   [IllegalArgumentException] and callers decide the fallback (the reader
 *   treats a corrupt file as "no settings" rather than blocking the open).
 * - [write] creates the file on first save and afterwards replaces it through
 *   [SafRecoverableDocumentWriter], leaving a SAF recovery copy on failure.
 */
class ProjectNoteSettingsStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
  settingsUri: Uri?,
) {
  private var settingsUri: Uri? = settingsUri

  @Volatile
  internal var lastRetainedRecoveryCopyName: String? = null
    private set

  fun read(): ProjectNoteSettings {
    val uri = resolveSettingsUri() ?: return ProjectNoteSettings.DEFAULT
    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
      it.readText()
    } ?: throw IOException("无法读取项目 note-settings.json")
    return ProjectNoteSettingsCodec.decode(text)
  }

  fun write(settings: ProjectNoteSettings) {
    lastRetainedRecoveryCopyName = null
    var uri = resolveSettingsUri()
    var createdUri: Uri? = null
    val encoded = ProjectNoteSettingsCodec.encode(settings)
    try {
      if (uri == null) {
        uri = DocumentsContract.createDocument(
          resolver,
          sourceDirectoryUri,
          "application/json",
          FILE_NAME,
        ) ?: throw IOException("无法在项目 src 目录创建 $FILE_NAME")
        createdUri = uri
        overwrite(uri, encoded)
      } else {
        val original = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
          it.readText()
        } ?: throw IOException("无法读取项目 $FILE_NAME")
        lastRetainedRecoveryCopyName = SafRecoverableDocumentWriter(
          resolver,
          uri,
          FILE_NAME,
        ).write(encoded, original)
      }
      settingsUri = uri
    } catch (error: Exception) {
      createdUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
      throw error
    }
  }

  private fun resolveSettingsUri(): Uri? {
    settingsUri?.let { return it }
    val parentId = DocumentsContract.getDocumentId(sourceDirectoryUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceDirectoryUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      ),
      null,
      null,
      null,
    ) ?: throw IOException("无法列出项目 src 目录")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == FILE_NAME) {
          return DocumentsContract.buildDocumentUriUsingTree(
            sourceDirectoryUri,
            it.getString(idColumn),
          ).also { uri -> settingsUri = uri }
        }
      }
    }
    return null
  }

  private fun overwrite(target: Uri, content: String) {
    val output = resolver.openOutputStream(target, "wt")
      ?: throw IOException("无法写入项目 $FILE_NAME")
    output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
  }

  private companion object {
    const val FILE_NAME = "note-settings.json"
  }
}
