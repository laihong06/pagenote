package io.pagenote.android

import android.content.ContentResolver
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import io.pagenote.core.project.ProjectManifest
import io.pagenote.core.project.ProjectStore
import io.pagenote.core.project.ProjectValidation
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ProjectCreator(
  private val resolver: ContentResolver,
  private val stagingDirectory: File,
) {
  companion object {
    const val PROJECT_MANIFEST_MIME_TYPE = "application/vnd.pagenote.project"
  }

  fun create(
    parentTreeUri: Uri,
    name: String,
    sourceDocumentUri: Uri? = null,
    sourceDisplayName: String? = null,
  ): ProjectOpenTarget {
    val displayName = if (sourceDocumentUri != null) {
      sourceDisplayName?.takeIf(String::isNotBlank) ?: "document.pdf"
    } else {
      null
    }
    val manifest = try {
      NewProjectManifestPolicy.create(name, displayName)
    } catch (error: IllegalArgumentException) {
      throw IOException(error.message ?: "不支持的原文格式", error)
    }
    val extension = displayName?.let(NewProjectManifestPolicy::sourceExtension)
    val parentId = DocumentsContract.getTreeDocumentId(parentTreeUri)
    if (childExists(parentTreeUri, parentId, name)) {
      throw IOException("同名项目已存在：$name")
    }

    val createdDocuments = mutableListOf<Uri>()
    var stagedPdf: File? = null
    try {
      if (sourceDocumentUri != null && extension == "pdf") {
        stagedPdf = stageAndValidatePdf(sourceDocumentUri)
      }
      val parentUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, parentId)
      val projectUri = createDocument(
        parentUri,
        DocumentsContract.Document.MIME_TYPE_DIR,
        name,
        createdDocuments,
      )
      val srcUri = createDocument(
        projectUri,
        DocumentsContract.Document.MIME_TYPE_DIR,
        "src",
        createdDocuments,
      )
      createDocument(
        srcUri,
        DocumentsContract.Document.MIME_TYPE_DIR,
        "assets",
        createdDocuments,
      )

      if (sourceDocumentUri == null) {
        writeText(srcUri, "left.md", "# ${manifest.name}\n", "text/markdown", createdDocuments)
      } else if (extension == "pdf") {
        val verifiedSource = requireNotNull(stagedPdf)
        val pdfUri = createDocument(
          srcUri,
          "application/pdf",
          "document.pdf",
          createdDocuments,
        )
        resolver.openOutputStream(pdfUri, "wt")?.use { output ->
          verifiedSource.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("无法写入 src/document.pdf")
        if (verifiedSource.delete()) stagedPdf = null
        validateProjectPdf(pdfUri)
      } else {
        val documentName = "document.$extension"
        val documentMimeType = if (extension == "md" || extension == "markdown") {
          "text/markdown"
        } else {
          error("Unsupported document extension: $extension")
        }
        val documentUri = createDocument(
          srcUri,
          documentMimeType,
          documentName,
          createdDocuments,
        )
        val input = resolver.openInputStream(requireNotNull(sourceDocumentUri))
          ?: throw IOException("无法读取所选原文")
        input.use { source ->
          resolver.openOutputStream(documentUri, "wt")?.use { output -> source.copyTo(output) }
            ?: throw IOException("无法写入 src/$documentName")
        }
      }
      writeText(srcUri, "right.md", "", "text/markdown", createdDocuments)

      val manifestUri = createDocument(
        projectUri,
        PROJECT_MANIFEST_MIME_TYPE,
        "project.pagenote",
        createdDocuments,
      )
      resolver.openOutputStream(manifestUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
        it.write(manifestJson(manifest))
      } ?: throw IOException("无法写入 project.pagenote")
      return ProjectOpenTarget.fromCreatedProject(parentTreeUri, projectUri)
    } catch (error: Exception) {
      rollback(createdDocuments, name, error)
      throw error
    } finally {
      stagedPdf?.delete()
    }
  }

  private fun stageAndValidatePdf(sourceUri: Uri): File {
    if (!stagingDirectory.isDirectory && !stagingDirectory.mkdirs()) {
      throw IOException("无法创建 PDF 临时目录")
    }
    val staged = File.createTempFile("pagenote-import-", ".pdf", stagingDirectory)
    try {
      val input = resolver.openInputStream(sourceUri) ?: throw IOException("无法读取所选 PDF")
      input.use { source -> staged.outputStream().use { output -> source.copyTo(output) } }
      validatePdfFile(staged, "所选文件不是有效 PDF")
      return staged
    } catch (error: Exception) {
      staged.delete()
      if (error is IOException) throw error
      throw IOException("所选文件不是有效 PDF", error)
    }
  }

  private fun validateProjectPdf(pdfUri: Uri) {
    val copiedPdf = File.createTempFile("pagenote-copy-", ".pdf", stagingDirectory)
    try {
      val input = resolver.openInputStream(pdfUri) ?: throw IOException("无法读取已复制的 PDF")
      input.use { source -> copiedPdf.outputStream().use { output -> source.copyTo(output) } }
      validatePdfFile(copiedPdf, "项目内 PDF 副本校验失败")
    } finally {
      copiedPdf.delete()
    }
  }

  private fun validatePdfFile(file: File, failureMessage: String) {
    try {
      if (file.length() == 0L) throw IOException("PDF 文件为空")
      val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
      descriptor.use { fileDescriptor ->
        PdfRenderer(fileDescriptor).use { renderer ->
          if (renderer.pageCount <= 0) throw IOException("PDF 至少需要包含一页")
        }
      }
    } catch (error: Exception) {
      throw IOException(failureMessage, error)
    }
  }

  private fun childExists(treeUri: Uri, parentId: String, name: String): Boolean {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    ) ?: throw IOException("无法列出所选目录")
    cursor.use {
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) return true
      }
    }
    return false
  }

  private fun createDocument(
    parentUri: Uri,
    mimeType: String,
    name: String,
    createdDocuments: MutableList<Uri>,
  ): Uri {
    val documentUri = DocumentsContract.createDocument(resolver, parentUri, mimeType, name)
      ?: throw IOException("无法创建 $name")
    createdDocuments += documentUri
    return documentUri
  }

  private fun writeText(
    parentUri: Uri,
    name: String,
    text: String,
    mimeType: String,
    createdDocuments: MutableList<Uri>,
  ) {
    val fileUri = createDocument(parentUri, mimeType, name, createdDocuments)
    resolver.openOutputStream(fileUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write(text)
    } ?: throw IOException("无法写入 $name")
  }

  private fun rollback(createdDocuments: List<Uri>, name: String, cause: Exception) {
    val cleanupFailures = mutableListOf<Exception>()
    createdDocuments.asReversed().forEach { uri ->
      try {
        if (!DocumentsContract.deleteDocument(resolver, uri)) {
          cleanupFailures += IOException("无法删除部分项目文件：$uri")
        }
      } catch (cleanupError: Exception) {
        cleanupFailures += cleanupError
      }
    }
    if (cleanupFailures.isNotEmpty()) {
      val failure = IOException("项目创建失败，且无法清理全部内容：$name", cause)
      cleanupFailures.forEach(failure::addSuppressed)
      throw failure
    }
  }

  private fun manifestJson(manifest: ProjectManifest): String {
    ProjectValidation.validate(manifest)
    val json = buildJsonObject {
      put("format", JsonPrimitive(manifest.format))
      put("version", JsonPrimitive(manifest.version))
      put("id", JsonPrimitive(manifest.id))
      put("name", JsonPrimitive(manifest.name))
      manifest.created?.let { put("created", JsonPrimitive(it)) }
      manifest.modified?.let { put("modified", JsonPrimitive(it)) }
      put("left", JsonPrimitive(manifest.left))
      put("right", JsonPrimitive(manifest.right))
      manifest.leftName?.let { put("leftName", JsonPrimitive(it)) }
      manifest.preview?.let { put("preview", JsonPrimitive(it)) }
    }
    return Json.encodeToString(JsonObject.serializer(), json)
  }
}
