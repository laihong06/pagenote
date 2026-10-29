package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

internal enum class StickerLibraryEntryKind {
  /** 静态图片贴纸（png/jpg/jpeg/webp）。 */
  IMAGE,

  /** 动效贴纸（gif，GIF 帧元数据见 GifMetadataParser）。 */
  GIF,

  /** 分组/包目录。 */
  FOLDER,
}

internal data class StickerLibraryEntry(
  /** 直接子项显示名（不含路径）。 */
  val displayName: String,
  val kind: StickerLibraryEntryKind,
  val sizeBytes: Long?,
  val documentUri: Uri,
)

internal data class StickerLibraryPage(
  /** 相对贴纸库根的文件夹路径（根 = ""）。 */
  val folderPath: String,
  /** 排序后的直接子项（StickerPanelOrder 合成顺序）。 */
  val entries: List<StickerLibraryEntry>,
)

/**
 * 贴纸面板数据（移植批次 5，SAF 侧）。对齐 touchnotes `StickerDialog` 的面板
 * 数据流：`stickers/` 目录树 + 当前文件夹直接子项 + 拖拽排序持久化
 * （`stickers/order.json`，见 [StickerPanelOrder]）。纯逻辑（排序编解码/合成/
 * 移动语义）在 [StickerPanelOrder]；本类只做 SAF 列举与 order.json 读写。
 */
internal class ProjectStickerLibrary(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
) {
  /** 列出 [folderPath]（"/" 分隔、相对根）的直接子项，按面板顺序排列。 */
  fun list(folderPath: String = ""): StickerLibraryPage {
    val root = stickersRoot() ?: return StickerLibraryPage(folderPath, emptyList())
    val folderUri = if (folderPath.isBlank()) root else descend(root, folderPath)
      ?: return StickerLibraryPage(folderPath, emptyList())
    val rawEntries = listChildren(folderUri)
    val order = readOrder()[folderPath]
    val sorted = StickerPanelOrder.orderedNames(
      rawEntries.map(StickerLibraryEntry::displayName),
      order,
    ).mapNotNull { name -> rawEntries.firstOrNull { it.displayName == name } }
    return StickerLibraryPage(folderPath, sorted)
  }

  /** 读取整棵排序表（坏文件容错为空表）。 */
  fun readOrder(): Map<String, List<String>> {
    val root = stickersRoot() ?: return emptyMap()
    val orderUri = root.findChild(StickerPanelOrder.ORDER_FILE_NAME) ?: return emptyMap()
    val text = resolver.openInputStream(orderUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: return emptyMap()
    return StickerPanelOrder.decode(text)
  }

  /**
   * 持久化拖拽后的排序表（SAF 恢复写入，对齐 ProjectPenStore 的 annotations
   * 恢复副本姿态）。order.json 已存在时保留恢复副本名返回值。
   */
  fun writeOrder(orders: Map<String, List<String>>): String? {
    val root = findOrCreateStickersRoot()
    val encoded = StickerPanelOrder.encode(orders)
    val existing = root.findChild(StickerPanelOrder.ORDER_FILE_NAME)
    return if (existing == null) {
      val created = DocumentsContract.createDocument(
        resolver,
        root,
        "application/json",
        StickerPanelOrder.ORDER_FILE_NAME,
      ) ?: throw IOException("The sticker order file could not be created")
      if (queryDisplayName(created) != StickerPanelOrder.ORDER_FILE_NAME) {
        runCatching { DocumentsContract.deleteDocument(resolver, created) }
        throw IOException("The project changed the sticker order file name")
      }
      resolver.openOutputStream(created, "w")?.buffered()?.use { output ->
        output.write(encoded.toByteArray(Charsets.UTF_8))
      } ?: throw IOException("The sticker order file could not be written")
      null
    } else {
      val original = resolver.openInputStream(existing)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IOException("The sticker order file could not be read")
      SafRecoverableDocumentWriter(resolver, existing, StickerPanelOrder.ORDER_FILE_NAME)
        .write(encoded, original)
    }
  }

  /** 删除一个贴纸/目录（面板管理入口）。order.json 本身拒绝删除。 */
  fun delete(entry: StickerLibraryEntry): Boolean {
    if (entry.displayName == StickerPanelOrder.ORDER_FILE_NAME) return false
    return runCatching { DocumentsContract.deleteDocument(resolver, entry.documentUri) }
      .getOrDefault(false)
  }

  /** 重命名一个贴纸/目录（返回新显示名；provider 改名时以实际结果为准）。 */
  fun rename(entry: StickerLibraryEntry, newDisplayName: String): String? {
    val safe = StickerPackNames.sanitizeSegment(newDisplayName)
    if (safe.isBlank() || safe == entry.displayName) return null
    if (entry.displayName == StickerPanelOrder.ORDER_FILE_NAME) return null
    val extension = entry.displayName.substringAfterLast('.', "")
    val candidate = if (entry.kind == StickerLibraryEntryKind.FOLDER || extension.isEmpty() ||
      extension == entry.displayName
    ) {
      safe
    } else {
      "${safe.substringBeforeLast('.', safe)}.$extension"
    }
    return runCatching {
      DocumentsContract.renameDocument(resolver, entry.documentUri, candidate)
    }.getOrNull()?.let { queryDisplayName(it) ?: candidate }
  }

  private fun stickersRoot(): Uri? = sourceDirectoryUri.findChild(STICKERS_DIRECTORY)

  private fun findOrCreateStickersRoot(): Uri =
    stickersRoot() ?: (
      DocumentsContract.createDocument(
        resolver,
        sourceDirectoryUri,
        DocumentsContract.Document.MIME_TYPE_DIR,
        STICKERS_DIRECTORY,
      ) ?: throw IOException("The stickers directory could not be created")
      )

  private fun descend(root: Uri, folderPath: String): Uri? {
    var current: Uri? = root
    folderPath.split('/').filter(String::isNotBlank).forEach { segment ->
      current = current?.findChild(segment)
    }
    return current
  }

  private fun Uri.findChild(displayName: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(this)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(this, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      ),
      null,
      null,
      null,
    ) ?: throw IOException("The stickers directory could not be listed")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == displayName) {
          return DocumentsContract.buildDocumentUriUsingTree(this, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun listChildren(folderUri: Uri): List<StickerLibraryEntry> {
    val parentId = DocumentsContract.getDocumentId(folderUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentId)
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
    ) ?: throw IOException("The stickers directory could not be listed")
    return cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
      buildList {
        while (it.moveToNext()) {
          val name = it.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
          val mimeType = it.getString(mimeColumn)
          val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, it.getString(idColumn))
          val kind = when {
            mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> StickerLibraryEntryKind.FOLDER
            name.endsWith(".gif", ignoreCase = true) -> StickerLibraryEntryKind.GIF
            else -> StickerLibraryEntryKind.IMAGE
          }
          if (name == StickerPanelOrder.ORDER_FILE_NAME && kind != StickerLibraryEntryKind.FOLDER) continue
          add(
            StickerLibraryEntry(
              displayName = name,
              kind = kind,
              sizeBytes = sizeColumn.takeIf { column -> column >= 0 }
                ?.let(it::getLong)
                ?.takeIf { size -> size >= 0 },
              documentUri = uri,
            ),
          )
        }
      }
    }
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

  private companion object {
    const val STICKERS_DIRECTORY = "stickers"
  }
}
