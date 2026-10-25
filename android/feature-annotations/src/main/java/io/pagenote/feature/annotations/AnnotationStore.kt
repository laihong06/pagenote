package io.pagenote.feature.annotations

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Mirror of desktop annotations.json: { document: {page: [items]}, note: {page: [items]} }. */
data class AnnotationPage(val side: String, val page: Int, val items: JsonArray)

object AnnotationValidation {
  private val SIDES = setOf("document", "note")
  private val TYPES = setOf(
    "pen", "highlight", "line", "arrow", "rect", "ellipse", "text", "image", "html",
  )
  private val COLOR = Regex("^#[a-fA-F0-9]{6}$")
  private val IMAGE_URL = Regex("^/attachments/[a-f0-9-]{36}\\.(png|jpe?g|gif|webp)$", RegexOption.IGNORE_CASE)

  fun validateSide(side: String) {
    require(SIDES.contains(side)) { "bad side" }
  }

  fun validatePage(page: Int) {
    require(page >= 1) { "bad page" }
  }

  /** Desktop server.mjs validAnnotationItems semantics. */
  fun validateItems(items: JsonArray) {
    require(items.size <= 2000) { "too many annotation items" }
    items.forEach { validateItem(it) }
  }

  private fun validateItem(element: JsonElement) {
    val item = (element as? JsonObject) ?: throw IllegalArgumentException("bad annotation item")
    val type = item["type"]?.toJsonStringOrNull() ?: throw IllegalArgumentException("bad annotation type")
    require(TYPES.contains(type)) { "bad annotation type" }
    val color = item["color"]?.toJsonStringOrNull() ?: throw IllegalArgumentException("bad annotation color")
    require(COLOR.matches(color)) { "bad annotation color" }
    val width = item["width"]?.toJsonDoubleOrNull() ?: throw IllegalArgumentException("bad annotation width")
    require(width.isFinite() && width >= 0.1 && width <= 100.0) { "bad annotation width" }
    // Desktop validAnnotationItems requires points for every item type; pen/highlight
    // additionally accept a rect shorthand (contract regression covers the rect branch).
    if (type == "highlight") {
      val points = item["points"] as? JsonArray
      val rect = item["rect"] as? JsonArray
      if (points != null) {
        validatePoints(points)
      } else if (rect != null) {
        require(rect.size == 4) { "bad annotation rect" }
        rect.forEach { axis ->
          val value = axis.toJsonDoubleOrNull() ?: throw IllegalArgumentException("bad annotation rect")
          require(value.isFinite() && kotlin.math.abs(value) <= 1_000_000.0) { "bad annotation rect" }
        }
      } else {
        throw IllegalArgumentException("bad annotation points")
      }
    } else {
      val points = item["points"] as? JsonArray ?: throw IllegalArgumentException("bad annotation points")
      validatePoints(points)
    }
    if (type == "text") {
      val text = item["text"]?.toJsonStringOrNull() ?: throw IllegalArgumentException("bad annotation text")
      require(text.length <= 10000) { "annotation text too long" }
    }
    if (type == "html") {
      val html = item["html"]?.toJsonStringOrNull() ?: throw IllegalArgumentException("bad annotation html")
      require(html.length <= 100000) { "annotation html too long" }
    }
    if (type == "image") {
      val url = item["url"]?.toJsonStringOrNull() ?: throw IllegalArgumentException("bad annotation image url")
      require(IMAGE_URL.matches(url)) { "bad annotation image url" }
    }
  }

  private fun validatePoints(points: JsonArray) {
    require(points.size in 1..30000) { "bad annotation points" }
    points.forEach { point ->
      val pair = (point as? JsonArray) ?: throw IllegalArgumentException("bad annotation point")
      require(pair.size == 2) { "bad annotation point" }
      pair.forEach { axis ->
        val value = axis.toJsonDoubleOrNull() ?: throw IllegalArgumentException("bad annotation point")
        require(value.isFinite() && kotlin.math.abs(value) <= 1_000_000.0) { "bad annotation point" }
      }
    }
  }

  private fun JsonElement.toJsonStringOrNull(): String? =
    try {
      (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    } catch (_: IllegalArgumentException) {
      null
    }

  private fun JsonElement.toJsonDoubleOrNull(): Double? {
    try {
      val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
      if (primitive.isString) return primitive.content.toDoubleOrNull()
      return primitive.doubleOrNull
    } catch (_: IllegalArgumentException) {
      return null
    }
  }
}

object AnnotationStore {
  private val json = Json { ignoreUnknownKeys = true }

