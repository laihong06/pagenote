package io.pagenote.android

import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

object AnnotationPageMerger {
  private val freehandTypes = setOf("pen", "highlight")

  private fun isFreehand(item: JsonElement): Boolean {
    val type = (item as? JsonObject)?.get("type") as? JsonPrimitive ?: return false
    return type.isString && type.content in freehandTypes
  }

  /** True only when this history entry does not itself change freehand ink. */
  fun isAnnotationOnlyHistory(expected: JsonArray, restored: JsonArray): Boolean =
    jsonListsEqual(expected.filter(::isFreehand), restored.filter(::isFreehand))

  /** Rebase annotation-only undo/redo onto latest ink; reject changed annotations. */
  fun rebaseAnnotationHistory(latest: JsonArray, expected: JsonArray, restored: JsonArray): JsonArray? {
    if (!isAnnotationOnlyHistory(expected, restored)) return null
    if (!jsonListsEqual(latest.filterNot(::isFreehand), expected.filterNot(::isFreehand))) return null
    val replacements = restored.filterNot(::isFreehand).iterator()
    val result = mutableListOf<JsonElement>()
    latest.forEach { item ->
      if (isFreehand(item)) result.add(item)
      else if (replacements.hasNext()) result.add(replacements.next())
    }
    while (replacements.hasNext()) result.add(replacements.next())
    return JsonArray(result)
  }

  /**
   * Prepare an all-or-nothing mixed history restore. The entire ordered page
   * must still match the snapshot from which the history action starts.
   * This does not write the page or update the live pen model.
   */
  fun rebaseMixedHistory(latest: JsonArray, expected: JsonArray, restored: JsonArray): JsonArray? {
    if (!jsonListsEqual(latest, expected)) return null
    return restored
  }

  /** Keep selection only when the same annotation is unambiguous across a history edit. */
  fun rebaseHistorySelection(
    visible: JsonArray,
    selectedIndex: Int?,
    expected: JsonArray,
    restored: JsonArray,
    committed: JsonArray,
  ): Int? {
    val index = selectedIndex ?: return null
    val selected = visible.getOrNull(index) ?: return null
    if (isFreehand(selected)) return null
    val currentAnnotations = visible.filterNot(::isFreehand)
    val beforeAnnotations = expected.filterNot(::isFreehand)
    val afterAnnotations = restored.filterNot(::isFreehand)
    if (!jsonListsEqual(currentAnnotations, beforeAnnotations) ||
      beforeAnnotations.size != afterAnnotations.size ||
      !jsonListsEqual(committed.filterNot(::isFreehand), afterAnnotations)) return null
    val ordinal = visible.take(index).count { !isFreehand(it) }
    if (ordinal !in beforeAnnotations.indices || beforeAnnotations.indices.any {
        it != ordinal && !jsonEqual(beforeAnnotations[it], afterAnnotations[it])
      }) return null
    val beforeType = (beforeAnnotations[ordinal] as? JsonObject)?.get("type")
    val afterType = (afterAnnotations[ordinal] as? JsonObject)?.get("type")
    if (beforeType == null || beforeType != afterType) return null
    return committed.indices.filter { !isFreehand(committed[it]) }.getOrNull(ordinal)
  }

  private fun jsonListsEqual(left: List<JsonElement>, right: List<JsonElement>): Boolean =
    left.size == right.size && left.indices.all { jsonEqual(left[it], right[it]) }

  private fun jsonEqual(left: JsonElement, right: JsonElement): Boolean = when {
    left is JsonObject && right is JsonObject ->
      left.keys == right.keys && left.keys.all { jsonEqual(left.getValue(it), right.getValue(it)) }
    left is JsonArray && right is JsonArray -> jsonListsEqual(left, right)
    left is JsonNull || right is JsonNull -> left is JsonNull && right is JsonNull
    left is JsonPrimitive && right is JsonPrimitive -> {
      if (left.isString || right.isString) left.isString == right.isString && left.content == right.content
      else {
        val leftNumber = left.content.toBigDecimalOrNull()
        val rightNumber = right.content.toBigDecimalOrNull()
        if (leftNumber != null && rightNumber != null) leftNumber.compareTo(rightNumber) == 0
        else left.content == right.content
      }
    }
    else -> false
  }

  fun replaceFreehandItems(
    existingItems: List<Map<String, Any?>>,
    strokes: List<PenStroke>,
  ): List<Map<String, Any?>> {
    val retained = existingItems.filterNot { it["type"] in freehandTypes }
    return retained + strokes.map(PenStroke::toAnnotationsItem)
  }

  fun replacePage(
    root: Map<String, Any?>,
    side: String,
    page: Int,
    strokes: List<PenStroke>,
  ): Map<String, Any?> {
    require(side == "document" || side == "note") { "bad annotation side: $side" }
    require(page >= 1) { "bad page: $page" }
    val updatedRoot = root.toMutableMap()
    val sidePages = (root[side] as? Map<*, *>)
      .orEmpty()
      .entries
      .mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
      .toMap(linkedMapOf())
      .toMutableMap()
    val currentItems = (sidePages[page.toString()] as? List<*>)
      .orEmpty()
      .mapNotNull { it as? Map<*, *> }
      .mapNotNull { item ->
        item.entries.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
          .toMap(linkedMapOf())
      }
    sidePages[page.toString()] = replaceFreehandItems(currentItems, strokes)
    updatedRoot[side] = sidePages
    return updatedRoot
  }
}
