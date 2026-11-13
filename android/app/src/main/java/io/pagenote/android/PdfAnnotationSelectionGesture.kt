package io.pagenote.android

import android.view.MotionEvent
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal data class PdfAnnotationSelectionGesture(
  val pointerId: Int,
  val sourceIndex: Int,
  val startX: Double,
  val startY: Double,
  val originalItem: JsonObject,
  val originalPage: JsonArray,
  val bounds: PdfAnnotationBounds,
  val resizing: Boolean,
  val moved: Boolean = false,
  val movingPageIndices: Set<Int> = emptySet(),
  val movingPenIndices: Set<Int> = emptySet(),
  val originalPenStrokes: List<PenStroke> = emptyList(),
)

internal data class PdfAnnotationSelectionState(
  val selectedPenIndex: Int?,
  val selectedPenIndices: Set<Int>,
  val selectedAnnotationIndex: Int?,
  val selectedAnnotationIndices: Set<Int>,
)

internal data class PdfAnnotationSelectionGestureEffect(
  val handled: Boolean,
  val gesture: PdfAnnotationSelectionGesture? = null,
  val selection: PdfAnnotationSelectionState? = null,
  val previewPage: JsonArray? = null,
  val previewPenStrokes: List<PenStroke>? = null,
  val commitGesture: PdfAnnotationSelectionGesture? = null,
  val commitPage: JsonArray? = null,
  val restorePage: JsonArray? = null,
  val restorePenStrokes: List<PenStroke>? = null,
)

