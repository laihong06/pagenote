package io.pagenote.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.caverock.androidsvg.SVG
import io.noties.markwon.image.MediaDecoder
import java.io.InputStream
import kotlin.math.min
import kotlin.math.sqrt

internal class MermaidSvgMediaDecoder(
  private val context: Context,
  private val maxWidthPx: () -> Int,
) : MediaDecoder() {
  private val fallbackWidthPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)

  override fun supportedTypes(): Collection<String> = listOf(CONTENT_TYPE)

  override fun decode(contentType: String?, inputStream: InputStream): Drawable {
    require(contentType == CONTENT_TYPE)
    val svg = SVG.getFromInputStream(inputStream)
    val viewBox = svg.documentViewBox
    val svgWidth = svg.documentWidth.takeIf { it.isFinite() && it > 0f }
      ?: viewBox?.width()?.takeIf { it.isFinite() && it > 0f }
      ?: fallbackWidthPx.toFloat()
    val svgHeight = svg.documentHeight.takeIf { it.isFinite() && it > 0f }
      ?: viewBox?.height()?.takeIf { it.isFinite() && it > 0f }
      ?: svgWidth * 0.6f
    val maxWidth = maxWidthPx().takeIf { it > 0 } ?: fallbackWidthPx
    val svgWidthPx = svgWidth.toDouble()
    val svgHeightPx = svgHeight.toDouble()
    val widthScale = min(1.0, maxWidth.toDouble() / svgWidthPx)
    val pixelScale = sqrt(MAX_RASTER_PIXELS / svgWidthPx / svgHeightPx)
    val dimensionScale = min(MAX_RASTER_DIMENSION / svgWidthPx, MAX_RASTER_DIMENSION / svgHeightPx)
    val rasterScale = min(widthScale, min(pixelScale, dimensionScale))
    val bitmapWidth = (svgWidthPx * rasterScale).toInt().coerceIn(1, MAX_RASTER_DIMENSION.toInt())
    val bitmapHeight = (svgHeightPx * rasterScale).toInt().coerceIn(1, MAX_RASTER_DIMENSION.toInt())
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
      scale(bitmapWidth / svgWidth, bitmapHeight / svgHeight)
      svg.renderToCanvas(this)
    }
    return BitmapDrawable(context.resources, bitmap).apply { setFilterBitmap(true) }
  }

  companion object {
    const val CONTENT_TYPE = "image/svg+xml"
    private const val MAX_RASTER_PIXELS = 4_194_304.0
    private const val MAX_RASTER_DIMENSION = 16_384.0
  }
}
