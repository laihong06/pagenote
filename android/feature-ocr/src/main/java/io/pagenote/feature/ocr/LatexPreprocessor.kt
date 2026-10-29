package io.pagenote.feature.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * pix2tex 输入张量构造：位图 → 灰度 → [LatexImageLayout] 定尺 → CHW float
 * 数组（背景填充白纸值，前景墨迹为暗值；归一化见 [LatexImageLayout.normalize]）。
 */
object LatexPreprocessor {

  fun layoutFor(bitmap: Bitmap): LatexImageLayout =
    LatexImageLayout.layout(bitmap.width, bitmap.height)

  /** 返回 CHW（C=1）浮点输入与 ONNX 形状 [1,1,H,W]；调用方拥有返回数组。 */
  fun toModelInput(bitmap: Bitmap): ModelInput {
    require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
      "latex input bitmap is empty or recycled"
    }
    val layout = layoutFor(bitmap)
    val pixels = readScaledGrayscale(bitmap, layout.contentWidth, layout.contentHeight)
    val background = LatexImageLayout.normalize(255)
    val plane = FloatArray(layout.canvasWidth * layout.canvasHeight) { background }
    for (y in 0 until layout.contentHeight) {
      for (x in 0 until layout.contentWidth) {
        plane[y * layout.canvasWidth + x] = LatexImageLayout.normalize(pixels[y * layout.contentWidth + x])
      }
    }
    return ModelInput(
      values = plane,
      shape = longArrayOf(
        1L,
        LatexImageLayout.CHANNELS.toLong(),
        layout.canvasHeight.toLong(),
        layout.canvasWidth.toLong(),
      ),
    )
  }

  private fun readScaledGrayscale(bitmap: Bitmap, width: Int, height: Int): IntArray {
    val scaled = if (bitmap.width == width && bitmap.height == height) {
      bitmap
    } else {
      Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    val ownedScale = if (scaled === bitmap) null else scaled
    return try {
      val grayBitmap = toGrayscale(scaled)
      try {
        val argb = IntArray(grayBitmap.width * grayBitmap.height)
        grayBitmap.getPixels(argb, 0, grayBitmap.width, 0, 0, grayBitmap.width, grayBitmap.height)
        argb.map { pixel ->
          // 灰度位图 R=G=B；按 ITU-R BT.601 亮度权重复核一遍。
          val r = (pixel shr 16) and 0xFF
          val g = (pixel shr 8) and 0xFF
          val b = pixel and 0xFF
          (r * 299 + g * 587 + b * 114) / 1000
        }.toIntArray()
      } finally {
        grayBitmap.recycle()
      }
    } finally {
      ownedScale?.let { if (!it.isRecycled) it.recycle() }
    }
  }

  private fun toGrayscale(source: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = ColorMatrixColorFilter(
      ColorMatrix(
        floatArrayOf(
          0.299f, 0.587f, 0.114f, 0f, 0f,
          0.299f, 0.587f, 0.114f, 0f, 0f,
          0.299f, 0.587f, 0.114f, 0f, 0f,
          0f, 0f, 0f, 1f, 0f,
        ),
      ),
    )
    canvas.drawBitmap(source, 0f, 0f, paint)
    return output
  }

  data class ModelInput(val values: FloatArray, val shape: LongArray) {
    override fun equals(other: Any?): Boolean =
      other is ModelInput && other.shape.contentEquals(shape) && other.values.contentEquals(values)

    override fun hashCode(): Int = shape.contentHashCode() * 31 + values.contentHashCode()
  }
}
