package io.pagenote.android

internal enum class PenBoxEdge { LEFT, TOP, RIGHT, BOTTOM }

internal object PenBoxGeometry {
  fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
  }
  fun nearestEdge(x: Float, y: Float, width: Float, height: Float, parentWidth: Float, parentHeight: Float): PenBoxEdge {
    val px = x.coerceIn(0f, (parentWidth - width).coerceAtLeast(0f))
    val py = y.coerceIn(0f, (parentHeight - height).coerceAtLeast(0f))
    var edge = PenBoxEdge.BOTTOM
    var distance = (parentHeight - py - height).coerceAtLeast(0f)
    if (px < distance) { edge = PenBoxEdge.LEFT; distance = px }
    if (py < distance) { edge = PenBoxEdge.TOP; distance = py }
    if ((parentWidth - px - width).coerceAtLeast(0f) < distance) edge = PenBoxEdge.RIGHT
    return edge
  }

  fun restoreAlongEdge(fraction: Float, parentSize: Float, boxSize: Float): Float =
    (fraction.coerceIn(0f, 1f) * parentSize).coerceIn(0f, (parentSize - boxSize).coerceAtLeast(0f))
}
