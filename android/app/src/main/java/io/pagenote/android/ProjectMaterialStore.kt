package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.feature.annotations.CanvasMaterialCodec
import io.pagenote.feature.annotations.CanvasMaterialEntry
import java.io.IOException

/**
 * 素材库数据（移植批次 5，SAF 侧）：`<project>/materials.json` 的读写适配。
 * 编解码与校验在 feature-annotations 的 [CanvasMaterialCodec]；本类只做 SAF
 * 文档 IO，写入姿态对齐 ProjectPenStore（已存在文件走 SafRecoverableDocumentWriter
 * 恢复写入，新建文件失败回滚删除）。
 */
internal class ProjectMaterialStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
) {
  fun read(): List<CanvasMaterialEntry> {
    val uri = findMaterialsDocument() ?: return emptyList()
    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("The project materials could not be read")
    return CanvasMaterialCodec.decode(text)
  }

  /** 返回恢复副本名（新建文件或无副本时为 null）。 */
  fun write(entries: List<CanvasMaterialEntry>): String? {
    val encoded = CanvasMaterialCodec.encode(entries)
    val existing = findMaterialsDocument()
    if (existing == null) {
      var created: Uri? = null
      try {
        created = DocumentsContract.createDocument(
          resolver,
          sourceDirectoryUri,
          "application/json",
          CanvasMaterialCodec.FILE_NAME,
        ) ?: throw IOException("The project did not create ${CanvasMaterialCodec.FILE_NAME}")
        if (queryDisplayName(created) != CanvasMaterialCodec.FILE_NAME) {
          throw IOException("The project changed the materials file name")
        }
        resolver.openOutputStream(created, "w")?.buffered()?.use { output ->
          output.write(encoded.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("The materials file could not be written")
        return null
      } catch (error: Exception) {
        created?.let { uri -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
        throw error
      }
    }
    val original = resolver.openInputStream(existing)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("The project materials could not be read")
    return SafRecoverableDocumentWriter(resolver, existing, CanvasMaterialCodec.FILE_NAME)
      .write(encoded, original)
  }

  fun exists(): Boolean = findMaterialsDocument() != null

  private fun findMaterialsDocument(): Uri? {
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
    ) ?: throw IOException("The project directory could not be listed")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == CanvasMaterialCodec.FILE_NAME) {
          return DocumentsContract.buildDocumentUriUsingTree(sourceDirectoryUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun queryDisplayName(uri: Uri): String? = resolver.query(
    uri,
    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
    null,
    null,
    null,
  )?.use {
    val column = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
    if (column >= 0 && it.moveToFirst()) it.getString(column)?.takeIf(String::isNotBlank) else null
  }
}
