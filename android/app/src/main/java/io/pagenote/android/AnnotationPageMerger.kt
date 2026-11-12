package io.pagenote.android

import io.pagenote.feature.pen.PenStroke
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenSide
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

  /** Native annotation edits keep ink out of their snapshots so separate ink edits do not reset this history. */
  fun annotationHistorySnapshot(page: JsonArray): JsonArray = JsonArray(page.filterNot(::isFreehand))

  fun sameAnnotationLayer(left: JsonArray, right: JsonArray): Boolean =
    jsonListsEqual(annotationHistorySnapshot(left), annotationHistorySnapshot(right))

  fun samePageContent(left: JsonArray, right: JsonArray): Boolean = jsonListsEqual(left, right)

  /** Rebase annotation-only undo/redo onto latest ink; reject changed annotations. */
  fun rebaseAnnotationHistory(latest: JsonArray, expected: JsonArray, restored: JsonArray): JsonArray? {
    if (!isAnnotationOnlyHistory(expected, restored)) return null
    if (!jsonListsEqual(latest.filterNot(::isFreehand), expected.filterNot(::isFreehand))) return null
    if (jsonListsEqual(latest, expected)) return restored
    if (jsonListsEqual(expected, restored)) return latest
    val oldAnnotations = expected.filterNot(::isFreehand)
    val newAnnotations = restored.filterNot(::isFreehand)
    var prefix = 0
    while (prefix < oldAnnotations.size && prefix < newAnnotations.size &&
      jsonEqual(oldAnnotations[prefix], newAnnotations[prefix])) prefix++
    var suffix = 0
    while (suffix < oldAnnotations.size - prefix && suffix < newAnnotations.size - prefix &&
      jsonEqual(oldAnnotations[oldAnnotations.lastIndex - suffix], newAnnotations[newAnnotations.lastIndex - suffix])) suffix++
    val oldEnd = oldAnnotations.size - suffix
    val replacements = newAnnotations.subList(prefix, newAnnotations.size - suffix)
    val removedCount = oldEnd - prefix
    val changedOriginals = oldAnnotations.subList(prefix, oldEnd)
    if (changedOriginals.any { target -> oldAnnotations.count { jsonEqual(it, target) } > 1 }) return null
    if (removedCount == 0 && replacements.isNotEmpty() && prefix < oldAnnotations.size) {
      val anchors = listOfNotNull(oldAnnotations.getOrNull(prefix - 1), oldAnnotations.getOrNull(prefix))
      if (anchors.any { anchor -> oldAnnotations.count { jsonEqual(it, anchor) } > 1 }) return null
    }
    fun applyChange(page: JsonArray): JsonArray {
      val result = mutableListOf<JsonElement>()
      var ordinal = 0
      var inserted = false
      page.forEach { item ->
        if (isFreehand(item)) result.add(item)
        else {
          if (removedCount == 0 && ordinal == prefix && !inserted) {
            result.addAll(replacements)
            inserted = true
          }
          if (ordinal in prefix until oldEnd) {
            val replacementIndex = ordinal - prefix
            replacements.getOrNull(replacementIndex)?.let(result::add)
            if (ordinal == oldEnd - 1) {
              if (replacements.size > removedCount) result.addAll(replacements.drop(removedCount))
              inserted = true
            }
          } else result.add(item)
          ordinal++
        }
      }
      if (!inserted) result.addAll(replacements)
      return JsonArray(result)
    }
    // If the proposed splice cannot reproduce the exact intended page, the
    // insertion/removal anchor is ambiguous and must not be guessed.
    if (!jsonListsEqual(applyChange(expected), restored)) return null
    return applyChange(latest)
  }

  /** Translate a selected annotation's index after preserving latest freehand items. */
  fun remapAnnotationSelection(original: JsonArray, selectedIndex: Int?, rebased: JsonArray): Int? {
    val index = selectedIndex ?: return null
    val item = original.getOrNull(index) ?: return null
    if (isFreehand(item) || !jsonListsEqual(original.filterNot(::isFreehand), rebased.filterNot(::isFreehand))) return null
    if (original.count { !isFreehand(it) && jsonEqual(item, it) } > 1 && !jsonListsEqual(original, rebased)) return null
    val ordinal = original.take(index).count { !isFreehand(it) }
    return rebased.indices.filter { !isFreehand(rebased[it]) }.getOrNull(ordinal)
  }

  /** Resolve the same unchanged annotation after unrelated items move or change. */
  fun findUniqueAnnotation(original: JsonArray, selectedIndex: Int?, latest: JsonArray): Int? {
    val index = selectedIndex ?: return null
    val selected = original.getOrNull(index) as? JsonObject ?: return null
    if (isFreehand(selected)) return null
    val originalMatches = original.count { it is JsonObject && !isFreehand(it) && jsonEqual(selected, it) }
    if (originalMatches > 1 && !jsonListsEqual(original, latest)) return null
    val matches = latest.indices.filter { latest[it] is JsonObject && !isFreehand(latest[it]) && jsonEqual(selected, latest[it]) }
    return when {
      matches.size == 1 -> matches.single()
      index in matches && jsonListsEqual(original, latest) -> index
      else -> null
    }
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
    if (currentAnnotations.count { jsonEqual(it, selected) } > 1) return null
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

  private fun jsonEqual(left: JsonElement, right: JsonElement): Boolean {
    if (left === right) return true
    return when {
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
  }

  fun replaceFreehandItems(
    existingItems: List<Map<String, Any?>>,
    strokes: List<PenStroke>,
  ): List<Map<String, Any?>> {
    // Append and suffix removal can retain each existing item's exact layer and
    // original JSON. Other edits lack stable IDs, so use the existing fallback.
    val existingInk = existingItems.filter { it["type"] in freehandTypes }
    val parsedInk = runCatching {
      PenPageStore().apply {
        loadAnnotationsMap(mapOf(PenSide.DOCUMENT.key to mapOf("1" to existingInk)))
      }.getItems(PenSide.DOCUMENT, 1)
    }.getOrNull()
    val shared = minOf(parsedInk?.size ?: -1, strokes.size)
    if (parsedInk != null && parsedInk.take(shared) == strokes.take(shared)) {
      var ordinal = 0
      val retained = existingItems.filter { item ->
        item["type"] !in freehandTypes || ordinal++ < strokes.size
      }
      return retained + strokes.drop(parsedInk.size).map(PenStroke::toAnnotationsItem)
    }
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