  fun read(file: File): Map<String, Map<Int, JsonArray>> {
    if (!file.exists()) return mapOf("document" to emptyMap(), "note" to emptyMap())
    val root = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    return mapOf(
      "document" to readSide(root["document"]),
      "note" to readSide(root["note"]),
    )
  }

  fun readPage(file: File, side: String, page: Int): JsonArray {
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    return read(file)[side]?.get(page) ?: JsonArray(emptyList())
  }

  /**
   * Full replace of one side/page, mirroring PUT /annotations/:side/:page.
   * Returns true when the file changed, false when content is identical (dedup).
   */
  fun writePage(srcDir: File, side: String, page: Int, items: JsonArray): Boolean {
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    AnnotationValidation.validateItems(items)
    val file = File(srcDir, "annotations.json")
    val current = read(file).toMutableMap()
    val sidePages = current.getOrDefault(side, emptyMap()).toMutableMap()
    if (sidePages[page] == items) return false
    backup(file)
    sidePages[page] = items
    current[side] = sidePages
    atomicWrite(file, serialize(current))
    return true
  }

  /**
   * Delete only one side/page, mirroring "delete annotation removes the item, not note text".
   * Returns true when the file changed; missing file or missing page is a no-op (false).
   */
  fun deletePage(srcDir: File, side: String, page: Int): Boolean {
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    val file = File(srcDir, "annotations.json")
    if (!file.exists()) return false
    val current = read(file).toMutableMap()
    val sidePages = current.getOrDefault(side, emptyMap()).toMutableMap()
    if (!sidePages.containsKey(page)) return false
    backup(file)
    sidePages.remove(page)
    current[side] = sidePages
    atomicWrite(file, serialize(current))
    return true
  }

  /**
   * Rect eraser (port batch 1): removes every item of one side/page whose
   * geometry intersects the axis-aligned rect (document units; corners may
   * be given in any order). Reuses [writePage], so the surviving page keeps
   * its validation, backup and dedup semantics. Returns the removed original
   * indexes, oldest first; empty when nothing intersected or the page is absent.
   */
  fun eraseInRect(
    srcDir: File,
    side: String,
    page: Int,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
  ): List<Int> {
    AnnotationValidation.validateSide(side)
    AnnotationValidation.validatePage(page)
    listOf(left, top, right, bottom).forEach {
      require(it.isFinite()) { "non-finite rect" }
    }
    val file = File(srcDir, "annotations.json")
    val current = readPage(file, side, page)
    if (current.isEmpty()) return emptyList()
    val removed = AnnotationGeometry.itemsIntersectingRect(current, left, top, right, bottom)
    if (removed.isEmpty()) return emptyList()
    val kept = JsonArray(current.filterIndexed { index, _ -> index !in removed.toSet() })
    writePage(srcDir, side, page, kept)
    return removed
  }

  private fun readSide(element: JsonElement?): Map<Int, JsonArray> {
    val side = (element as? JsonObject) ?: return emptyMap()
    val pages = mutableMapOf<Int, JsonArray>()
    side.forEach { (key, value) ->
      val page = key.toIntOrNull() ?: return@forEach
      if (page >= 1 && value is JsonArray) pages[page] = value
    }
    return pages
  }

  private fun serialize(all: Map<String, Map<Int, JsonArray>>): String {
    val root = buildJsonObject {
      listOf("document", "note").forEach { side ->
        val pages = all.getOrDefault(side, emptyMap())
        put(side, buildJsonObject {
          pages.toSortedMap().forEach { (page, items) -> put(page.toString(), items) }
        })
      }
    }
    return Json.encodeToString(JsonObject.serializer(), root)
  }

  private fun backup(file: File) {
    if (!file.exists()) return
    val history = File(file.parentFile, "annotation-history")
    history.mkdirs()
    val backup = File(history, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
    val tmp = File(history, backup.name + ".tmp")
    tmp.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
    if (!tmp.renameTo(backup)) {
      backup.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
      tmp.delete()
    }
  }

  private fun atomicWrite(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + "." + UUID.randomUUID() + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (!tmp.renameTo(file)) {
      file.writeText(text, Charsets.UTF_8)
      tmp.delete()
    }
  }
}