internal object PdfAnnotationSelectionGestureReducer {
  fun handle(
    event: MotionEvent,
    geometry: PenCanvasGeometry,
    pointerIndex: Int,
    densityScale: Float,
    page: JsonArray,
    penStrokes: List<PenStroke>,
    selectedPens: Set<Int>,
    selectedAnnotations: Set<Int>,
    activeAnnotationIndex: Int?,
    selectionBounds: PdfAnnotationBounds?,
    gesture: PdfAnnotationSelectionGesture?,
  ): PdfAnnotationSelectionGestureEffect {
    if (!geometry.isValid) return PdfAnnotationSelectionGestureEffect(false, gesture)
    if (pointerIndex !in 0 until event.pointerCount && event.actionMasked != MotionEvent.ACTION_CANCEL) {
      return PdfAnnotationSelectionGestureEffect(false, gesture)
    }
    val safePointerIndex = pointerIndex.takeIf { it in 0 until event.pointerCount } ?: 0
    val point = if (event.pointerCount > 0) {
      geometry.toDocumentPoint(
        event.getX(safePointerIndex),
        event.getY(safePointerIndex),
        pressure = null,
        clampToPage = false,
      )
    } else null
    val x = point?.x?.toDouble() ?: 0.0
    val y = point?.y?.toDouble() ?: 0.0
    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> down(
        event.getPointerId(safePointerIndex), x, y, geometry, densityScale, page, penStrokes, selectedPens, selectedAnnotations,
        activeAnnotationIndex, selectionBounds,
      )
      MotionEvent.ACTION_MOVE -> move(x, y, gesture)
      MotionEvent.ACTION_UP -> commit(page, gesture)
      MotionEvent.ACTION_POINTER_UP -> {
        val active = gesture ?: return PdfAnnotationSelectionGestureEffect(false)
        if (event.getPointerId(event.actionIndex) != active.pointerId) {
          PdfAnnotationSelectionGestureEffect(handled = true, gesture = active)
        } else {
          commit(page, active)
        }
      }
      MotionEvent.ACTION_CANCEL -> PdfAnnotationSelectionGestureEffect(
        handled = gesture != null,
        restorePage = gesture?.originalPage?.takeIf { gesture.moved },
        restorePenStrokes = gesture?.originalPenStrokes?.takeIf { gesture.moved && gesture.movingPageIndices.isNotEmpty() },
      )
      else -> PdfAnnotationSelectionGestureEffect(gesture != null, gesture)
    }
  }

  private fun down(
    pointerId: Int,
    x: Double,
    y: Double,
    geometry: PenCanvasGeometry,
    densityScale: Float,
    page: JsonArray,
    penStrokes: List<PenStroke>,
    selectedPens: Set<Int>,
    selectedAnnotations: Set<Int>,
    activeAnnotationIndex: Int?,
    selectionBounds: PdfAnnotationBounds?,
  ): PdfAnnotationSelectionGestureEffect {
    val groupSize = selectedPens.size + selectedAnnotations.size
    val groupTolerance = geometry.documentUnitsForPixels(18f * densityScale).toDouble()
    val insideGroup = groupSize > 1 && selectionBounds?.let { bounds ->
      x >= bounds.left - groupTolerance && x <= bounds.right + groupTolerance &&
        y >= bounds.top - groupTolerance && y <= bounds.bottom + groupTolerance
    } == true
    if (insideGroup) {
      val movingPageIndices = selectedPageIndices(page, selectedPens, selectedAnnotations)
      if (movingPageIndices.size == groupSize) {
        val bounds = checkNotNull(selectionBounds)
        return PdfAnnotationSelectionGestureEffect(
          handled = true,
          gesture = PdfAnnotationSelectionGesture(
            pointerId = pointerId,
            sourceIndex = -1,
            startX = x,
            startY = y,
            originalItem = JsonObject(emptyMap()),
            originalPage = page,
            bounds = bounds,
            resizing = false,
            movingPageIndices = movingPageIndices,
            movingPenIndices = selectedPens,
            originalPenStrokes = penStrokes,
          ),
        )
      }
    }

    val activeBounds = activeAnnotationIndex?.let { index ->
      (page.getOrNull(index) as? JsonObject)?.let(PdfAnnotationClipboard::bounds)
    }
    val handleRadius = 24.0
    val resize = activeBounds?.let { bounds ->
      kotlin.math.hypot(x - bounds.right - 5.0, y - bounds.bottom - 5.0) <= handleRadius
    } == true
    val tolerance = geometry.documentUnitsForPixels(18f * densityScale).toDouble().coerceAtLeast(0.1)
    val hit = if (resize) activeAnnotationIndex else {
      PdfAnnotationClipboard.hitTest(page, x, y, tolerance) ?: activeAnnotationIndex?.takeIf { index ->
        activeBounds?.let { bounds -> x in bounds.left..bounds.right && y in bounds.top..bounds.bottom } == true &&
          index in page.indices
      }
    }
    if (hit == null) {
      return PdfAnnotationSelectionGestureEffect(
        handled = false,
        selection = PdfAnnotationSelectionState(null, selectedPens, null, emptySet()),
      )
    }
    val item = page.getOrNull(hit) as? JsonObject ?: return PdfAnnotationSelectionGestureEffect(false)
    val bounds = PdfAnnotationClipboard.bounds(item) ?: return PdfAnnotationSelectionGestureEffect(false)
    return PdfAnnotationSelectionGestureEffect(
      handled = true,
      gesture = PdfAnnotationSelectionGesture(
        pointerId = pointerId,
        sourceIndex = hit,
        startX = x,
        startY = y,
        originalItem = item,
        originalPage = page,
        bounds = bounds,
        resizing = resize && item["type"]?.let { it.jsonPrimitive.content } != "text",
      ),
      selection = PdfAnnotationSelectionState(null, emptySet(), hit, setOf(hit)),
    )
  }

  private fun commit(page: JsonArray, gesture: PdfAnnotationSelectionGesture?): PdfAnnotationSelectionGestureEffect =
    PdfAnnotationSelectionGestureEffect(
      handled = gesture != null,
      commitGesture = gesture?.takeIf { it.moved },
      commitPage = page.takeIf { gesture?.moved == true },
    )

  private fun move(
    x: Double,
    y: Double,
    gesture: PdfAnnotationSelectionGesture?,
  ): PdfAnnotationSelectionGestureEffect {
    val active = gesture ?: return PdfAnnotationSelectionGestureEffect(false)
    if (kotlin.math.hypot(x - active.startX, y - active.startY) <= 3.0) {
      return PdfAnnotationSelectionGestureEffect(true, active)
    }
    val dx = x - active.startX
    val dy = y - active.startY
    if (active.movingPageIndices.isNotEmpty()) {
      val movedPage = translateMixedSelectionPage(active.originalPage, active.movingPageIndices, dx, dy)
        ?: return PdfAnnotationSelectionGestureEffect(true, active)
      val movedStrokes = active.originalPenStrokes.mapIndexed { index, stroke ->
        if (index !in active.movingPenIndices) stroke else stroke.copy(
          points = stroke.points.map { it.copy(x = (it.x + dx).toFloat(), y = (it.y + dy).toFloat()) },
        )
      }
      return PdfAnnotationSelectionGestureEffect(
        handled = true,
        gesture = active.copy(moved = true),
        previewPage = movedPage,
        previewPenStrokes = movedStrokes,
      )
    }
    val updated = if (active.resizing) {
      PdfAnnotationClipboard.resize(active.originalItem, active.bounds, x, y)
    } else {
      PdfAnnotationClipboard.translate(active.originalItem, dx, dy)
    } ?: return PdfAnnotationSelectionGestureEffect(true, active)
    val updatedPage = active.originalPage.toMutableList().apply { this[active.sourceIndex] = updated }
    return PdfAnnotationSelectionGestureEffect(
      handled = true,
      gesture = active.copy(moved = true),
      previewPage = JsonArray(updatedPage),
    )
  }

  private fun selectedPageIndices(
    page: JsonArray,
    selectedPens: Set<Int>,
    selectedAnnotations: Set<Int>,
  ): Set<Int> {
    val pageIndices = linkedSetOf<Int>()
    var penOrdinal = 0
    page.forEachIndexed { pageIndex, element ->
      val type = (element as? JsonObject)?.get("type")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
      if (type == "pen" || type == "highlight") {
        if (penOrdinal in selectedPens) pageIndices += pageIndex
        penOrdinal++
      }
    }
    pageIndices += selectedAnnotations
    return pageIndices.filterTo(linkedSetOf()) { it in page.indices }
  }
}

