package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.UUID

internal class SafRecoverableDocumentWriter(
  private val resolver: ContentResolver,
  private val targetUri: Uri,
  private val documentLabel: String,
) {
  fun write(updated: String, expectedOriginal: String): String? = RecoverableDocumentWrite(
    readTarget = ::readTarget,
    overwriteTarget = { content -> overwrite(targetUri, content) },
    createBackup = ::createRecoveryCopy,
    deleteBackup = { copy ->
      if (!DocumentsContract.deleteDocument(resolver, copy.uri)) {
        throw IOException("Unable to delete recovery copy: ${copy.displayName}")
      }
    },
    backupName = RecoveryCopy::displayName,
    documentLabel = documentLabel,
  ).write(updated, expectedOriginal)

  private fun readTarget(): String = resolver.openInputStream(targetUri)?.bufferedReader(Charsets.UTF_8)?.use {
    it.readText()
  } ?: throw IOException("Unable to read $documentLabel")

  private fun overwrite(target: Uri, content: String) {
    val output = resolver.openOutputStream(target, "wt") ?: throw IOException("Unable to write $documentLabel")
    output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
  }

  private fun createRecoveryCopy(original: String): RecoveryCopyPreparation<RecoveryCopy> {
    val path = DocumentsContract.findDocumentPath(resolver, targetUri)?.path
      ?: throw IOException("Unable to determine $documentLabel parent")
    val parent = path.getOrNull(path.lastIndex - 1)?.let {
      DocumentsContract.buildDocumentUriUsingTree(targetUri, it)
    }
    val displayName = "pagenote-recovery-${UUID.randomUUID()}.json"
    val preparation = RecoveryCopyPreparation.fromProvider(
      findParent = { parent },
      supportsCreate = { supportsFlag(it, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) },
      create = { directory ->
        DocumentsContract.createDocument(resolver, directory, "application/json", displayName)
          ?.let { copyUri -> RecoveryCopy(copyUri, displayName) }
      },
    )
    val copy = when (preparation) {
      RecoveryCopyPreparation.Unsupported -> return preparation
      is RecoveryCopyPreparation.Available -> preparation.copy
    }

    val supportsDelete = runCatching {
      supportsFlag(copy.uri, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)
    }.getOrElse { error ->
      removeRecoveryCopyOrThrow(copy, error)
      throw IOException("Unable to verify recovery-copy cleanup: $displayName", error)
    }
    if (!supportsDelete) {
      removeRecoveryCopyOrThrow(copy)
      throw IOException("SAF provider cannot clean up recovery copy: $displayName")
    }

    return try {
      overwrite(copy.uri, original)
      RecoveryCopyPreparation.Available(copy)
    } catch (error: Exception) {
      removeRecoveryCopyOrThrow(copy, error)
      throw IOException("Unable to write recovery copy: $displayName", error)
    }
  }

  private fun supportsFlag(document: Uri, flag: Int): Boolean {
    val cursor = resolver.query(
      document,
      arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
      null,
      null,
      null,
    ) ?: throw IOException("Unable to read SAF document capabilities")
    return cursor.use {
      if (!it.moveToFirst()) throw IOException("SAF document capability query returned no row")
      val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
      if (index < 0) throw IOException("SAF document capability flags are unavailable")
      (it.getInt(index) and flag) != 0
    }
  }

  private fun removeRecoveryCopyOrThrow(copy: RecoveryCopy, cause: Throwable? = null) {
    val removed = runCatching { DocumentsContract.deleteDocument(resolver, copy.uri) }.getOrDefault(false)
    if (!removed) throw IOException("Unable to remove recovery copy: ${copy.displayName}", cause)
  }

  private data class RecoveryCopy(
    val uri: Uri,
    val displayName: String,
  )
}
