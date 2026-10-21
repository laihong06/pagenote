package io.pagenote.android

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.core.project.ProjectManifest
import io.pagenote.core.project.ProjectManifestCodec
import io.pagenote.core.project.ProjectValidation

data class OpenedProject(
  val manifest: ProjectManifest,
  val sourceUri: Uri,
  val notesUri: Uri,
  val sourceDirectoryUri: Uri,
  val annotationsUri: Uri?,
  val reviewsUri: Uri?,
  val handoffsUri: Uri?,
  val sourceName: String,
  val sourceExtension: String,
) {
  val name: String get() = manifest.name
}

class ProjectDocumentOpener(private val resolver: ContentResolver) {
  fun open(treeUri: Uri, rootDocumentId: String? = null): OpenedProject {
    val projectRootId = rootDocumentId ?: try {
      DocumentsContract.getTreeDocumentId(treeUri)
    } catch (error: IllegalArgumentException) {
      throw IllegalArgumentException("请选择项目文件夹", error)
    }
    val manifestUri = findDocument(treeUri, projectRootId, listOf("project.pagenote"))
      ?: throw IllegalArgumentException("项目文件夹中没有 project.pagenote")
    val manifestText = resolver.openInputStream(manifestUri)?.bufferedReader(Charsets.UTF_8)?.use {
      it.readText()
    } ?: throw IllegalStateException("无法读取 project.pagenote")
    val manifest = ProjectManifestCodec.decode(manifestText)
    val sourceSegments = ProjectValidation.resolveLinkSegments(manifest.left)
    val notesSegments = ProjectValidation.resolveLinkSegments(manifest.right)
    val sourceUri = findDocument(treeUri, projectRootId, sourceSegments)
      ?: throw IllegalArgumentException("找不到原文：${manifest.left}")
    val sourceDirectoryUri = findDocument(treeUri, projectRootId, listOf("src"))
      ?: throw IllegalArgumentException("项目文件夹中没有 src 目录")
    val annotationsUri = findDocument(treeUri, projectRootId, listOf("src", "annotations.json"))
    val reviewsUri = findDocument(treeUri, projectRootId, listOf("src", "ai-reviews.json"))
    val handoffsUri = findDocument(treeUri, projectRootId, listOf("src", "ai-handoffs.json"))
    val notesUri = findDocument(treeUri, projectRootId, notesSegments)
      ?: throw IllegalArgumentException("找不到笔记：${manifest.right}")
    val sourceName = manifest.leftName ?: sourceSegments.last()
    val extension = sourceSegments.last().substringAfterLast('.', "").lowercase()
    require(extension in setOf("pdf", "md", "markdown")) { "安卓端不支持此原文格式：$extension" }
    return OpenedProject(
      manifest = manifest,
      sourceUri = sourceUri,
      notesUri = notesUri,
      sourceDirectoryUri = sourceDirectoryUri,
      annotationsUri = annotationsUri,
      reviewsUri = reviewsUri,
      handoffsUri = handoffsUri,
      sourceName = sourceName,
      sourceExtension = extension,
    )
  }

  private fun findDocument(treeUri: Uri, rootId: String, path: List<String>): Uri? {
    var parentId = rootId
    var childUri: Uri? = null
    for (name in path) {
      childUri = findChild(treeUri, parentId, name) ?: return null
      parentId = DocumentsContract.getDocumentId(childUri)
    }
    return childUri
  }

  private fun findChild(treeUri: Uri, parentId: String, name: String): Uri? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val columns = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    )
    val cursor: Cursor = resolver.query(childrenUri, columns, null, null, null)
      ?: throw IllegalStateException("无法列出项目文件夹内容")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) {
          return DocumentsContract.buildDocumentUriUsingTree(treeUri, it.getString(idColumn))
        }
      }
    }
    return null
  }
}
