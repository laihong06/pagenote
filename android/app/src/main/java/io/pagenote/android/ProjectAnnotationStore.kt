package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.feature.annotations.AnnotationValidation
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ProjectAnnotationStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
  annotationsUri: Uri?,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private var annotationsUri: Uri? = annotationsUri

  @Volatile
  internal var lastRetainedRecoveryCopyName: String? = null
    private set

  fun readPage(side: String, page: Int): JsonArray {
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    val uri = resolveAnnotationsUri() ?: return JsonArray(emptyList())
    val root = readRoot(uri)
    val sideObj = root[side]
    if (sideObj !is JsonObject) return JsonArray(emptyList())
    val items = sideObj[page.toString()]
    return if (items is JsonArray) items else JsonArray(emptyList())
  }

  fun writePage(side: String, page: Int, items: JsonArray): Boolean {
    lastRetainedRecoveryCopyName = null
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    AnnotationValidation.validateItems(items)
    var uri = resolveAnnotationsUri()
    if (uri == null && !AnnotationSafBoundary.shouldCreateFile(false, items)) return false
    var createdUri: Uri? = null
    try {
      if (uri == null) {
        val created = DocumentsContract.createDocument(
          resolver, sourceDirectoryUri, "application/json", "annotations.json",
        ) ?: throw IOException("无法在项目 src 目录创建 annotations.json")
        uri = created
        createdUri = created
      }
      val target = checkNotNull(uri)
      val original = if (createdUri == null) readRawRoot(target) else null
      val current = original?.let(::parseKotlinRoot) ?: emptyRoot()
      val sidePages = current[side]
      val currentItems = if (sidePages is Map<*, *>) sidePages[page.toString()] as? List<*> else null
      if (currentItems != null) {
        val currentJson = JsonArray(currentItems.map { it.toJsonElement() })
        if (currentJson == items) return false
      }
      val updated = AnnotationSafBoundary.replacePage(
        current, side, page, items.map { (it as JsonObject).toKotlinMap() },
      )
      val encoded = updated.toJsonObject().toString()
      if (original == null) {
        overwrite(target, encoded)
      } else {
        lastRetainedRecoveryCopyName = SafRecoverableDocumentWriter(
          resolver,
          target,
          "annotations.json",
        ).write(encoded, original)
      }
      annotationsUri = target
      return true
    } catch (error: Exception) {
      val created = createdUri
      if (created != null) runCatching { DocumentsContract.deleteDocument(resolver, created) }
      throw error
    }
  }

  fun deletePage(side: String, page: Int): Boolean {
    lastRetainedRecoveryCopyName = null
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    val uri = resolveAnnotationsUri() ?: return false
    val original = readRawRoot(uri)
    val current = parseKotlinRoot(original)
    val sidePages = current[side]
    if (sidePages !is Map<*, *> || !sidePages.containsKey(page.toString())) return false
    val updated = AnnotationSafBoundary.removePage(current, side, page)
    lastRetainedRecoveryCopyName = SafRecoverableDocumentWriter(
      resolver,
      uri,
      "annotations.json",
    ).write(updated.toJsonObject().toString(), original)
    return true
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

  private fun emptyRoot(): Map<String, Any?> {
    val doc = linkedMapOf<String, Any?>()
    val note = linkedMapOf<String, Any?>()
    val root = linkedMapOf<String, Any?>()
    root.put("document", doc)
    root.put("note", note)
    return root
  }

  private fun readRoot(uri: Uri): JsonObject {
    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("无法读取项目 annotations.json")
    try {
      return json.parseToJsonElement(text).jsonObject
    } catch (error: IllegalArgumentException) {
      throw IOException("项目 annotations.json 格式无效", error)
    }
  }

  private fun readRawRoot(uri: Uri): String = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
    it.readText()
  } ?: throw IOException("无法读取项目 annotations.json")

  private fun parseKotlinRoot(text: String): Map<String, Any?> {
    try {
      return JSONObject(text).toKotlinMap()
    } catch (error: JSONException) {
      throw IOException("项目 annotations.json 格式无效", error)
    }
  }

  private fun overwrite(target: Uri, content: String) {
    val output = resolver.openOutputStream(target, "wt")
      ?: throw IOException("无法写入项目 annotations.json")
    output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
  }

  private fun JSONObject.toKotlinMap(): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    val iter = keys()
    while (iter.hasNext()) {
      val key = iter.next()
      out.put(key, get(key).toKotlinValue())
    }
    return out
  }

  private fun Any?.toKotlinValue(): Any? {
    if (this == null || this === JSONObject.NULL) return null
    if (this is JSONObject) return toKotlinMap()
    if (this is JSONArray) {
      val out = mutableListOf<Any?>()
      var i = 0
      while (i < length()) {
        out.add(opt(i).toKotlinValue())
        i += 1
      }
      return out
    }
    return this
  }

  private fun Map<String, Any?>.toJsonObject(): JSONObject {
    val target = JSONObject()
    for ((key, value) in this) {
      target.put(key, value.toJsonValue())
    }
    return target
  }

  private fun Any?.toJsonValue(): Any {
    if (this == null) return JSONObject.NULL
    if (this is Map<*, *>) {
      val target = JSONObject()
      for ((key, value) in this) {
        if (key is String) target.put(key, value.toJsonValue())
      }
      return target
    }
    if (this is Iterable<*>) {
      val target = JSONArray()
      for (item in this) target.put(item.toJsonValue())
      return target
    }
    return this
  }

  private fun JsonObject.toKotlinMap(): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    for ((key, value) in this) {
      out.put(key, value.toKotlinValue())
    }
    return out
  }

  private fun kotlinx.serialization.json.JsonElement.toKotlinValue(): Any? {
    if (this is JsonObject) return toKotlinMap()
    if (this is JsonArray) return map { it.toKotlinValue() }
    val prim = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (prim.isString) return prim.content
    prim.content.toLongOrNull()?.let { return it }
    prim.content.toDoubleOrNull()?.let { return it }
    if (prim.content == "true") return true
    if (prim.content == "false") return false
    if (prim.content == "null") return null
    return prim.content
  }

  private fun Any?.toJsonElement(): kotlinx.serialization.json.JsonElement {
    if (this == null) return kotlinx.serialization.json.JsonNull
    if (this is Map<*, *>) {
      val pairs = mutableListOf<Pair<String, kotlinx.serialization.json.JsonElement>>()
      for ((key, value) in this) {
        if (key is String) pairs.add(Pair(key, value.toJsonElement()))
      }
      return JsonObject(mapOf(*pairs.toTypedArray()))
    }
    if (this is Iterable<*>) return JsonArray(map { it.toJsonElement() })
    if (this is Number) {
      try {
        return kotlinx.serialization.json.JsonPrimitive(this as Number)
      } catch (_: Exception) {
        return kotlinx.serialization.json.JsonPrimitive(toString())
      }
    }
    if (this is Boolean) return kotlinx.serialization.json.JsonPrimitive(this as Boolean)
    return kotlinx.serialization.json.JsonPrimitive(toString())
  }
}
