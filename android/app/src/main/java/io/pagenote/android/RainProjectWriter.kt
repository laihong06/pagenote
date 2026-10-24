package io.pagenote.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
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
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal class RainProjectWriter(
  private val context: Context,
  private val resolver: ContentResolver = context.contentResolver,
) {
  private val documents = SafPreparedDocumentAccess(resolver)

  fun validateDestination(parentTreeUri: Uri, name: String) {
    require(name.isNotBlank() && !Regex("[<>:\"/\\\\|?*]").containsMatchIn(name)) { "请输入有效的项目名称" }
    val rootId = DocumentsContract.getTreeDocumentId(parentTreeUri)
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, rootId)
    require(documents.findChild(rootUri.toString(), name) == null) { "所选目录中已存在同名项目：$name" }
    resolver.query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
      require(cursor.moveToFirst()) { "无法读取所选目录" }
      val flags = cursor.getInt(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS))
      require(flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "所选目录无法创建项目" }
    } ?: throw IOException("无法读取所选目录")
  }

  suspend fun create(
    parentTreeUri: Uri,
    name: String,
    course: RainCourse,
    lesson: RainLesson,
    downloadPages: suspend (suspend (RainSlideImage) -> Unit) -> Unit,
  ): ProjectOpenTarget = withContext(Dispatchers.IO) {
    validateDestination(parentTreeUri, name)
    val rootId = DocumentsContract.getTreeDocumentId(parentTreeUri)
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, rootId)
    val projectUri = DocumentsContract.createDocument(
      resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, name,
    ) ?: throw IOException("所选目录无法创建项目")
    try {
      val src = Uri.parse(documents.createDirectory(projectUri.toString(), "src"))
      val assets = Uri.parse(documents.createDirectory(src.toString(), "assets"))
      val rainAssets = Uri.parse(documents.createDirectory(assets.toString(), "yuketang"))
      var sourceEntries = JSONArray()
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
        val order = RainPageOrder(sourceManifest(course, lesson, "", sourceEntries), pageCount)
        reorderPdf(document, order)
        sourceEntries = order.pages
        writeBytes(src, "document.pdf", "application/pdf", savePdf(document))
      } finally {
        document.close()
      }
      writeText(src, "right.md", "text/markdown", PageNotes.join(List(pageCount) { "" }))
      val firstPresentationId = sourceEntries.getJSONObject(0).getString("presentationId")
      val manifestSource = sourceManifest(course, lesson, firstPresentationId, sourceEntries)
        .put("sections", RainPageOrder(sourceManifest(course, lesson, firstPresentationId, sourceEntries), pageCount).sections)
      writeText(src, "yuketang-source.json", "application/json", manifestSource.toString(2))
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
  ): Int? = insert(project, course, lesson, page).let { if (it.added) it.pageNumber else null }

  suspend fun insert(
    project: OpenedProject,
    course: RainCourse,
    lesson: RainLesson,
    page: RainSlideImage,
  ): RainPageWrite = withContext(Dispatchers.IO) {
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
      if (existingIdentity == incomingIdentity) return@withContext RainPageWrite(entry.getInt("projectPage"), false)
    }

    val assetEntry = writeAsset(rainAssets, page)
    lateinit var order: RainPageOrder
    var oldPageCount = 0
    val nextPdf = PDDocument.load(ByteArrayInputStream(oldPdf)).use { document ->
      oldPageCount = document.numberOfPages
      order = RainPageOrder(sourceManifest, oldPageCount, sourceEntry(page, assetEntry, oldPageCount + 1, course, lesson))
      addPdfPage(document, page)
      reorderPdf(document, order)
      savePdf(document)
    }
    val oldNotes = ProjectNoteStore(resolver, project.notesUri).read()
    val notes = PageNotes.split(oldNotes).toMutableList()
    if (notes.size > oldPageCount)
      throw IOException("项目笔记页数与 PDF 不一致，已停止追加以保护现有笔记")
    while (notes.size < oldPageCount) notes.add("")
    val nextPage = order.oldToNew.getValue(oldPageCount + 1)
    notes.add("")
    val newNotes = PageNotes.join(order.slots.map { notes[it.oldPage - 1] })
    data class Sidecar(val uri: Uri, val old: ByteArray, val updated: ByteArray)
    val sidecars = listOf("annotations.json", "ai-reviews.json", "ai-handoffs.json").mapNotNull { name ->
      val uri = access.findChild(src.toString(), name)?.let(Uri::parse) ?: return@mapNotNull null
      val old = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("无法备份 $name")
      Sidecar(uri, old, order.remapSidecar(name, old.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
    }
    val historyDirectory = NoteHistoryMirror.directory(context.filesDir, project.manifest.id)
    val historyMoves = order.oldToNew.filter { (old, next) -> old <= oldPageCount && old != next }.flatMap { (old, next) ->
      NoteHistoryMirror.entries(context.filesDir, project.manifest.id, old).map { version ->
        File(historyDirectory, version.id) to File(historyDirectory, version.id.removeSuffix("-page$old.md") + "-page$next.md")
      }
    }
    require(historyMoves.none { it.second.exists() }) { "笔记历史目标已存在，已停止插页" }
    val updatedManifest = sourceManifest
      .put("format", "pagenote-yuketang-source")
      .put("version", 1)
      .put("provider", "www.yuketang.cn")
      .put("courseId", course.courseId)
      .put("classroomId", course.classroomId)
      .put("lessonId", lesson.lessonId.orEmpty())
      .put("presentationId", page.slide.presentationId)
      .put("pages", order.pages)
      .put("sections", order.sections)
    val previousManifestBytes = sourceManifestUri?.let { uri ->
      resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("无法备份雨课堂来源记录")
    }
    var writtenManifestUri = sourceManifestUri
    val attemptedSidecars = mutableListOf<Sidecar>()
    val movedHistory = mutableListOf<Pair<File, File>>()
    try {
      resolver.openOutputStream(sourceUri, "wt")?.use { it.write(nextPdf) }
        ?: throw IOException("无法写入当前项目 PDF")
      ProjectNoteStore(resolver, project.notesUri).write(newNotes, oldNotes)
      for (sidecar in sidecars) {
        attemptedSidecars.add(sidecar)
        overwrite(sidecar.uri, sidecar.updated)
      }
      for (move in historyMoves) {
        if (!move.first.renameTo(move.second)) throw IOException("无法调整笔记历史页码")
        movedHistory.add(move)
      }
      val manifestUri = sourceManifestUri ?: Uri.parse(
        access.createFile(src.toString(), "application/json", "yuketang-source.json"),
      ).also { writtenManifestUri = it }
      resolver.openOutputStream(manifestUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
        it.write(updatedManifest.toString(2))
      } ?: throw IOException("无法更新雨课堂来源记录")
      RainPageWrite(nextPage, true, order.oldToNew.filterKeys { it <= oldPageCount })
    } catch (error: Exception) {
      runCatching {
        resolver.openOutputStream(sourceUri, "wt")?.use { it.write(oldPdf) }
          ?: throw IOException("无法回滚 PDF")
      }.onFailure(error::addSuppressed)
      runCatching { ProjectNoteStore(resolver, project.notesUri).write(oldNotes, newNotes) }
        .onFailure(error::addSuppressed)
      for (sidecar in attemptedSidecars.asReversed()) {
        runCatching { overwrite(sidecar.uri, sidecar.old) }.onFailure(error::addSuppressed)
      }
      for ((old, next) in movedHistory.asReversed()) {
        runCatching { if (!next.renameTo(old)) throw IOException("无法回滚笔记历史页码") }.onFailure(error::addSuppressed)
      }
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

  private fun overwrite(uri: Uri, bytes: ByteArray) {
    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
      ?: throw IOException("无法写入项目文件")
  }

  private fun reorderPdf(document: PDDocument, order: RainPageOrder) {
    val sectionKey = COSName.getPDFName("PagenoteRainSection")
    fun copyOutline(item: PDOutlineItem): PDOutlineItem {
      val dictionary = COSDictionary(item.cosObject)
      for (key in listOf(COSName.PARENT, COSName.PREV, COSName.NEXT, COSName.FIRST, COSName.LAST, COSName.COUNT)) dictionary.removeItem(key)
      return PDOutlineItem(dictionary).apply {
        for (child in item.children()) addLast(copyOutline(child))
        if (item.isNodeOpen) openNode()
      }
    }
    val retainedOutlines = document.documentCatalog.documentOutline?.children()
      ?.filterNot { it.cosObject.getBoolean(sectionKey, false) }?.map(::copyOutline).orEmpty()
    val original = (0 until document.numberOfPages).map(document::getPage)
    while (document.numberOfPages > 0) document.removePage(0)
    for (slot in order.slots) document.addPage(original[slot.oldPage - 1])
    val outline = PDDocumentOutline()
    document.documentCatalog.documentOutline = outline
    retainedOutlines.forEach(outline::addLast)
    for (index in 0 until order.sections.length()) {
      val section = order.sections.getJSONObject(index)
      outline.addLast(PDOutlineItem().apply {
        cosObject.setBoolean(sectionKey, true)
        title = section.getString("title")
        setDestination(document.getPage(section.getInt("startPage") - 1))
      })
    }
    outline.openNode()
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
