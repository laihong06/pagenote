package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Committed ink uses document-space hardware display lists, so viewport
 * transforms do not rerasterize samples. Software canvases retain the
 * view-pixel bitmap fallback, including fit-to-page reuse during a pinch.
 */
class PenStrokeCache {
  private val displayList = PenStrokeDisplayList()
  private var bitmap: Bitmap? = null
  private var rasterCanvas: Canvas? = null
  private var committed: List<PenStroke> = emptyList()
  private var key: CacheKey? = null
  private var dirty = false
  private val filteredPaint = Paint(Paint.FILTER_BITMAP_FLAG)
  private var fitCheckKey: FitCheckKey? = null
  private var inkFitsBase = false

  fun setStrokes(strokes: List<PenStroke>) {
    if (strokes != committed) {
      committed = strokes
      dirty = true
      fitCheckKey = null
    }
  }

  fun appendStroke(stroke: PenStroke, geometry: PenCanvasGeometry) {
    committed = committed + stroke
    fitCheckKey = null
    val canvas = rasterCanvas
    if (canvas == null || dirty || key != CacheKey.from(geometry)) {
      dirty = true
      return
    }
    drawStrokeInto(canvas, stroke, geometry)
  }

  fun drawInto(
    target: Canvas,
    geometry: PenCanvasGeometry,
    widthPx: Int,
    heightPx: Int,
    transformInProgress: Boolean = false,
  ) {
    if (widthPx <= 0 || heightPx <= 0 || !geometry.isValid) return
    if (committed.isEmpty()) {
      displayList.clear()
      // An empty page needs no full-screen bitmap, especially during a pinch.
      bitmap?.recycle()
      bitmap = null
      rasterCanvas = null
      key = null
      dirty = false
      return
    }
    if (target.isHardwareAccelerated) {
      displayList.draw(target, committed, geometry)
      return
    }
    if (transformInProgress && !geometry.allowOutsidePage && geometry.rotationDegrees == 0f) {
      // Cache the complete fit-to-page ink once, then let Canvas transform the
      // same raster for intermediate pinch frames. Rebuild at final scale when
      // the gesture ends. Out-of-page ink uses the exact existing path.
      val base = PenCanvasGeometry(
        widthPx.toFloat(), heightPx.toFloat(),
        PenCanvasPageTransform(geometry.pageWidthPx, geometry.pageHeightPx),
      )
      val baseKey = CacheKey.from(base)
      if (!contentFitsBase(base, widthPx, heightPx)) {
        drawExact(target, geometry, widthPx, heightPx)
        return
      }
      if (bitmap == null || bitmap?.width != widthPx || bitmap?.height != heightPx || key != baseKey || dirty) {
        rebuild(widthPx, heightPx, base, baseKey)
      }
      val ratio = geometry.pixelsPerDocumentUnit / base.pixelsPerDocumentUnit
      val saved = target.save()
      target.translate(geometry.pageOriginX - base.pageOriginX * ratio, geometry.pageOriginY - base.pageOriginY * ratio)
      target.scale(ratio, ratio)
      bitmap?.let { target.drawBitmap(it, 0f, 0f, filteredPaint) }
      target.restoreToCount(saved)
      return
    }
    drawExact(target, geometry, widthPx, heightPx)
  }

  private fun drawExact(target: Canvas, geometry: PenCanvasGeometry, widthPx: Int, heightPx: Int) {
    val cacheKey = CacheKey.from(geometry)
    val current = bitmap
    if (current == null || current.width != widthPx || current.height != heightPx ||
      cacheKey != key || dirty
    ) {
      rebuild(widthPx, heightPx, geometry, cacheKey)
    }
    bitmap?.let { target.drawBitmap(it, 0f, 0f, null) }
  }

  private fun contentFitsBase(base: PenCanvasGeometry, widthPx: Int, heightPx: Int): Boolean {
    val checkKey = FitCheckKey(CacheKey.from(base), widthPx, heightPx)
    if (fitCheckKey != checkKey) {
      inkFitsBase = committed.all { stroke ->
        val margin = (stroke.width.toFloat() + if (stroke.type == "arrow") 40f else 0f) * base.pixelsPerDocumentUnit + 2f
        stroke.points.all { point ->
          val x = base.toViewX(point)
          val y = base.toViewY(point)
          x >= margin && x <= widthPx - margin && y >= margin && y <= heightPx - margin
        }
      }
      fitCheckKey = checkKey
    }
    return inkFitsBase
  }

  fun clear() {
    displayList.clear()
    bitmap?.recycle()
    bitmap = null
    rasterCanvas = null
    committed = emptyList()
    key = null
    dirty = false
    fitCheckKey = null
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

  private data class FitCheckKey(val geometry: CacheKey, val width: Int, val height: Int)

  private companion object {
    val RASTER_TYPES = setOf("pen", "highlight", "line", "arrow", "rect", "ellipse")
  }
}
