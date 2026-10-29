package io.pagenote.feature.ocr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * pix2tex 输入布局（纯几何，可 JVM 测试）：灰度图按保持纵横比缩放到固定
 * 高度（默认 192px），宽度上限 [MAX_WIDTH]（默认 672px），不足处右侧/底部
 * 以背景值填充；归一化常数沿用 pix2tex `TransformImage` 的 mean/std。
 */
data class LatexImageLayout(
  val canvasWidth: Int,
  val canvasHeight: Int,
  val contentWidth: Int,
  val contentHeight: Int,
) {
  init {
    require(canvasWidth > 0 && canvasHeight > 0) { "bad latex canvas: $canvasWidth x $canvasHeight" }
    require(contentWidth in 1..canvasWidth && contentHeight in 1..canvasHeight) {
      "bad latex content box: $contentWidth x $contentHeight"
    }
  }

  companion object {
    const val TARGET_HEIGHT = 192
    const val MAX_WIDTH = 672
    const val MEAN = 0.7931f
    const val STD = 0.1738f
    const val CHANNELS = 1

    /**
     * 计算源图（宽高像素）在模型画布中的布局。超高图按高度优先缩放；
     * 超宽图先压到 [MAX_WIDTH] 再保持比例（高度随之减小，画布高度仍是
     * [TARGET_HEIGHT]，剩余部分填充背景）。
     */
    fun layout(
      sourceWidth: Int,
      sourceHeight: Int,
      targetHeight: Int = TARGET_HEIGHT,
      maxWidth: Int = MAX_WIDTH,
    ): LatexImageLayout {
      require(sourceWidth > 0 && sourceHeight > 0) { "bad source size: $sourceWidth x $sourceHeight" }
      require(targetHeight > 0 && maxWidth > 0) { "bad target box: $targetHeight x $maxWidth" }
      val byHeight = targetHeight.toDouble() * sourceWidth / sourceHeight
      var contentWidth = min(maxWidth, max(1, byHeight.roundToInt()))
      var contentHeight = targetHeight
      if (sourceWidth.toDouble() / sourceHeight > maxWidth.toDouble() / targetHeight) {
        // 源图过宽：以宽度为约束重算高度。
        contentHeight = max(1, (maxWidth.toDouble() * sourceHeight / sourceWidth).roundToInt())
        contentWidth = maxWidth
      }
      return LatexImageLayout(
        canvasWidth = maxWidth,
        canvasHeight = targetHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
      )
    }

    /** 归一化单通道像素值（0..255 灰度，纸白墨黑，与 pix2tex 一致：不反转）。 */
    fun normalize(gray: Int): Float = ((gray.coerceIn(0, 255) / 255f) - MEAN) / STD
  }
}
