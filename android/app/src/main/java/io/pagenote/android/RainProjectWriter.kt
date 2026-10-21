package io.pagenote.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.yuketang.RainCourse
import io.pagenote.feature.yuketang.RainLesson
import io.pagenote.feature.yuketang.RainSourcePageIdentity
import io.pagenote.feature.yuketang.RainSlideImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal class RainProjectWriter(
  private val context: Context,
  private val resolver: ContentResolver = context.contentResolver,
) {
  private val documents = SafPreparedDocumentAccess(resolver)

  suspend fun create(
    parentTreeUri: Uri,
    name: String,
    course: RainCourse,
    lesson: RainLesson,
    downloadPages: suspend (suspend (RainSlideImage) -> Unit) -> Unit,
  ): ProjectOpenTarget = withContext(Dispatchers.IO) {
    require(name.isNotBlank() && !Regex("[<>:\"/\\\\|?*]").containsMatchIn(name)) { "请输入有效的项目名称" }
    val rootId = DocumentsContract.getTreeDocumentId(parentTreeUri)
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, rootId)
    require(documents.findChild(rootUri.toString(), name) == null) { "所选目录中已存在同名项目：$name" }
    val projectUri = DocumentsContract.createDocument(
      resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, name,
    ) ?: throw IOException("所选目录无法创建项目")
    try {
      val src = Uri.parse(documents.createDirectory(projectUri.toString(), "src"))
      val assets = Uri.parse(documents.createDirectory(src.toString(), "assets"))
      val rainAssets = Uri.parse(documents.createDirectory(assets.toString(), "yuketang"))
      val sourceEntries = JSONArray()
      val document = newPdf()
      var pageCount = 0
      try {
        downloadPages { page ->
          addPdfPage(document, page)
          val entry = writeAsset(rainAssets, page)
          pageCount++
          sourceEntries.put(sourceEntry(page, entry, pageCount, course, lesson))
        }
        require(pageCount > 0) { "雨课堂课件没有可导入的页面" }
        writeBytes(src, "document.pdf", "application/pdf", savePdf(document))
      } finally {
        document.close()
      }
      writeText(src, "right.md", "text/markdown", PageNotes.join(List(pageCount) { "" }))
      writeText(src, "yuketang-source.json", "application/json", sourceManifest(course, lesson, lesson.presentationId, sourceEntries).toString(2))
      val projectId = sha256(UUID.randomUUID().toString().toByteArray())
      val manifest = JSONObject()
        .put("format", "pagenote-project")
        .put("version", 1)
        .put("id", projectId)
        .put("name", name)
        .put("created", Instant.now().toString())
        .put("left", "src/document.pdf")
        .put("right", "src/right.md")
      writeText(projectUri, "project.pagenote", ProjectCreator.PROJECT_MANIFEST_MIME_TYPE, manifest.toString(2))
      ProjectOpenTarget.fromCreatedProject(parentTreeUri, projectUri)
    } catch (error: Exception) {
      runCatching { DocumentsContract.deleteDocument(resolver, projectUri) }
      throw error
    }
  }

  suspend fun append(
    project: OpenedProject,
    course: RainCourse,
    lesson: RainLesson,
    page: RainSlideImage,
  ): Int? = withContext(Dispatchers.IO) {
    require(project.sourceExtension == "pdf") { "实时雨课堂采集需要 PDF 项目" }
    val sourceUri = project.sourceUri
    val oldPdf = resolver.openInputStream(sourceUri)?.use { it.readBytes() }
      ?: throw IOException("无法读取当前项目 PDF")
    val src = project.sourceDirectoryUri
    val access = SafPreparedDocumentAccess(resolver)
    val assets = access.findChild(src.toString(), "assets")?.let(Uri::parse)
      ?: Uri.parse(access.createDirectory(src.toString(), "assets"))
    val rainAssets = access.findChild(assets.toString(), "yuketang")?.let(Uri::parse)
      ?: Uri.parse(access.createDirectory(assets.toString(), "yuketang"))
    val sourceManifestUri = access.findChild(src.toString(), "yuketang-source.json")?.let(Uri::parse)
    val sourceManifest = sourceManifestUri?.let { uri ->
      resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { JSONObject(it.readText()) }
    } ?: sourceManifest(course, lesson, page.slide.presentationId, JSONArray())
    val entries = sourceManifest.optJSONArray("pages") ?: JSONArray()
    val digest = sha256(page.bytes)
    val incomingIdentity = RainSourcePageIdentity(
      course.classroomId,
      page.slide.presentationId,
      page.slide.pageId,
      digest,
    )
    for (index in 0 until entries.length()) {
      val entry = entries.optJSONObject(index) ?: continue
      val existingIdentity = RainSourcePageIdentity(
        entry.optString("classroomId").ifBlank { sourceManifest.optString("classroomId") },
        entry.optString("presentationId"),
        entry.optString("pageId"),
        entry.optString("sha256"),
      )
      if (existingIdentity == incomingIdentity) return@withContext null
    }

    var nextPageNumber = 0
    val nextPdf = PDDocument.load(ByteArrayInputStream(oldPdf)).use { document ->
      addPdfPage(document, page)
      nextPageNumber = document.numberOfPages
      savePdf(document)
    }
    val oldNotes = ProjectNoteStore(resolver, project.notesUri).read()
    val notes = PageNotes.split(oldNotes).toMutableList()
    if (notes.size > nextPageNumber - 1)
      throw IOException("项目笔记页数与 PDF 不一致，已停止追加以保护现有笔记")
    while (notes.size < nextPageNumber - 1) notes.add("")
    val nextPage = nextPageNumber
    notes.add("")
    val newNotes = PageNotes.join(notes)
    val assetEntry = writeAsset(rainAssets, page)
    val migratedEntries = JSONArray()
    for (index in 0 until entries.length()) {
      val entry = JSONObject(entries.getJSONObject(index).toString())
      if (!entry.has("courseId")) entry.put("courseId", sourceManifest.optString("courseId"))
      if (!entry.has("classroomId")) entry.put("classroomId", sourceManifest.optString("classroomId"))
      if (!entry.has("lessonId")) entry.put("lessonId", sourceManifest.optString("lessonId"))
      migratedEntries.put(entry)
    }
    val updatedManifest = sourceManifest
      .put("format", "pagenote-yuketang-source")
      .put("version", 1)
      .put("provider", "www.yuketang.cn")
      .put("courseId", course.courseId)
      .put("classroomId", course.classroomId)
      .put("lessonId", lesson.lessonId.orEmpty())
      .put("presentationId", page.slide.presentationId)
      .put("pages", migratedEntries.put(sourceEntry(page, assetEntry, nextPage, course, lesson)))
    val previousManifestBytes = sourceManifestUri?.let { uri ->
      resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("无法备份雨课堂来源记录")
    }
    var writtenManifestUri = sourceManifestUri
    try {
      resolver.openOutputStream(sourceUri, "wt")?.use { it.write(nextPdf) }
        ?: throw IOException("无法写入当前项目 PDF")
      ProjectNoteStore(resolver, project.notesUri).write(newNotes, oldNotes)
      val manifestUri = sourceManifestUri ?: Uri.parse(
        access.createFile(src.toString(), "application/json", "yuketang-source.json"),
      ).also { writtenManifestUri = it }
      resolver.openOutputStream(manifestUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
        it.write(updatedManifest.toString(2))
      } ?: throw IOException("无法更新雨课堂来源记录")
      nextPage
    } catch (error: Exception) {
      runCatching {
        resolver.openOutputStream(sourceUri, "wt")?.use { it.write(oldPdf) }
          ?: throw IOException("无法回滚 PDF")
      }.onFailure(error::addSuppressed)
      runCatching { ProjectNoteStore(resolver, project.notesUri).write(oldNotes, newNotes) }
        .onFailure(error::addSuppressed)
      if (previousManifestBytes != null) {
        runCatching {
          resolver.openOutputStream(checkNotNull(writtenManifestUri), "wt")?.use { it.write(previousManifestBytes) }
            ?: throw IOException("无法回滚雨课堂来源记录")
        }.onFailure(error::addSuppressed)
      } else {
        writtenManifestUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) }.onFailure(error::addSuppressed) }
      }
      throw error
    }
  }

  private fun newPdf(): PDDocument {
    PDFBoxResourceLoader.init(context.applicationContext)
    return PDDocument()
  }

  private fun addPdfPage(document: PDDocument, page: RainSlideImage) {
    val bitmap = BitmapFactory.decodeByteArray(page.bytes, 0, page.bytes.size)
      ?: throw IOException("无法解码雨课堂课件页面")
    try {
      val scale = minOf(960f / bitmap.width, 720f / bitmap.height)
      val width = maxOf(1f, bitmap.width * scale)
      val height = maxOf(1f, bitmap.height * scale)
      val pdfPage = PDPage(PDRectangle(width, height))
      document.addPage(pdfPage)
      val image = if (page.contentType == "image/jpeg") {
        JPEGFactory.createFromImage(document, bitmap, 0.88f)
      } else {
        LosslessFactory.createFromImage(document, bitmap)
      }
      PDPageContentStream(document, pdfPage).use { it.drawImage(image, 0f, 0f, width, height) }
    } finally {
      bitmap.recycle()
    }
  }

  private fun savePdf(document: PDDocument): ByteArray = ByteArrayOutputStream().use { output ->
    document.save(output)
    output.toByteArray()
  }

  private fun writeAsset(directory: Uri, page: RainSlideImage): String {
    val digest = sha256(page.bytes)
    val extension = if (page.contentType == "image/png") "png" else "jpg"
    val safeId = page.slide.pageId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64).ifBlank { "page" }
    val name = "$safeId-${digest.take(16)}.$extension"
    val existing = documents.findChild(directory.toString(), name)
    if (existing == null) writeBytes(directory, name, page.contentType, page.bytes)
    return "src/assets/yuketang/$name"
  }

  private fun sourceEntry(
    page: RainSlideImage,
    asset: String,
    projectPage: Int,
    course: RainCourse,
    lesson: RainLesson,
  ) = JSONObject()
    .put("pageId", page.slide.pageId)
    .put("courseId", course.courseId)
    .put("classroomId", course.classroomId)
    .put("lessonId", lesson.lessonId.orEmpty())
    .put("presentationId", page.slide.presentationId)
    .put("sourceIndex", page.slide.sourceIndex)
    .put("projectPage", projectPage)
    .put("sha256", sha256(page.bytes))
    .put("asset", asset)
    .put("firstSeenAt", Instant.now().toString())

  private fun sourceManifest(course: RainCourse, lesson: RainLesson, presentationId: String, pages: JSONArray) =
    JSONObject().put("format", "pagenote-yuketang-source")
      .put("version", 1).put("provider", "www.yuketang.cn")
      .put("courseId", course.courseId).put("classroomId", course.classroomId)
      .put("lessonId", lesson.lessonId.orEmpty()).put("presentationId", presentationId)
      .put("pages", pages)

  private fun writeText(parent: Uri, name: String, mime: String, text: String) =
    writeBytes(parent, name, mime, text.toByteArray(Charsets.UTF_8))

  private fun writeBytes(parent: Uri, name: String, mime: String, bytes: ByteArray): Uri {
    val uri = Uri.parse(documents.createFile(parent.toString(), mime, name))
    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
      ?: throw IOException("无法写入 $name")
    return uri
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
}
