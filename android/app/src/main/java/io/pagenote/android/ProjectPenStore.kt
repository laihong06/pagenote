package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class ProjectPenStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
  annotationsUri: Uri?,
) {
  private var annotationsUri: Uri? = annotationsUri

  @Volatile
  internal var lastRetainedRecoveryCopyName: String? = null
    private set

  fun supportsWrite(): Boolean {
    val resolvedUri = resolveAnnotationsUri()
    val uri = resolvedUri ?: sourceDirectoryUri
    val flag = if (resolvedUri == null) {
      DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
    } else {
      DocumentsContract.Document.FLAG_SUPPORTS_WRITE
    }
    val cursor = resolver.query(
      uri,
      arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
      null,
      null,
      null,
    ) ?: return false
    return cursor.use {
      if (!it.moveToFirst()) return@use false
      val column = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
      column >= 0 && (it.getInt(column) and flag) != 0
    }
  }

  fun readPage(side: PenSide, page: Int): List<PenStroke> {
    require(page >= 1) { "bad page: $page" }
    val uri = resolveAnnotationsUri() ?: return emptyList()
    val root = readRoot(uri)
    val items = root.optJSONObject(side.key)
      ?.optJSONArray(page.toString())
      ?.let(::freehandItems)
      .orEmpty()
    if (items.isEmpty()) return emptyList()
    val store = PenPageStore()
    store.loadAnnotationsMap(mapOf(side.key to mapOf(page.toString() to items)))
    return store.getItems(side, page)
  }

  fun readPages(side: PenSide): Map<String, Any?> {
    val uri = resolveAnnotationsUri() ?: return emptyMap()
    val sidePages = readRoot(uri).optJSONObject(side.key) ?: return emptyMap()
    return sidePages.keys().asSequence().mapNotNull { pageKey ->
      val page = pageKey.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
      val items = sidePages.optJSONArray(pageKey)?.let(::freehandItems).orEmpty()
      page.toString() to items
    }.toMap(linkedMapOf())
  }

  fun writePage(side: PenSide, page: Int, strokes: List<PenStroke>) {
    lastRetainedRecoveryCopyName = null
    require(page >= 1) { "bad page: $page" }
    var uri = resolveAnnotationsUri()
    if (uri == null && strokes.isEmpty()) return

    var createdUri: Uri? = null
    try {
      if (uri == null) {
        uri = DocumentsContract.createDocument(
          resolver,
          sourceDirectoryUri,
          "application/json",
          "annotations.json",
        ) ?: throw IOException("无法在项目 src 目录创建 annotations.json")
        createdUri = uri
      }

      val original = if (createdUri == null) {
        readRawRoot(uri)
      } else {
        null
      }
      val latest = original?.let(::parseRoot)
        ?: JSONObject().put("document", JSONObject()).put("note", JSONObject())
      val updated = AnnotationPageMerger.replacePage(
        root = latest.toKotlinMap(),
        side = side.key,
        page = page,
        strokes = strokes,
      )
      val encoded = updated.toJsonObject().toString()
      if (original == null) {
        overwrite(uri, encoded)
      } else {
        lastRetainedRecoveryCopyName = SafRecoverableDocumentWriter(
          resolver,
          uri,
          "annotations.json",
        ).write(encoded, original)
      }
      annotationsUri = uri
    } catch (error: Exception) {
      createdUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
      throw error
    }
  }

  private fun readRoot(uri: Uri): JSONObject {
    return parseRoot(readRawRoot(uri))
  }

  private fun resolveAnnotationsUri(): Uri? {
    annotationsUri?.let { return it }
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
    ) ?: throw IOException("无法列出项目 src 目录")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == "annotations.json") {
          return DocumentsContract.buildDocumentUriUsingTree(
            sourceDirectoryUri,
            it.getString(idColumn),
          ).also { uri -> annotationsUri = uri }
        }
      }
    }
    return null
  }

  private fun readRawRoot(uri: Uri): String = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
    it.readText()
  } ?: throw IOException("无法读取项目 annotations.json")

  private fun parseRoot(text: String): JSONObject {
    return try {
      JSONObject(text)
    } catch (error: JSONException) {
      throw IOException("项目 annotations.json 格式无效", error)
    }
  }

  private fun overwrite(target: Uri, content: String) {
    val output = resolver.openOutputStream(target, "wt")
      ?: throw IOException("无法写入项目 annotations.json")
    output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
  }

  private fun freehandItems(items: JSONArray): List<Map<String, Any?>> =
    (0 until items.length()).mapNotNull { index ->
      val item = items.optJSONObject(index) ?: return@mapNotNull null
      if (item.optString("type") != "pen" && item.optString("type") != "highlight") {
        return@mapNotNull null
      }
      item.toKotlinMap()
    }
}

private fun JSONObject.toKotlinMap(): Map<String, Any?> =
  keys().asSequence().associateWith { key -> get(key).toKotlinValue() }

private fun Any?.toKotlinValue(): Any? = when (this) {
  JSONObject.NULL -> null
  is JSONObject -> toKotlinMap()
  is JSONArray -> (0 until length()).map { opt(it).toKotlinValue() }
  else -> this
}

private fun Map<String, Any?>.toJsonObject(): JSONObject = JSONObject().also { target ->
  forEach { (key, value) -> target.put(key, value.toJsonValue()) }
}

private fun Any?.toJsonValue(): Any = when (this) {
  null -> JSONObject.NULL
  is Map<*, *> -> JSONObject().also { target ->
    forEach { (key, value) ->
      if (key is String) target.put(key, value.toJsonValue())
    }
  }
  is Iterable<*> -> JSONArray().also { target -> forEach { target.put(it.toJsonValue()) } }
  else -> this
}
