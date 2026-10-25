package io.pagenote.android

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 系统截屏（MediaProjection）→ OCR / 取色的纯策略（移植差距清单 #23）。
 *
 * Android 侧 MediaProjection / VirtualDisplay / ImageReader 的装配在
 * [ScreenCaptureService]；这里只放可 JVM 单测的决策逻辑。
 */
object ScreenCapturePolicy {
  /** 截屏帧长边上限：OCR 与取色预览都不需要原始分辨率，控制内存与推理耗时。 */
  const val MAX_FRAME_LONG_EDGE = 2048

  data class ScaledSize(val width: Int, val height: Int)

  /** 等比缩放到长边不超过上限；不足上限保持原尺寸（不放大会引入模糊）。 */
  fun scaleToFrameLimit(width: Int, height: Int): ScaledSize {
    require(width > 0 && height > 0) { "frame size must be positive" }
    val longEdge = max(width, height)
    if (longEdge <= MAX_FRAME_LONG_EDGE) return ScaledSize(width, height)
    val ratio = MAX_FRAME_LONG_EDGE.toDouble() / longEdge
    return ScaledSize(
      width = (width * ratio).roundToInt().coerceAtLeast(1),
      height = (height * ratio).roundToInt().coerceAtLeast(1),
    )
  }
}

/** 取色结果：hex 与最接近的命名色（用于无障碍与状态提示）。 */
data class PickedColor(val argb: Int, val hex: String, val nearest: NamedColor)

data class NamedColor(val nameZh: String, val nameEn: String, val argb: Int)

object ColorPicking {
  private val PALETTE = listOf(
    NamedColor("黑色", "black", 0xFF000000.toInt()),
    NamedColor("白色", "white", 0xFFFFFFFF.toInt()),
    NamedColor("灰色", "gray", 0xFF808080.toInt()),
    NamedColor("红色", "red", 0xFFFF0000.toInt()),
    NamedColor("橙色", "orange", 0xFFFFA500.toInt()),
    NamedColor("黄色", "yellow", 0xFFFFFF00.toInt()),
    NamedColor("绿色", "green", 0xFF00FF00.toInt()),
    NamedColor("青色", "cyan", 0xFF00FFFF.toInt()),
    NamedColor("蓝色", "blue", 0xFF0000FF.toInt()),
    NamedColor("紫色", "purple", 0xFF800080.toInt()),
    NamedColor("粉色", "pink", 0xFFFFC0CB.toInt()),
    NamedColor("棕色", "brown", 0xFF8B4513.toInt()),
  )

  fun hex(argb: Int): String = "#%08X".format(argb)

  /** 去掉 alpha 影响：取色关心的是视觉颜色，alpha 全程按不透明处理。 */
  fun opaque(argb: Int): Int = argb or 0xFF000000.toInt()

  /** RGB 欧氏距离最近的命名色。 */
  fun nearestNamedColor(argb: Int): NamedColor {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return PALETTE.minBy { candidate ->
      val cr = (candidate.argb shr 16) and 0xFF
      val cg = (candidate.argb shr 8) and 0xFF
      val cb = candidate.argb and 0xFF
      val dr = abs(r - cr)
      val dg = abs(g - cg)
      val db = abs(b - cb)
      dr * dr + dg * dg + db * db
    }
  }

  fun pick(argb: Int): PickedColor {
    val opaqueColor = opaque(argb)
    return PickedColor(
      argb = opaqueColor,
      hex = hex(opaqueColor),
      nearest = nearestNamedColor(opaqueColor),
    )
  }

  /** 页面坐标（0..1 归一化）→ 位图像素坐标，越界收拢到图内。 */
  fun pixelAt(normalizedX: Float, normalizedY: Float, width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0) { "bitmap size must be positive" }
    val x = (normalizedX.coerceIn(0f, 1f) * (width - 1)).roundToInt()
    val y = (normalizedY.coerceIn(0f, 1f) * (height - 1)).roundToInt()
    return x to y
  }
}
