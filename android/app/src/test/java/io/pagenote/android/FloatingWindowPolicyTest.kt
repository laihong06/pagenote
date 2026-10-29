package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 悬浮窗拖拽/缩放几何策略（移植差距清单 #24）。 */
class FloatingWindowPolicyTest {

  // --- 悬浮球位置钳制 ---------------------------------------------------------

  @Test
  fun ballPositionStaysFullyOnScreen() {
    val (x, y) = FloatingWindowPolicy.clampBallPosition(
      x = 500, y = 400,
      widthPx = 150, heightPx = 150,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(500, x)
    assertEquals(400, y)
  }

  @Test
  fun ballPositionAllowsEdgeSnapButKeepsMinVisiblePixels() {
    // 完全拖出右缘 → 钳到保留 48px 可见。
    val (rightX, _) = FloatingWindowPolicy.clampBallPosition(
      x = 1300, y = 400,
      widthPx = 150, heightPx = 150,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(1080 - FloatingWindowPolicy.MIN_VISIBLE_PX, rightX)
    // 完全拖出左缘 → 钳到只露出 48px（x = MIN_VISIBLE - width）。
    val (leftX, _) = FloatingWindowPolicy.clampBallPosition(
      x = -400, y = 400,
      widthPx = 150, heightPx = 150,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(FloatingWindowPolicy.MIN_VISIBLE_PX - 150, leftX)
    // 拖出底部 → y 只保留 48px 可见。
    val (_, bottomY) = FloatingWindowPolicy.clampBallPosition(
      x = 500, y = 3000,
      widthPx = 150, heightPx = 150,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(2340 - FloatingWindowPolicy.MIN_VISIBLE_PX, bottomY)
  }

  @Test
  fun ballPositionRejectsBadSizes() {
    assertThrows(IllegalArgumentException::class.java) {
      FloatingWindowPolicy.clampBallPosition(0, 0, 0, 150, 1080, 2340)
    }
    assertThrows(IllegalArgumentException::class.java) {
      FloatingWindowPolicy.clampBallPosition(0, 0, 150, 150, 0, 2340)
    }
  }

  // --- 面板位置钳制 -----------------------------------------------------------

  @Test
  fun panelPositionStaysFullyOnScreen() {
    val (x, y) = FloatingWindowPolicy.clampPanelPosition(
      x = 100, y = 100,
      widthPx = 900, heightPx = 400,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(100, x)
    assertEquals(100, y)
  }

  @Test
  fun panelPositionClampsToScreenBounds() {
    val (rightX, bottomY) = FloatingWindowPolicy.clampPanelPosition(
      x = 800, y = 2200,
      widthPx = 900, heightPx = 400,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(1080 - 900, rightX)
    assertEquals(2340 - 400, bottomY)
    val (leftX, topY) = FloatingWindowPolicy.clampPanelPosition(
      x = -50, y = -50,
      widthPx = 900, heightPx = 400,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(0, leftX)
    assertEquals(0, topY)
  }

  @Test
  fun panelWiderThanScreenClampsToTopLeft() {
    val (x, y) = FloatingWindowPolicy.clampPanelPosition(
      x = 20, y = 20,
      widthPx = 2000, heightPx = 3000,
      screenWidthPx = 1080, screenHeightPx = 2340,
    )
    assertEquals(0, x)
    assertEquals(0, y)
  }

  // --- 缩放钳制 ---------------------------------------------------------------

  @Test
  fun resizeClampsToPanelBounds() {
    assertEquals(
      FloatingWindowPolicy.MIN_PANEL_WIDTH_DP to FloatingWindowPolicy.MIN_PANEL_HEIGHT_DP,
      FloatingWindowPolicy.resizedPanel(300, 320, -2_000, -2_000),
    )
    assertEquals(
      FloatingWindowPolicy.MAX_PANEL_WIDTH_DP to FloatingWindowPolicy.MAX_PANEL_HEIGHT_DP,
      FloatingWindowPolicy.resizedPanel(300, 320, 2_000, 2_000),
    )
    assertEquals(
      360 to 400,
      FloatingWindowPolicy.resizedPanel(320, 320, 40, 80),
    )
  }

  @Test
  fun panelSizeDefaultsAreInsideBounds() {
    assertTrue(FloatingWindowPolicy.DEFAULT_PANEL_WIDTH_DP in
      FloatingWindowPolicy.MIN_PANEL_WIDTH_DP..FloatingWindowPolicy.MAX_PANEL_WIDTH_DP)
    assertTrue(FloatingWindowPolicy.DEFAULT_PANEL_HEIGHT_DP in
      FloatingWindowPolicy.MIN_PANEL_HEIGHT_DP..FloatingWindowPolicy.MAX_PANEL_HEIGHT_DP)
  }

  // --- 点按判定 ---------------------------------------------------------------

  @Test
  fun tapWithinSlopIsTap() {
    assertTrue(FloatingWindowPolicy.isTapWithinSlop(3f, 4f, slopPx = 8f))
    assertFalse(FloatingWindowPolicy.isTapWithinSlop(7f, 7f, slopPx = 8f))
    assertTrue(FloatingWindowPolicy.isTapWithinSlop(0f, 0f, slopPx = 0f))
  }

  @Test
  fun negativeSlopRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      FloatingWindowPolicy.isTapWithinSlop(1f, 1f, slopPx = -1f)
    }
  }
}
