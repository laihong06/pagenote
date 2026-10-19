package io.pagenote.feature.pen

/**
 * Per-page pen storage mirroring desktop annotations.json:
 * `{ document: {page: [items]}, note: {page: [items]} }`.
 *
 * Eraser and undo/redo are scoped to one (side, page) key and never touch
 * other pages. Every mutation validates via [PenValidation] before snapshotting.
 */
class PenPageStore(
  private val maxUndoDepth: Int = PenValidation.UNDO_DEPTH,
) {
  private val pages: MutableMap<PenSide, MutableMap<Int, MutableList<PenStroke>>> = mutableMapOf()
  private val undoStacks: MutableMap<String, ArrayDeque<List<PenStroke>>> = mutableMapOf()
  private val redoStacks: MutableMap<String, ArrayDeque<List<PenStroke>>> = mutableMapOf()

  private fun key(side: PenSide, page: Int) = "${side.key}:$page"

  private fun liveList(side: PenSide, page: Int): MutableList<PenStroke> =
    pages.getOrPut(side) { mutableMapOf() }.getOrPut(page) { mutableListOf() }

  /** Strokes on the given side/page, oldest first. Empty when the page has none. */
  fun getItems(side: PenSide, page: Int): List<PenStroke> {
    require(page >= 1) { "bad page: $page" }
    return pages[side]?.get(page)?.toList() ?: emptyList()
  }

  /** Appends a validated stroke, returns its index on the page. */
  fun addStroke(side: PenSide, page: Int, stroke: PenStroke): Int {
    require(page >= 1) { "bad page: $page" }
    PenValidation.validate(stroke, page)
    val list = liveList(side, page)
    PenValidation.validateItemCount(list.size + 1, side, page)
    snapshot(side, page)
    list.add(stroke)
    return list.lastIndex
  }

  /**
   * Eraser: removes only the stroke at [index] on the given side/page.
   * Returns false when the index is out of range and changes nothing.
   */
  fun eraseAt(side: PenSide, page: Int, index: Int): Boolean {
    require(page >= 1) { "bad page: $page" }
    val list = pages[side]?.get(page) ?: return false
    if (index < 0 || index >= list.size) return false
    snapshot(side, page)
    list.removeAt(index)
    return true
  }

  /** Changes the style of one selected item and records a page-local undo snapshot. */
  fun updateStyle(
    side: PenSide,
    page: Int,
    index: Int,
    color: String? = null,
    width: Double? = null,
  ): Boolean {
    require(page >= 1) { "bad page: $page" }
    val list = pages[side]?.get(page) ?: return false
    val current = list.getOrNull(index) ?: return false
    val updated = current.copy(
      color = color ?: current.color,
      width = width ?: current.width,
    )
    PenValidation.validate(updated, page)
    if (updated == current) return false
    snapshot(side, page)
    list[index] = updated
    return true
  }

  /** Clears only the given side/page. Returns false when already empty. */
  fun clearPage(side: PenSide, page: Int): Boolean {
    require(page >= 1) { "bad page: $page" }
    val list = pages[side]?.get(page) ?: return false
    if (list.isEmpty()) return false
    snapshot(side, page)
    list.clear()
    return true
  }

  fun canUndo(side: PenSide, page: Int): Boolean =
    !undoStacks[key(side, page)].isNullOrEmpty()

  fun canRedo(side: PenSide, page: Int): Boolean =
    !redoStacks[key(side, page)].isNullOrEmpty()

  /** Undoes the last mutation on this side/page only. False when the stack is empty. */
  fun undo(side: PenSide, page: Int): Boolean {
    require(page >= 1) { "bad page: $page" }
    val stackKey = key(side, page)
    val prev = undoStacks[stackKey]?.removeLastOrNull() ?: return false
    redoStacks.getOrPut(stackKey) { ArrayDeque() }.addLast(liveList(side, page).toList())
    liveList(side, page).clear()
    liveList(side, page).addAll(prev)
    return true
  }

  /** Redoes the last undone mutation on this side/page only. */
  fun redo(side: PenSide, page: Int): Boolean {
    require(page >= 1) { "bad page: $page" }
    val stackKey = key(side, page)
    val next = redoStacks[stackKey]?.removeLastOrNull() ?: return false
    undoStacks.getOrPut(stackKey) { ArrayDeque() }.addLast(liveList(side, page).toList())
    liveList(side, page).clear()
    liveList(side, page).addAll(next)
    return true
  }

  /** Serializes to the annotations.json shape with string page keys. */
  fun toAnnotationsMap(): Map<String, Map<String, List<Map<String, Any?>>>> {
    val out: MutableMap<String, Map<String, List<Map<String, Any?>>>> = linkedMapOf()
    for (side in PenSide.values()) {
      val pageMap: MutableMap<String, List<Map<String, Any?>>> = linkedMapOf()
      for (page in pages[side].orEmpty().toSortedMap().keys) {
        pageMap[page.toString()] = liveList(side, page).map { it.toAnnotationsItem() }
      }
      out[side.key] = pageMap
    }
    return out
  }

  /**
   * Replaces store contents from an annotations.json-shaped map.
   * Unknown sides and non-numeric page keys are ignored; invalid items throw.
   */
  fun loadAnnotationsMap(raw: Map<String, Any?>) {
    val loaded: MutableMap<PenSide, MutableMap<Int, MutableList<PenStroke>>> = mutableMapOf()
    for ((sideKey, pagesRaw) in raw) {
      val side = PenSide.fromKey(sideKey) ?: continue
      val pageMap = pagesRaw as? Map<*, *> ?: continue
      for ((pageKey, itemsRaw) in pageMap) {
        val page = when (pageKey) {
          is Number -> pageKey.toInt()
          is String -> pageKey.toIntOrNull() ?: continue
          else -> continue
        }
        if (page < 1) continue
        val itemsList = itemsRaw as? List<*> ?: continue
        val strokes = itemsList.map { parseAnnotationsItem(it) }
        PenValidation.validateItemCount(strokes.size, side, page)
        strokes.forEach { PenValidation.validate(it, page) }
        loaded.getOrPut(side) { mutableMapOf() }[page] = strokes.toMutableList()
      }
    }
    pages.clear()
    undoStacks.clear()
    redoStacks.clear()
    for ((side, pageMap) in loaded) {
      pages[side] = pageMap
    }
  }

  private fun snapshot(side: PenSide, page: Int) {
    val stack = undoStacks.getOrPut(key(side, page)) { ArrayDeque() }
    stack.addLast(liveList(side, page).toList())
    while (stack.size > maxUndoDepth) stack.removeFirst()
    redoStacks[key(side, page)]?.clear()
  }

  private fun parseAnnotationsItem(raw: Any?): PenStroke {
    val map = raw as? Map<*, *> ?: throw IllegalArgumentException("bad item: $raw")
    val type = map["type"] as? String ?: throw IllegalArgumentException("bad item type")
    val color = map["color"] as? String ?: throw IllegalArgumentException("bad item color")
    val width = (map["width"] as? Number)?.toDouble()
      ?: throw IllegalArgumentException("bad item width")
    val pointsRaw = map["points"] as? List<*> ?: throw IllegalArgumentException("bad item points")
    if (pointsRaw.isEmpty()) throw IllegalArgumentException("bad item points")
    val pressuresRaw = map["pressures"] as? List<*>
    val points = pointsRaw.mapIndexed { index, pointRaw ->
      val pair = pointRaw as? List<*> ?: throw IllegalArgumentException("bad point: $pointRaw")
      val x = (pair.getOrNull(0) as? Number)?.toFloat()
        ?: throw IllegalArgumentException("bad point x: $pointRaw")
      val y = (pair.getOrNull(1) as? Number)?.toFloat()
        ?: throw IllegalArgumentException("bad point y: $pointRaw")
      val pressure = (pressuresRaw?.getOrNull(index) as? Number)?.toFloat()
        ?: PenValidation.DEFAULT_PRESSURE
      PenPoint(x, y, pressure)
    }
    val extraFields = map.entries
      .mapNotNull { (key, value) ->
        (key as? String)?.takeIf { it !in PenStroke.CANONICAL_ANNOTATION_FIELDS }?.let { it to value }
      }
      .toMap(linkedMapOf())
    return PenStroke(
      type = type,
      color = color,
      width = width,
      points = points,
      text = map["text"] as? String,
      html = map["html"] as? String,
      url = map["url"] as? String,
      extraFields = extraFields,
    )
  }
}
