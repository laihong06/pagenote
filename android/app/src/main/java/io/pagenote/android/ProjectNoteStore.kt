package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Coordinates this app process only; SAF cannot enlist writers in other apps. */
internal object ProjectNoteMutationLocks {
  private val locks = ConcurrentHashMap<String, Any>()

  fun <T> withLock(uri: Uri, action: () -> T): T =
    synchronized(locks.computeIfAbsent(uri.toString()) { Any() }) { action() }
}

class ProjectNoteStore(
  private val resolver: ContentResolver,
  private val uri: Uri,
) {
  fun read(): String = withRevisionLock {
    resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
      it.readText()
    } ?: throw IOException("无法读取项目笔记")
  }

  internal fun <T> withRevisionLock(action: () -> T): T =
    ProjectNoteMutationLocks.withLock(uri, action)

  fun supportsWrite(): Boolean {
    val cursor = resolver.query(
      uri,
      arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
      null,
      null,
      null,
    ) ?: return false
    return cursor.use {
      if (!it.moveToFirst()) return@use false
      val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
      index >= 0 &&
        (it.getInt(index) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
    }
  }

  fun write(markdown: String, expectedOriginal: String): String? {
    return withRevisionLock {
      RecoverableDocumentWrite(
        readTarget = ::read,
        overwriteTarget = { content -> overwrite(uri, content) },
        createBackup = ::createRecoveryCopy,
        deleteBackup = { copy ->
          if (!DocumentsContract.deleteDocument(resolver, copy.uri)) {
            throw IOException("无法清理笔记恢复副本")
          }
        },
        backupName = RecoveryCopy::displayName,
      ).write(updated = markdown, expectedOriginal = expectedOriginal)
    }
  }

  private fun overwrite(target: Uri, markdown: String) {
    val output = resolver.openOutputStream(target, "wt")
      ?: throw IOException("无法写入项目笔记")
    output.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
  }

  private fun createRecoveryCopy(markdown: String): RecoveryCopyPreparation<RecoveryCopy> {
    val displayName = "pagenote-recovery-${UUID.randomUUID()}.md"
    val preparation = RecoveryCopyPreparation.fromProvider(
      findParent = ::findParentDocument,
      supportsCreate = { parent ->
        supportsFlag(parent, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
      },
      create = { parent ->
        DocumentsContract.createDocument(resolver, parent, "text/markdown", displayName)
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
      removeRecoveryCopyOrThrow(copy.uri, displayName, error)
      throw IOException("Unable to verify recovery-copy cleanup: $displayName", error)
    }
    if (!supportsDelete) {
      removeRecoveryCopyOrThrow(copy.uri, displayName)
      throw IOException("SAF provider cannot clean up a recovery copy: $displayName")
    }

    return try {
      overwrite(copy.uri, markdown)
      RecoveryCopyPreparation.Available(copy)
    } catch (error: Exception) {
      removeRecoveryCopyOrThrow(copy.uri, displayName, error)
      throw IOException("Unable to write recovery copy: $displayName", error)
    }
  }

  private fun findParentDocument(): Uri? {
    val path = DocumentsContract.findDocumentPath(resolver, uri)?.path
      ?: throw IOException("Unable to determine project note parent")
    if (path.size < 2) return null
    return DocumentsContract.buildDocumentUriUsingTree(uri, path[path.lastIndex - 1])
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

  private fun removeRecoveryCopyOrThrow(
    copyUri: Uri,
    displayName: String,
    cause: Throwable? = null,
  ) {
    val removed = runCatching { DocumentsContract.deleteDocument(resolver, copyUri) }
      .getOrDefault(false)
    if (!removed) throw IOException("无法清理未完成的恢复副本：$displayName", cause)
  }

  private data class RecoveryCopy(
    val uri: Uri,
    val displayName: String,
  )
}
