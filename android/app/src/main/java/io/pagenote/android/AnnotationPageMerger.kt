package io.pagenote.android

import io.pagenote.feature.pen.PenStroke

object AnnotationPageMerger {
  private val freehandTypes = setOf("pen", "highlight")

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
