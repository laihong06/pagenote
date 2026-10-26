package io.pagenote.android

/**
 * 悬浮窗拖拽/缩放几何策略（移植差距清单 #24）。
 *
 * 纯逻辑：所有边界钳制在此决定，[FloatingWindowService] 只把结果
 * 应用到 WindowManager 的 LayoutParams。悬浮球允许部分离屏（贴边），
 * 展开面板要求完整留在屏幕内；拖动与触摸判定都以像素计算。
 */
data class FloatingWindowGeometry(
  val x: Int,
  val y: Int,
  val widthPx: Int,
  val heightPx: Int,
)

object FloatingWindowPolicy {
  /** 展开面板尺寸值域（dp），缩放把手与默认尺寸都在此范围内钳制。 */
  const val MIN_PANEL_WIDTH_DP = 240
  const val MAX_PANEL_WIDTH_DP = 600
  const val MIN_PANEL_HEIGHT_DP = 200
  const val MAX_PANEL_HEIGHT_DP = 480

  /** 面板默认尺寸（dp）。 */
  const val DEFAULT_PANEL_WIDTH_DP = 320
  const val DEFAULT_PANEL_HEIGHT_DP = 320

  /** 悬浮球直径（dp）。 */
  const val BALL_SIZE_DP = 56

  /** 拖拽后窗口至少保留的可见像素（贴边不被完全拖出屏幕）。 */
  const val MIN_VISIBLE_PX = 48

  /**
   * 悬浮球拖拽后的位置钳制：允许部分离屏（贴边收纳），但保留
   * [MIN_VISIBLE_PX] 可见；y 保持可见（顶部不悬浮出屏）。
   */
  fun clampBallPosition(
    x: Int,
    y: Int,
    widthPx: Int,
    heightPx: Int,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): Pair<Int, Int> {
    require(widthPx > 0 && heightPx > 0) { "bad ball size" }
    require(screenWidthPx > 0 && screenHeightPx > 0) { "bad screen size" }
    val minX = MIN_VISIBLE_PX - widthPx
    val maxX = screenWidthPx - MIN_VISIBLE_PX
    val minY = 0
    val maxY = screenHeightPx - MIN_VISIBLE_PX
    return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
  }

  /** 展开面板拖拽后的位置钳制：完整留在屏幕内。 */
  fun clampPanelPosition(
    x: Int,
    y: Int,
    widthPx: Int,
    heightPx: Int,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): Pair<Int, Int> {
    require(widthPx > 0 && heightPx > 0) { "bad panel size" }
    require(screenWidthPx > 0 && screenHeightPx > 0) { "bad screen size" }
    val clampedWidth = minOf(widthPx, screenWidthPx)
    val clampedHeight = minOf(heightPx, screenHeightPx)
    return x.coerceIn(0, screenWidthPx - clampedWidth) to
      y.coerceIn(0, screenHeightPx - clampedHeight)
  }

  /**
   * 缩放把手拖拽（dx/dy 为 dp）：右下角拖动等价于宽高同时变化，
   * 结果钳制在 [MIN_PANEL_WIDTH_DP]..[MAX_PANEL_WIDTH_DP] 与
   * [MIN_PANEL_HEIGHT_DP]..[MAX_PANEL_HEIGHT_DP]。
   */
  fun resizedPanel(widthDp: Int, heightDp: Int, dWidthDp: Int, dHeightDp: Int): Pair<Int, Int> {
    val width = (widthDp + dWidthDp).coerceIn(MIN_PANEL_WIDTH_DP, MAX_PANEL_WIDTH_DP)
    val height = (heightDp + dHeightDp).coerceIn(MIN_PANEL_HEIGHT_DP, MAX_PANEL_HEIGHT_DP)
    return width to height
  }

  /** 触摸位移是否仍在单击容差内（用于区分拖动与点按悬浮球）。 */
  fun isTapWithinSlop(totalDxPx: Float, totalDyPx: Float, slopPx: Float): Boolean {
    require(slopPx >= 0f) { "bad touch slop" }
    return totalDxPx * totalDxPx + totalDyPx * totalDyPx <= slopPx * slopPx
  }
}
