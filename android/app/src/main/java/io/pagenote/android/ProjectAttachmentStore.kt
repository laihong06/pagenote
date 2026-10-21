package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal data class ProjectAttachmentEntry(
  val assetName: String,
  val uri: Uri,
  val mimeType: String?,
  val sizeBytes: Long?,
) {
  val isImage: Boolean
    get() = ProjectAttachmentPolicy.extensionForDisplayName(assetName) in IMAGE_EXTENSIONS

  val canInsertImage: Boolean
    get() = isImage && ProjectAttachmentPolicy.isSafeAssetName(assetName)

  private companion object {
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")
  }
}

internal data class ImportedProjectAttachment(
  val assetName: String,
  val uri: Uri,
  val isImage: Boolean,
)

internal enum class ProjectAttachmentDeleteResult {
  DELETED,
  NOT_FOUND,
  REFERENCED,
  UNKNOWN_REFERENCES,
  UNSAFE_NAME,
  PROVIDER_REJECTED,
}

internal class ProjectAttachmentImportException(
  val residualAssetName: String? = null,
  cause: Throwable? = null,
) : IOException("Unable to import project attachment", cause)

internal class ProjectAttachmentStore(
  private val resolver: ContentResolver,
  private val cacheDirectory: java.io.File,
  private val sourceDirectoryUri: Uri,
) {
  fun list(): List<ProjectAttachmentEntry> {
    val assetsUri = findChild(sourceDirectoryUri, ASSETS_DIRECTORY) ?: return emptyList()
    if (queryMimeType(assetsUri) != DocumentsContract.Document.MIME_TYPE_DIR) {
      throw IOException("The project assets path is not a directory")
    }
    val parentId = DocumentsContract.getDocumentId(assetsUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(assetsUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
      ),
      null,
      null,
      null,
    ) ?: throw IOException("The project assets could not be listed")
    return cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
      buildList {
        while (it.moveToNext()) {
          val mimeType = it.getString(mimeColumn)
          val name = it.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
          if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue
          val documentUri = DocumentsContract.buildDocumentUriUsingTree(assetsUri, it.getString(idColumn))
          add(
            ProjectAttachmentEntry(
              assetName = name,
              uri = documentUri,
              mimeType = mimeType,
              sizeBytes = sizeColumn.takeIf { column -> column >= 0 }
                ?.let(it::getLong)
                ?.takeIf { size -> size >= 0 },
            ),
          )
        }
      }.sortedBy { entry -> entry.assetName.lowercase(Locale.ROOT) }
    }
  }

  fun import(sourceUri: Uri, fallbackDisplayName: String? = null): ImportedProjectAttachment {
    val displayName = queryDisplayName(sourceUri) ?: fallbackDisplayName?.takeIf(String::isNotBlank)
      ?: throw ProjectAttachmentImportException()
    val extension = ProjectAttachmentPolicy.extensionForDisplayName(displayName)
      ?: throw ProjectAttachmentImportException()
    if (extension in IMAGE_EXTENSIONS) {
      return try {
        val image = ProjectImageImporter(resolver, cacheDirectory, sourceDirectoryUri).import(
          sourceUri,
          fallbackDisplayName = displayName,
        )
        ImportedProjectAttachment(image.assetName, image.documentUri, isImage = true)
      } catch (error: ProjectImageImportException) {
        throw ProjectAttachmentImportException(error.residualAssetName, error)
      } catch (error: Exception) {
        throw ProjectAttachmentImportException(cause = error)
      }
    }

    return importOpaqueAttachment(sourceUri, extension)
  }

  fun deleteIfUnreferenced(
    assetName: String,
    allPageNotes: String,
    annotationsJson: String?,
  ): ProjectAttachmentDeleteResult {
    when (ProjectAttachmentPolicy.deletionEligibility(assetName, allPageNotes, annotationsJson)) {
      ProjectAttachmentPolicy.DeleteEligibility.REFERENCED -> return ProjectAttachmentDeleteResult.REFERENCED
      ProjectAttachmentPolicy.DeleteEligibility.UNKNOWN_REFERENCES ->
        return ProjectAttachmentDeleteResult.UNKNOWN_REFERENCES
      ProjectAttachmentPolicy.DeleteEligibility.UNSAFE_NAME -> return ProjectAttachmentDeleteResult.UNSAFE_NAME
      ProjectAttachmentPolicy.DeleteEligibility.ALLOWED -> Unit
    }
    val assetsUri = findChild(sourceDirectoryUri, ASSETS_DIRECTORY)
      ?: return ProjectAttachmentDeleteResult.NOT_FOUND
    val assetUri = findChild(assetsUri, assetName) ?: return ProjectAttachmentDeleteResult.NOT_FOUND
    return if (DocumentsContract.deleteDocument(resolver, assetUri)) {
      ProjectAttachmentDeleteResult.DELETED
    } else {
      ProjectAttachmentDeleteResult.PROVIDER_REJECTED
    }
  }

  fun readAnnotations(): String? {
    val uri = findChild(sourceDirectoryUri, ANNOTATIONS_FILE) ?: return null
    return resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
      val text = StringBuilder()
      val buffer = CharArray(BUFFER_SIZE)
      while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        if (text.length + count > MAX_ANNOTATION_JSON_CHARS) {
          throw IOException("Project annotations are too large to verify attachment references")
        }
        text.append(buffer, 0, count)
      }
      text.toString()
    } ?: throw IOException("Project annotations could not be read")
  }

  private fun importOpaqueAttachment(sourceUri: Uri, extension: String): ImportedProjectAttachment {
    val declaredSize = querySize(sourceUri)
    if (declaredSize != null && declaredSize > MAX_ATTACHMENT_BYTES) {
      throw ProjectAttachmentImportException()
    }
    var assetsUri: Uri? = null
    var createdAssetsUri: Uri? = null
    var targetUri: Uri? = null
    var assetName: String? = null
    try {
      val assetsResult = findOrCreateAssetsDirectory()
      assetsUri = assetsResult.first
      if (assetsResult.second) createdAssetsUri = assetsUri
      repeat(MAX_NAME_ATTEMPTS) {
        val candidate = "${UUID.randomUUID()}.$extension"
        if (findChild(assetsUri!!, candidate) != null) return@repeat
        assetName = candidate
        targetUri = DocumentsContract.createDocument(
          resolver,
          assetsUri!!,
          MIME_BY_EXTENSION.getValue(extension),
          candidate,
        ) ?: throw IOException("The project did not create the attachment")
        if (queryDisplayName(targetUri!!) != candidate) {
          throw IOException("The project changed the attachment name")
        }
        copySource(sourceUri, targetUri!!)
        return ImportedProjectAttachment(candidate, targetUri!!, isImage = false)
      }
      throw IOException("Could not reserve an attachment name")
    } catch (error: Exception) {
      val target = targetUri
      val targetRemoved = target == null || runCatching { DocumentsContract.deleteDocument(resolver, target) }
        .getOrDefault(false)
      if (targetRemoved) {
        createdAssetsUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
      }
      throw ProjectAttachmentImportException(
        residualAssetName = assetName.takeUnless { targetRemoved },
        cause = error,
      )
    }
  }

  private fun copySource(sourceUri: Uri, targetUri: Uri) {
    val source = resolver.openInputStream(sourceUri) ?: throw IOException("The attachment could not be read")
    val target = resolver.openOutputStream(targetUri, "w") ?: throw IOException("The attachment could not be written")
    source.use { input ->
      target.buffered().use { output ->
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          total += count
          if (total > MAX_ATTACHMENT_BYTES) throw IOException("The attachment is too large")
          output.write(buffer, 0, count)
        }
        if (total == 0L) throw IOException("The attachment is empty")
      }
    }
  }

  private fun findOrCreateAssetsDirectory(): Pair<Uri, Boolean> {
    val existing = findChild(sourceDirectoryUri, ASSETS_DIRECTORY)
    if (existing != null) {
      if (queryMimeType(existing) != DocumentsContract.Document.MIME_TYPE_DIR) {
        throw IOException("The project assets path is not a directory")
      }
      return existing to false
    }
    val created = DocumentsContract.createDocument(
      resolver,
      sourceDirectoryUri,
      DocumentsContract.Document.MIME_TYPE_DIR,
      ASSETS_DIRECTORY,
    ) ?: throw IOException("The project assets directory could not be created")
    return created to true
  }

  private fun findChild(parentUri: Uri, displayName: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    ) ?: throw IOException("The project directory could not be listed")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == displayName) {
          return DocumentsContract.buildDocumentUriUsingTree(parentUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun queryDisplayName(uri: Uri): String? = resolver.query(
    uri,
    arrayOf(OpenableColumns.DISPLAY_NAME),
    null,
    null,
    null,
  )?.use {
    val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (column >= 0 && it.moveToFirst()) it.getString(column)?.takeIf(String::isNotBlank) else null
  }

  private fun querySize(uri: Uri): Long? = resolver.query(
    uri,
    arrayOf(OpenableColumns.SIZE),
    null,
    null,
    null,
  )?.use {
    val column = it.getColumnIndex(OpenableColumns.SIZE)
    if (column >= 0 && it.moveToFirst()) it.getLong(column).takeIf { size -> size >= 0L } else null
  }

  private fun queryMimeType(uri: Uri): String? = resolver.query(
    uri,
    arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
    null,
    null,
    null,
  )?.use {
    val column = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
    if (column >= 0 && it.moveToFirst()) it.getString(column) else null
  }

  private companion object {
    const val ASSETS_DIRECTORY = "assets"
    const val ANNOTATIONS_FILE = "annotations.json"
    const val BUFFER_SIZE = 16 * 1024
    const val MAX_ANNOTATION_JSON_CHARS = 8 * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 200L * 1024L * 1024L
    const val MAX_NAME_ATTEMPTS = 3
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")
    val MIME_BY_EXTENSION = mapOf(
      "pdf" to "application/pdf",
      "mp3" to "audio/mpeg",
      "mp4" to "video/mp4",
      "md" to "text/markdown",
      "markdown" to "text/markdown",
      "txt" to "text/plain",
      "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
  }
}
