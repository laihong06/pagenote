package io.pagenote.android

/**
 * 公式素材多行布局（移植批次 5）。复刻 touchnotes `FormulaMaterial.Companion.
 * createFormulaImage` 的布局常量与语义（decompiled-real FormulaMaterial.java：
 * `MAX_FORMULA_HEIGHT=100, HORIZONTAL_PADDING=40, VERTICAL_PADDING=40,
 * VERTICAL_SPACING=20`）：
 *
 * - 每行先按基准高归一：高于 [MAX_FORMULA_HEIGHT] 的行等比缩到 100；
 * - 画布高 = Σ行高 + (n-1)×[VERTICAL_SPACING] + 2×[VERTICAL_PADDING]；
 *   画布宽 = 最大行宽 + 2×[HORIZONTAL_PADDING]（行水平居中）；
 * - `targetHeight > 0` 时整体再等比缩放到目标高（touchnotes 的
 *   `scaleBitmap(f, f)` 语义）。
 *
 * 纯 JVM 几何（行宽高由渲染器测量后传入），Android 侧 FormulaMaterialRenderer
 * 消费本布局绘制 JLatexMath 位图。
 */
data class FormulaLineLayout(
  val index: Int,
  /** 归一后行宽（px）。 */
  val width: Int,
  /** 归一后行高（px）。 */
  val height: Int,
  /** 画布内绘制原点 x（水平居中）。 */
  val x: Int,
  /** 画布内绘制原点 y。 */
  val y: Int,
  /** 行相对整体缩放（targetHeight 生效时 <1）。 */
  val overallScale: Float,
)

data class FormulaBlockLayout(
  val width: Int,
  val height: Int,
  val lines: List<FormulaLineLayout>,
)

object FormulaMaterialLayout {
  const val MAX_FORMULA_HEIGHT = 100
  const val HORIZONTAL_PADDING = 40
  const val VERTICAL_PADDING = 40
  const val VERTICAL_SPACING = 20

  /**
   * [lineSizes] 为各行（宽, 高）；[targetHeight] ≤0 表示不整体缩放。
   * 空输入或非正尺寸返回 null。
   */
  fun layout(
    lineSizes: List<Pair<Int, Int>>,
    targetHeight: Int = -1,
  ): FormulaBlockLayout? {
    if (lineSizes.isEmpty() || lineSizes.size > MAX_LINES) return null
    if (lineSizes.any { it.first <= 0 || it.second <= 0 }) return null

    // 行级归一：高 >100 等比缩到 100。
    val normalized = lineSizes.map { (width, height) ->
      if (height > MAX_FORMULA_HEIGHT) {
        (width * MAX_FORMULA_HEIGHT.toFloat() / height).toInt().coerceAtLeast(1) to MAX_FORMULA_HEIGHT
      } else {
        width to height
      }
    }
    val lineWidths = normalized.map { it.first }

    var canvasWidth = (lineWidths.maxOrNull() ?: 0) + 2 * HORIZONTAL_PADDING
    var canvasHeight = normalized.sumOf { it.second } + (normalized.size - 1) * VERTICAL_SPACING +
      2 * VERTICAL_PADDING

    var overallScale = 1f
    if (targetHeight > 0 && targetHeight != canvasHeight && canvasHeight > 0) {
      overallScale = targetHeight.toFloat() / canvasHeight
      canvasWidth = (canvasWidth * overallScale).toInt().coerceAtLeast(1)
      canvasHeight = targetHeight
    }

    val lines = mutableListOf<FormulaLineLayout>()
    // 缩放语义对齐 touchnotes：先按原始尺寸排布，再整体等比缩到 targetHeight。
    var y = (VERTICAL_PADDING * overallScale).toInt()
    val scaledSpacing = (VERTICAL_SPACING * overallScale).toInt()
    normalized.forEachIndexed { index, (width, height) ->
      val scaledWidth = (width * overallScale).toInt().coerceAtLeast(1)
      val x = (canvasWidth - scaledWidth) / 2
      val scaledHeight = (height * overallScale).toInt().coerceAtLeast(1)
      lines.add(
        FormulaLineLayout(
          index = index,
          width = scaledWidth,
          height = scaledHeight,
          x = x,
          y = y,
          overallScale = overallScale,
        ),
      )
      y += scaledHeight + scaledSpacing
    }
    return FormulaBlockLayout(width = canvasWidth, height = canvasHeight, lines = lines)
  }

  const val MAX_LINES = 8
}
