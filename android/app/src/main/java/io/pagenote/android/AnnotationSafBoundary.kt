package io.pagenote.android

import kotlinx.serialization.json.JsonArray

/**
 * Pure helpers for the SAF annotation boundary: generic page replace keeps sibling pages
 * and unknown root fields; missing file + empty write stays a no-op.
 */
object AnnotationSafBoundary {
  fun replacePage(
    root: Map<String, Any?>,
    side: String,
    page: Int,
    items: List<Map<String, Any?>>,
  ): Map<String, Any?> {
    require(side == "document" || side == "note") { "bad annotation side: $side" }
    require(page >= 1) { "bad page: $page" }
    val updated = root.toMutableMap()
    val sidePages = (root[side] as? Map<*, *>)
      .orEmpty()
      .entries
      .mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
      .toMap(linkedMapOf())
      .toMutableMap()
    sidePages[page.toString()] = items
    updated[side] = sidePages
    return updated
  }

  fun removePage(root: Map<String, Any?>, side: String, page: Int): Map<String, Any?> {
    require(side == "document" || side == "note") { "bad annotation side: $side" }
    require(page >= 1) { "bad page: $page" }
    val updated = root.toMutableMap()
    val sidePages = (root[side] as? Map<*, *>)
      .orEmpty()
      .entries
      .mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
      .toMap(linkedMapOf())
      .toMutableMap()
    sidePages.remove(page.toString())
    updated[side] = sidePages
    return updated
  }

  fun shouldCreateFile(hasFile: Boolean, items: JsonArray): Boolean =
    hasFile || items.isNotEmpty()
}
