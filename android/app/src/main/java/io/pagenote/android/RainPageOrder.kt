package io.pagenote.android

import org.json.JSONArray
import org.json.JSONObject

internal data class RainPageWrite(val pageNumber: Int, val added: Boolean, val pageMap: Map<Int, Int> = emptyMap())
internal data class RainPageSlot(val oldPage: Int, val entry: JSONObject?)

/** One permutation is shared by the PDF and every page-linked sidecar. */
internal class RainPageOrder(manifest: JSONObject, private val pageCount: Int, incoming: JSONObject? = null) {
  val slots: List<RainPageSlot>
  val oldToNew: Map<Int, Int>
  val pages = JSONArray()
  val sections = JSONArray()

  init {
    require(!manifest.has("pages") || manifest.optJSONArray("pages") != null) { "雨课堂来源记录无效，已停止插页" }
    val original = manifest.optJSONArray("pages") ?: JSONArray()
    val entries = (0 until original.length()).map { index ->
      JSONObject(original.getJSONObject(index).toString()).apply {
        for (key in listOf("courseId", "classroomId", "lessonId")) {
          if (!has(key)) put(key, manifest.optString(key))
        }
        require(optInt("projectPage") in 1..pageCount && optInt("sourceIndex", -1) >= 0 &&
          optString("presentationId").isNotBlank()) { "雨课堂来源页码无效，已停止插页" }
      }
    }
    require(entries.map { it.getInt("projectPage") }.distinct().size == entries.size) { "雨课堂来源页码重复，已停止插页" }
    val captured = entries.map { RainPageSlot(it.getInt("projectPage"), it) }.toMutableList()
    if (incoming != null) {
      require(incoming.optInt("sourceIndex", -1) >= 0 && incoming.optString("presentationId").isNotBlank()) { "雨课堂页面索引无效" }
      captured.add(RainPageSlot(pageCount + 1, incoming))
    }
    val groups = captured.groupBy { it.entry!!.getString("classroomId") to it.entry.getString("presentationId") }
    val mapped = entries.map { it.getInt("projectPage") }.toSet()
    val leading = (1..pageCount).filterNot { it in mapped }.map { RainPageSlot(it, null) }
    slots = leading + groups.values.flatMap { group -> group.sortedBy { it.entry!!.getInt("sourceIndex") } }
    oldToNew = slots.mapIndexed { index, slot -> slot.oldPage to index + 1 }.toMap()
    for ((index, slot) in slots.withIndex()) slot.entry?.put("projectPage", index + 1)?.let(pages::put)
    for ((index, group) in groups.values.withIndex()) {
      val first = group.first().entry!!
      val positions = group.map { oldToNew.getValue(it.oldPage) }
      sections.put(JSONObject().put("classroomId", first.getString("classroomId"))
        .put("presentationId", first.getString("presentationId"))
        .put("title", "PPT ${index + 1}").put("startPage", positions.min()).put("endPage", positions.max()))
    }
  }

  fun remapSidecar(name: String, text: String): String = when (name) {
    "annotations.json" -> JSONObject(text).apply {
      for (side in listOf("document", "note")) optJSONObject(side)?.let { put(side, remapPageMap(it)) }
    }.toString()
    "ai-handoffs.json" -> remapPageMap(JSONObject(text)).toString()
    "ai-reviews.json" -> JSONArray(text).apply {
      for (index in 0 until length()) {
        val review = getJSONObject(index)
        if (review.has("page")) review.put("page", mappedPage(review.getInt("page")))
        review.optJSONObject("anchor")?.let { anchor ->
          if (anchor.has("page")) anchor.put("page", mappedPage(anchor.getInt("page")))
        }
      }
    }.toString()
    else -> error("Unknown page sidecar")
  }

  private fun mappedPage(page: Int): Int {
    require(page in 1..pageCount) { "项目批注页码超出 PDF，已停止插页" }
    return oldToNew.getValue(page)
  }

  private fun remapPageMap(root: JSONObject): JSONObject = JSONObject().apply {
    for (key in root.keys().asSequence().toList()) {
      val page = key.toIntOrNull()
      put(if (page != null) mappedPage(page).toString() else key, root.get(key))
    }
  }
}
