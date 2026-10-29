package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

internal data class ImportedStickerEntry(
  /** 相对贴纸库根的相对路径（`pack/cat.png`）。 */
  val relativePath: String,
  val documentUri: Uri,
  val sizeBytes: Long,
)

internal data class ImportedStickerPack(
  /** 贴纸库内包目录名（唯一化后的显示名）。 */
  val packName: String,
  val directoryUri: Uri,
  val entries: List<ImportedStickerEntry>,
)

internal class ProjectStickerImportException(
  val residualPaths: List<String> = emptyList(),
  cause: Throwable? = null,
) : IOException("Unable to import sticker pack", cause)

/**
 * 贴纸资产包 SAF 导入（移植批次 5）。对齐 touchnotes `draw.sticker.StickerManager`
 * 的导入管线（zip → 校验 → `stickers/` 目录入库 + 唯一名消解），落到 pagenote 的
 * SAF 项目目录：`<project>/stickers/<pack>/<group?>/<file>`。
 *
 * 失败语义对齐 [ProjectImageImporter]：任何一步失败即清理本次已创建的文档与
 * 目录，残留无法删除的路径经 [ProjectStickerImportException.residualPaths]
 * 上报；成功返回包目录名与全部条目 URI。
 */
internal class ProjectStickerPackImporter(
  private val resolver: ContentResolver,
  private val cacheDirectory: File,
  private val sourceDirectoryUri: Uri,
) {
  fun importPack(sourceUri: Uri, fallbackDisplayName: String? = null): ImportedStickerPack {
    val temporaryFile = File.createTempFile("pagenote-sticker-", ".tmp", cacheDirectory)
    val createdDocuments = mutableListOf<Uri>()
    val createdDirectories = mutableListOf<Uri>()
    try {
      copySourceToTemporaryFile(sourceUri, temporaryFile)
      // 第一遍：整体校验（全有或全无，任何条目非法都不建目录）。
      val entries = StickerPackArchive.validate(temporaryFile.inputStream().buffered())
      val stickersRoot = findOrCreateDirectory(sourceDirectoryUri, STICKERS_DIRECTORY)
        .also { if (it.second) createdDirectories.add(it.first) }.first
      val displayName = queryDisplayName(sourceUri)
        ?: fallbackDisplayName?.takeIf(String::isNotBlank)
        ?: "stickers-${UUID.randomUUID().toString().substring(0, 8)}"
      val packName = resolveUniqueDirectoryName(stickersRoot, StickerPackNames.sanitizePackName(displayName))
      val packDirectory = DocumentsContract.createDocument(
        resolver,
        stickersRoot,
        DocumentsContract.Document.MIME_TYPE_DIR,
        packName,
      ) ?: throw IOException("The project did not create the sticker pack directory")
      createdDirectories.add(packDirectory)
      if (queryDisplayName(packDirectory) != packName) {
        throw IOException("The project changed the sticker pack directory name")
      }

      // 第二遍：按 zip 内顺序把每个已校验条目写成 SAF 文档。
      val imported = mutableListOf<ImportedStickerEntry>()
      val groupDirectories = mutableMapOf<String, Uri>()
      val validatedByName = entries.associateBy { it.relativePath }
      java.util.zip.ZipInputStream(temporaryFile.inputStream().buffered()).use { zip ->
        while (true) {
          val zipEntry = zip.nextEntry ?: break
          try {
            if (zipEntry.isDirectory) continue
            val descriptor = validatedByName[zipEntry.name]
              ?: // 第一遍校验过的 zip 在第二遍出现新条目名，视为包内容变化，整体拒绝。
              throw IOException("The sticker pack changed between validation and import")
            val parent = if (descriptor.group.isBlank()) {
              packDirectory
            } else {
              groupDirectories.getOrPut(descriptor.group) {
                findOrCreateDirectory(packDirectory, StickerPackNames.sanitizeSegment(descriptor.group))
                  .also { if (it.second) createdDirectories.add(it.first) }.first
              }
            }
            val extension = descriptor.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = StickerPackArchive.mimeTypeFor(extension)
              ?: throw IOException("Unsupported sticker extension: ${descriptor.fileName}")
            val documentUri = DocumentsContract.createDocument(resolver, parent, mimeType, descriptor.fileName)
              ?: throw IOException("The project did not create the sticker file")
            createdDocuments.add(documentUri)
            if (queryDisplayName(documentUri) != descriptor.fileName) {
              throw IOException("The project changed the sticker file name")
            }
            var written = 0L
            val output = resolver.openOutputStream(documentUri, "w")
              ?: throw IOException("The sticker file could not be opened for writing")
            output.buffered().use { target ->
              val buffer = ByteArray(ProjectImageImporter.BUFFER_SIZE)
              while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                written += count
                if (written > StickerPackArchive.MAX_ENTRY_BYTES) {
                  throw IOException("Sticker pack entry is too large: ${descriptor.fileName}")
                }
                target.write(buffer, 0, count)
              }
            }
            imported.add(
              ImportedStickerEntry(
                relativePath = "$packName/${descriptor.relativePath}",
                documentUri = documentUri,
                sizeBytes = written,
              ),
            )
          } finally {
            zip.closeEntry()
          }
        }
      }
      if (imported.size != entries.size) {
        throw IOException("The sticker pack changed between validation and import")
      }
      return ImportedStickerPack(packName = packName, directoryUri = packDirectory, entries = imported)
    } catch (error: Exception) {
      val residuals = mutableListOf<String>()
      createdDocuments.forEach { uri ->
        if (!runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)) {
          residuals.add(uri.toString())
        }
      }
      createdDirectories.asReversed().forEach { uri ->
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
      }
      throw ProjectStickerImportException(residualPaths = residuals, cause = error)
    } finally {
      temporaryFile.delete()
    }
  }

  /**
   * 单张图片直入贴纸库根（对齐 touchnotes 的单图导入路径）：显示名安全化 +
   * 唯一化后创建文档。图片校验复用 [ProjectImageImporter] 的尺寸/解码规则。
   */
  fun importSingleImage(sourceUri: Uri, fallbackDisplayName: String? = null): ImportedStickerEntry {
    val temporaryFile = File.createTempFile("pagenote-sticker-", ".tmp", cacheDirectory)
    var documentUri: Uri? = null
    var createdRoot: Uri? = null
    try {
      val info = resolveImageInfo(sourceUri, fallbackDisplayName)
      copySourceToTemporaryFile(sourceUri, temporaryFile)
      val image = ProjectImageImporter(resolver, cacheDirectory, sourceDirectoryUri)
      val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
      android.graphics.BitmapFactory.decodeFile(temporaryFile.absolutePath, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IOException("Unsupported or invalid sticker image")
      }

      val stickersRoot = findOrCreateDirectory(sourceDirectoryUri, STICKERS_DIRECTORY)
        .also { if (it.second) createdRoot = it.first }.first
      val fileName = resolveUniqueFileName(stickersRoot, info.fileName)
      val mimeType = ProjectImageImporter.MIME_BY_EXTENSION.getValue(info.extension)
      val created = DocumentsContract.createDocument(resolver, stickersRoot, mimeType, fileName)
        ?: throw IOException("The project did not create the sticker file")
      documentUri = created
      if (queryDisplayName(created) != fileName) {
        throw IOException("The project changed the sticker file name")
      }
      temporaryFile.inputStream().buffered().use { input ->
        val output = resolver.openOutputStream(created, "w")
          ?: throw IOException("The sticker file could not be opened for writing")
        output.buffered().use { input.copyTo(it, ProjectImageImporter.BUFFER_SIZE) }
      }
      return ImportedStickerEntry(
        relativePath = fileName,
        documentUri = created,
        sizeBytes = temporaryFile.length(),
      )
    } catch (error: Exception) {
      documentUri?.let { uri -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
      createdRoot?.let { uri -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
      throw ProjectStickerImportException(cause = error)
    } finally {
      temporaryFile.delete()
    }
  }

  private fun resolveImageInfo(sourceUri: Uri, fallbackDisplayName: String?): ImageInfo {
    val mimeType = resolver.getType(sourceUri)
      ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
      ?: throw IOException("The selected sticker has no supported media type")
    val extension = ProjectImageImporter.extensionForMimeType(mimeType)
      ?: throw IOException("The selected sticker type is not supported")
    val displayName = queryDisplayName(sourceUri)
      ?: fallbackDisplayName?.takeIf(String::isNotBlank)
      ?: throw IOException("The selected sticker name is unavailable")
    val declared = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val verifiedExtension = if (declared.isEmpty()) {
      extension
    } else if (declared == extension ||
      declared in ProjectImageImporter.JPEG_EXTENSIONS && extension in ProjectImageImporter.JPEG_EXTENSIONS
    ) {
      declared
    } else {
      throw IOException("The selected sticker type and extension do not match")
    }
    val safeName = StickerPackNames.sanitizeFileName(displayName.substringBeforeLast('.'), verifiedExtension)
    return ImageInfo(fileName = safeName, extension = verifiedExtension)
  }

  private fun copySourceToTemporaryFile(sourceUri: Uri, destination: File) {
    val input = resolver.openInputStream(sourceUri) ?: throw IOException("The sticker pack could not be read")
    input.use { source ->
      java.io.FileOutputStream(destination, false).buffered().use { output ->
        val buffer = ByteArray(ProjectImageImporter.BUFFER_SIZE)
        var total = 0L
        while (true) {
          val count = source.read(buffer)
          if (count < 0) break
          total += count
          if (total > MAX_PACK_BYTES) throw IOException("The sticker pack is too large")
          output.write(buffer, 0, count)
        }
        if (total == 0L) throw IOException("The sticker pack is empty")
      }
    }
  }

  private fun findOrCreateDirectory(parentUri: Uri, displayName: String): Pair<Uri, Boolean> {
    val existing = findChild(parentUri, displayName)
    if (existing != null) {
      if (queryMimeType(existing) != DocumentsContract.Document.MIME_TYPE_DIR) {
        throw IOException("The stickers path is not a directory: $displayName")
      }
      return existing to false
    }
    val created = DocumentsContract.createDocument(
      resolver,
      parentUri,
      DocumentsContract.Document.MIME_TYPE_DIR,
      displayName,
    ) ?: throw IOException("The stickers directory could not be created: $displayName")
    return created to true
  }

  private fun resolveUniqueDirectoryName(parentUri: Uri, baseName: String): String {
    var candidate = baseName
    var index = 1
    while (findChild(parentUri, candidate) != null) {
      candidate = "${baseName}_$index"
      index += 1
      if (index > MAX_NAME_ATTEMPTS) throw IOException("Could not reserve a sticker directory name")
    }
    return candidate
  }

  private fun resolveUniqueFileName(parentUri: Uri, fileName: String): String {
    val base = fileName.substringBeforeLast('.', fileName)
    val extension = fileName.substringAfterLast('.', "")
    var candidate = fileName
    var index = 1
    while (findChild(parentUri, candidate) != null) {
      candidate = if (extension.isEmpty()) "${base}_$index" else "${base}_$index.$extension"
      index += 1
      if (index > MAX_NAME_ATTEMPTS) throw IOException("Could not reserve a sticker file name")
    }
    return candidate
  }

  private fun findChild(parentUri: Uri, displayName: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      ),
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

  private fun queryMimeType(uri: Uri): String? = resolver.query(
    uri,
    arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
    null,
    null,
    null,
  )?.use {
    val column = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
    if (it.moveToFirst()) it.getString(column) else null
  }

  private data class ImageInfo(val fileName: String, val extension: String)

  private companion object {
    const val STICKERS_DIRECTORY = "stickers"
    const val MAX_NAME_ATTEMPTS = 64
    const val MAX_PACK_BYTES = 256L * 1024L * 1024L
  }
}

/** 贴纸库命名安全化（写侧严格：只产出 `[A-Za-z0-9._ -]` 且不以点开头的段）。 */
internal object StickerPackNames {
  private const val MAX_SEGMENT_LENGTH = 64

  fun sanitizePackName(displayName: String): String {
    val base = sanitizeSegment(displayName.substringBeforeLast('.'))
    return base.ifBlank { "stickers-${UUID.randomUUID().toString().substring(0, 8)}" }
  }

  fun sanitizeSegment(raw: String): String {
    val builder = StringBuilder(raw.length)
    raw.codePoints().forEach { code ->
      builder.appendCodePoint(
        if (Character.isLetterOrDigit(code) || code == '.'.code || code == '-'.code || code == '_'.code || code == ' '.code) {
          code
        } else {
          '_'.code
        },
      )
    }
    return builder.toString().trim().trim('.').trim().take(MAX_SEGMENT_LENGTH)
  }

  fun sanitizeFileName(baseName: String, extension: String): String {
    val safe = sanitizeSegment(baseName).ifBlank { "sticker" }
    return "$safe.$extension"
  }
}
