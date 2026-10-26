package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * View-pixel raster of committed strokes — the Kotlin counterpart of the
 * page handle in the Newskyer PaintView kernel. Committed ink renders once
 * into this layer; the owner draws only live ink on top per frame.
 * Rebuilds are lazy: they happen when the view size, the geometry key, or
 * the committed list changes, and skip strokes outside the viewport.
 */
class PenStrokeCache {
  private var bitmap: Bitmap? = null
  private var rasterCanvas: Canvas? = null
  private var committed: List<PenStroke> = emptyList()
  private var key: CacheKey? = null
  private var dirty = false

  fun setStrokes(strokes: List<PenStroke>) {
    if (strokes != committed) {
      committed = strokes
      dirty = true
    }
  }

  fun appendStroke(stroke: PenStroke, geometry: PenCanvasGeometry) {
    committed = committed + stroke
    val canvas = rasterCanvas
    if (canvas == null || dirty || key != CacheKey.from(geometry)) {
      dirty = true
      return
    }
    drawStrokeInto(canvas, stroke, geometry)
  }

  fun drawInto(target: Canvas, geometry: PenCanvasGeometry, widthPx: Int, heightPx: Int) {
    if (widthPx <= 0 || heightPx <= 0 || !geometry.isValid) return
    val cacheKey = CacheKey.from(geometry)
    val current = bitmap
    if (current == null || current.width != widthPx || current.height != heightPx ||
      cacheKey != key || dirty
    ) {
      rebuild(widthPx, heightPx, geometry, cacheKey)
    }
    bitmap?.let { target.drawBitmap(it, 0f, 0f, null) }
  }

  fun clear() {
    bitmap?.recycle()
    bitmap = null
    rasterCanvas = null
    committed = emptyList()
    key = null
    dirty = false
  }

  private fun rebuild(
    widthPx: Int,
    heightPx: Int,
    geometry: PenCanvasGeometry,
    cacheKey: CacheKey,
  ) {
    bitmap?.recycle()
    val newBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val newCanvas = Canvas(newBitmap)
    committed.forEach { stroke ->
      if (intersectsViewport(stroke, geometry, widthPx, heightPx)) {
        drawStrokeInto(newCanvas, stroke, geometry)
      }
    }
    bitmap = newBitmap
    rasterCanvas = newCanvas
    key = cacheKey
    dirty = false
  }

  private fun drawStrokeInto(target: Canvas, stroke: PenStroke, geometry: PenCanvasGeometry) {
    val save = target.save()
    target.translate(geometry.pageOriginX, geometry.pageOriginY)
    target.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
    PenStrokePainter.drawStroke(target, stroke, geometry)
    target.restoreToCount(save)
  }

  private fun intersectsViewport(
    stroke: PenStroke,
    geometry: PenCanvasGeometry,
    widthPx: Int,
    heightPx: Int,
  ): Boolean {
    val points = stroke.points
    if (points.isEmpty() || stroke.type !in RASTER_TYPES) return false
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val pad = stroke.width.toFloat() + 1f
    val left = geometry.pageOriginX + (minX - pad) * geometry.pixelsPerDocumentUnit
    val top = geometry.pageOriginY + (minY - pad) * geometry.pixelsPerDocumentUnit
    val right = geometry.pageOriginX + (maxX + pad) * geometry.pixelsPerDocumentUnit
    val bottom = geometry.pageOriginY + (maxY + pad) * geometry.pixelsPerDocumentUnit
    return right >= 0f && bottom >= 0f && left <= widthPx && top <= heightPx
  }

  private data class CacheKey(
    val pageOriginX: Float,
    val pageOriginY: Float,
    val pixelsPerDocumentUnit: Float,
  ) {
    companion object {
      fun from(geometry: PenCanvasGeometry) = CacheKey(
        pageOriginX = geometry.pageOriginX,
        pageOriginY = geometry.pageOriginY,
        pixelsPerDocumentUnit = geometry.pixelsPerDocumentUnit,
      )
    }
  }

  private companion object {
    val RASTER_TYPES = setOf("pen", "highlight", "line", "arrow", "rect", "ellipse")
  }
}
