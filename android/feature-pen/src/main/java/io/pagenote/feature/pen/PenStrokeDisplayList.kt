package io.pagenote.feature.pen

import android.graphics.Canvas
import android.graphics.RenderNode
import kotlin.math.ceil
import kotlin.math.floor

/** Hardware ink commands in document coordinates; zoom and pan only change the canvas matrix. */
internal class PenStrokeDisplayList {
  private data class Entry(val stroke: PenStroke, val node: RenderNode, val bounds: IntArray)
  private var entries = emptyList<Entry>()
  private var root: RenderNode? = null
  private var recorded = emptyList<PenStroke>()
  private val documentGeometry = PenCanvasGeometry(1000f, 1000f)

  fun draw(canvas: Canvas, strokes: List<PenStroke>, geometry: PenCanvasGeometry) {
    if (root == null || recorded !== strokes) record(strokes)
    val node = root ?: return
    val saved = canvas.save()
    canvas.translate(geometry.pageOriginX, geometry.pageOriginY)
    canvas.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
    canvas.drawRenderNode(node)
    canvas.restoreToCount(saved)
  }

  private fun record(strokes: List<PenStroke>) {
    val previous = entries
    val next = strokes.mapIndexed { index, stroke ->
      previous.getOrNull(index)?.takeIf { it.stroke === stroke } ?: recordStroke(stroke)
    }
    val retainedNodes = next.mapTo(HashSet()) { it.node }
    previous.forEach { old -> if (old.node !in retainedNodes) old.node.discardDisplayList() }
    entries = next
    val node = root ?: RenderNode("pagenote-ink").apply { setClipToBounds(false) }.also { root = it }
    val left = next.minOfOrNull { it.bounds[0] } ?: 0
    val top = next.minOfOrNull { it.bounds[1] } ?: 0
    val right = (next.maxOfOrNull { it.bounds[2] } ?: 1).coerceAtLeast(left + 1)
    val bottom = (next.maxOfOrNull { it.bounds[3] } ?: 1).coerceAtLeast(top + 1)
    node.setPosition(left, top, right, bottom)
    val recording = node.beginRecording(right - left, bottom - top)
    try {
      recording.translate(-left.toFloat(), -top.toFloat())
      next.forEach { recording.drawRenderNode(it.node) }
    } finally {
      node.endRecording()
    }
    recorded = strokes
  }

  private fun recordStroke(stroke: PenStroke): Entry {
    val points = stroke.points
    val padding = maxOf(42f, stroke.width.toFloat() * 4f)
    val left = floor((points.minOfOrNull { it.x } ?: 0f) - padding).toInt()
    val top = floor((points.minOfOrNull { it.y } ?: 0f) - padding).toInt()
    val right = ceil((points.maxOfOrNull { it.x } ?: 0f) + padding).toInt().coerceAtLeast(left + 1)
    val bottom = ceil((points.maxOfOrNull { it.y } ?: 0f) + padding).toInt().coerceAtLeast(top + 1)
    val node = RenderNode("pagenote-stroke").apply {
      setPosition(left, top, right, bottom)
      setClipToBounds(false)
    }
    val recording = node.beginRecording(right - left, bottom - top)
    try {
      recording.translate(-left.toFloat(), -top.toFloat())
      PenStrokePainter.drawStroke(recording, stroke, documentGeometry)
    } finally {
      node.endRecording()
    }
    return Entry(stroke, node, intArrayOf(left, top, right, bottom))
  }

  fun clear() {
    entries.forEach { it.node.discardDisplayList() }
    root?.discardDisplayList()
    root = null
    entries = emptyList()
    recorded = emptyList()
  }
}
