package io.pagenote.android

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal data class ImportedProjectImage(
  val assetName: String,
  val altText: String,
  val documentUri: Uri,
  val pixelWidth: Int,
  val pixelHeight: Int,
)

internal class ProjectImageImportException(
  val residualAssetName: String? = null,
  cause: Throwable? = null,
) : IOException("Unable to import image into the project", cause)

internal class ProjectImageImporter(
  private val resolver: ContentResolver,
  private val cacheDirectory: File,
  private val sourceDirectoryUri: Uri,
) {
  fun import(
    sourceUri: Uri,
    fallbackDisplayName: String? = null,
    altTextOverride: String? = null,
  ): ImportedProjectImage {
    val temporaryFile = File.createTempFile("pagenote-image-", ".tmp", cacheDirectory)
    var assetUri: Uri? = null
    var assetName: String? = null
    var createdAssetsUri: Uri? = null
    try {
      val imageInfo = resolveImageInfo(sourceUri, fallbackDisplayName, altTextOverride)
      copySourceToTemporaryFile(sourceUri, temporaryFile)
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(temporaryFile.absolutePath, bounds)
      val pixelCount = bounds.outWidth.toLong() * bounds.outHeight.toLong()
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixelCount > MAX_IMAGE_PIXELS) {
        throw IOException("The selected image dimensions are not supported")
      }
      val bitmap = BitmapFactory.decodeFile(temporaryFile.absolutePath)
        ?: throw IOException("Unsupported or invalid image")
      bitmap.recycle()

      val assets = findOrCreateAssetsDirectory().also {
        if (it.second) createdAssetsUri = it.first
      }.first
      val mimeType = MIME_BY_EXTENSION.getValue(imageInfo.extension)
      repeat(MAX_NAME_ATTEMPTS) {
        val candidate = "${UUID.randomUUID()}.${imageInfo.extension}"
        if (findChild(assets, candidate) == null) {
          assetName = candidate
          assetUri = DocumentsContract.createDocument(resolver, assets, mimeType, candidate)
            ?: throw IOException("The project did not create the image asset")
          val createdName = queryDisplayName(assetUri!!)
          if (createdName != candidate) throw IOException("The project changed the image asset name")
          temporaryFile.inputStream().buffered().use { input ->
            val output = resolver.openOutputStream(assetUri!!, "w")
              ?: throw IOException("The project image could not be opened for writing")
            output.buffered().use { input.copyTo(it, BUFFER_SIZE) }
          }
          return ImportedProjectImage(
            assetName = candidate,
            altText = imageInfo.altText,
            documentUri = assetUri!!,
            pixelWidth = bounds.outWidth,
            pixelHeight = bounds.outHeight,
          )
        }
      }
      throw IOException("Could not reserve a project image name")
    } catch (error: Exception) {
      val imageUri = assetUri
      val cleanupFailed = imageUri != null && !runCatching {
        DocumentsContract.deleteDocument(resolver, imageUri)
      }.getOrDefault(false)
      createdAssetsUri?.let { directory ->
        runCatching { DocumentsContract.deleteDocument(resolver, directory) }
      }
      throw ProjectImageImportException(
        residualAssetName = assetName.takeIf { cleanupFailed },
        cause = error,
      )
    } finally {
      temporaryFile.delete()
    }
  }

  fun deleteImported(documentUri: Uri): Boolean =
    runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }.getOrDefault(false)

  private fun resolveImageInfo(
    sourceUri: Uri,
    fallbackDisplayName: String?,
    altTextOverride: String?,
  ): SourceImageInfo {
    val mimeType = resolver.getType(sourceUri)
      ?.substringBefore(';')
      ?.trim()
      ?.lowercase()
      ?: throw IOException("The selected image has no supported media type")
    val mimeExtension = extensionForMimeType(mimeType)
      ?: throw IOException("The selected image type is not supported")
    val displayName = queryDisplayNameOrNull(sourceUri)
      ?: fallbackDisplayName?.takeIf(String::isNotBlank)
      ?: throw IOException("The selected image name is unavailable")
    val declaredExtension = displayName.substringAfterLast('.', "").lowercase()
    val extension = if (declaredExtension.isEmpty()) {
      mimeExtension
    } else {
      normalizeExtension(declaredExtension)
        ?: throw IOException("The selected image extension is not supported")
    }
    if (!sameImageType(extension, mimeExtension)) {
      throw IOException("The selected image type and extension do not match")
    }
    val altText = truncateCodePoints(
      altTextOverride?.takeIf(String::isNotBlank)
        ?: displayName.substringBeforeLast('.', displayName),
      MAX_ALT_TEXT_LENGTH,
    ).ifBlank { "image" }
    return SourceImageInfo(extension, altText)
  }

  private fun truncateCodePoints(value: String, maxCodePoints: Int): String {
    val end = value.offsetByCodePoints(0, minOf(value.codePointCount(0, value.length), maxCodePoints))
    return value.substring(0, end)
  }

  private fun copySourceToTemporaryFile(sourceUri: Uri, destination: File) {
    val declaredSize = querySize(sourceUri)
    if (declaredSize != null && declaredSize > MAX_IMAGE_BYTES) {
      throw IOException("The selected image is too large")
    }
    val input = resolver.openInputStream(sourceUri) ?: throw IOException("The selected image could not be read")
    input.use { source ->
      FileOutputStream(destination, false).use { output ->
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
          val count = source.read(buffer)
          if (count < 0) break
          total += count
          if (total > MAX_IMAGE_BYTES) throw IOException("The selected image is too large")
          output.write(buffer, 0, count)
        }
        if (total == 0L) throw IOException("The selected image is empty")
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

  private fun queryDisplayName(uri: Uri): String = queryDisplayNameOrNull(uri)
    ?: throw IOException("The selected image name is unavailable")

  private fun queryDisplayNameOrNull(uri: Uri): String? {
    val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?: return null
    return cursor.use {
      val nameColumn = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (nameColumn >= 0 && it.moveToFirst()) {
        it.getString(nameColumn)?.takeIf(String::isNotBlank)
      } else {
        null
      }
    }
  }

  private fun querySize(uri: Uri): Long? = resolver.query(
    uri,
    arrayOf(OpenableColumns.SIZE),
    null,
    null,
    null,
  )?.use {
    if (!it.moveToFirst()) {
      null
    } else {
      val sizeColumn = it.getColumnIndex(OpenableColumns.SIZE)
      if (sizeColumn < 0) null else it.getLong(sizeColumn).takeIf { size -> size >= 0L }
    }
  }

  private fun queryMimeType(uri: Uri): String? {
    val cursor = resolver.query(
      uri,
      arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
      null,
      null,
      null,
    ) ?: return null
    cursor.use {
      if (!it.moveToFirst()) return null
      return it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
    }
  }

  private fun normalizeExtension(extension: String): String? = when (extension) {
    "png", "jpg", "jpeg", "gif", "webp" -> extension
    else -> null
  }

  private fun sameImageType(first: String, second: String): Boolean =
    first == second || first in JPEG_EXTENSIONS && second in JPEG_EXTENSIONS

  private data class SourceImageInfo(val extension: String, val altText: String)

  internal companion object {
    fun extensionForMimeType(mimeType: String): String? =
      EXTENSION_BY_MIME[mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)]

    const val ASSETS_DIRECTORY = "assets"
    const val BUFFER_SIZE = 8 * 1024
    const val MAX_ALT_TEXT_LENGTH = 160
    const val MAX_NAME_ATTEMPTS = 3
    const val MAX_IMAGE_BYTES = 200L * 1024L * 1024L
    const val MAX_IMAGE_PIXELS = 24_000_000L
    val JPEG_EXTENSIONS = setOf("jpg", "jpeg")
    val EXTENSION_BY_MIME = mapOf(
      "image/png" to "png",
      "image/jpeg" to "jpg",
      "image/jpg" to "jpg",
      "image/gif" to "gif",
      "image/webp" to "webp",
    )
    val MIME_BY_EXTENSION = mapOf(
      "png" to "image/png",
      "jpg" to "image/jpeg",
      "jpeg" to "image/jpeg",
      "gif" to "image/gif",
      "webp" to "image/webp",
    )
  }
}