private fun translateMixedSelectionItem(item: JsonElement, dx: Double, dy: Double): JsonElement? {
  val source = item as? JsonObject ?: return null
  if (!dx.isFinite() || !dy.isFinite()) return null
  val points = runCatching { source["points"]?.jsonArray }.getOrNull() ?: return null
  if (points.isEmpty()) return null
  val translatedPoints = ArrayList<JsonElement>(points.size)
  points.forEach { rawPoint ->
    val coordinates = runCatching { rawPoint.jsonArray }.getOrNull() ?: return null
    if (coordinates.size < 2) return null
    val x = runCatching { coordinates[0].jsonPrimitive.doubleOrNull }.getOrNull() ?: return null
    val y = runCatching { coordinates[1].jsonPrimitive.doubleOrNull }.getOrNull() ?: return null
    val movedX = x + dx
    val movedY = y + dy
    if (!movedX.isFinite() || !movedY.isFinite() || kotlin.math.abs(movedX) > 1_000_000.0 || kotlin.math.abs(movedY) > 1_000_000.0) return null
    translatedPoints.add(JsonArray(listOf(JsonPrimitive(movedX), JsonPrimitive(movedY)) + coordinates.drop(2)))
  }
  return JsonObject(source + ("points" to JsonArray(translatedPoints)))
}

private fun translateMixedSelectionPage(page: JsonArray, selectedIndices: Set<Int>, dx: Double, dy: Double): JsonArray? {
  if (selectedIndices.isEmpty() || selectedIndices.any { it !in page.indices }) return null
  val moved = page.mapIndexed { index, item ->
    if (index in selectedIndices) translateMixedSelectionItem(item, dx, dy) ?: return null else item
  }
  return JsonArray(moved)
}
