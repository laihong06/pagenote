package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 悬浮计时器策略与状态机（移植差距清单 #24）。 */
class FloatingTimerPolicyTest {

  @Test
  fun presetsCoverCommonDurations() {
    assertEquals(listOf(1, 5, 10, 25), FloatingTimerPolicy.MINUTE_PRESETS)
    assertEquals(25, FloatingTimerPolicy.DEFAULT_PRESET_MINUTES)
    assertEquals(25 * 60_000L, FloatingTimerPolicy.durationMillis(FloatingTimerPolicy.DEFAULT_PRESET_MINUTES))
  }

  @Test
  fun durationRejectsNonPositiveMinutes() {
    assertThrows(IllegalArgumentException::class.java) { FloatingTimerPolicy.durationMillis(0) }
    assertThrows(IllegalArgumentException::class.java) { FloatingTimerPolicy.durationMillis(-5) }
  }

  @Test
  fun formatRendersClockText() {
    assertEquals("00:00", FloatingTimerPolicy.format(0L))
    assertEquals("00:59", FloatingTimerPolicy.format(59_999L))
    assertEquals("05:00", FloatingTimerPolicy.format(5 * 60_000L))
    assertEquals("1:00:00", FloatingTimerPolicy.format(60 * 60_000L))
    assertEquals("1:01:05", FloatingTimerPolicy.format(3_665_000L))
    // 负值钳为 0。
    assertEquals("00:00", FloatingTimerPolicy.format(-1L))
  }

  @Test
  fun remainingAfterTickDecrementsAndClampsAtZero() {
    assertEquals(4_000L, FloatingTimerPolicy.remainingAfterTick(5_000L, 1_000L))
    assertEquals(0L, FloatingTimerPolicy.remainingAfterTick(5_000L, 9_000L))
    assertEquals(0L, FloatingTimerPolicy.remainingAfterTick(0L, 0L))
    assertThrows(IllegalArgumentException::class.java) {
      FloatingTimerPolicy.remainingAfterTick(5_000L, -1L)
    }
  }
}

/** 计时器状态机：注入时钟逐 tick 推进。 */
class FloatingTimerEngineTest {

  private var nowMillis = 0L

  private fun engine() = FloatingTimerEngine { nowMillis }

  @Test
  fun startThenTicksCountDown() {
    val timer = engine()
    timer.start(10_000L)
    nowMillis = 2_500L
    val state = timer.tick()
    assertTrue(state.running)
    assertFalse(state.finished)
    assertEquals(7_500L, state.remainingMillis)
  }

  @Test
  fun finishReportsExactlyOnce() {
    val timer = engine()
    timer.start(1_000L)
    nowMillis = 500L
    val running = timer.tick()
    assertFalse(running.finished)
    assertEquals(500L, running.remainingMillis)
    nowMillis = 1_000L
    val state = timer.tick()
    assertTrue(state.finished)
    assertFalse(state.running)
    assertEquals(0L, state.remainingMillis)
    // 后续 tick 保持完成态，不再重复触发。
    val again = timer.tick()
    assertTrue(again.finished)
    assertEquals(0L, again.remainingMillis)
  }

  @Test
  fun pauseAndResumeFreezeRemaining() {
    val timer = engine()
    timer.start(10_000L)
    nowMillis = 4_000L
    val paused = timer.pause()
    assertFalse(paused.running)
    assertEquals(6_000L, paused.remainingMillis)
    // 暂停期间时间照流，剩余不变。
    nowMillis = 100_000L
    assertEquals(6_000L, timer.tick().remainingMillis)
    val resumed = timer.resume()
    assertTrue(resumed.running)
    nowMillis = 102_000L
    assertEquals(4_000L, timer.tick().remainingMillis)
  }

  @Test
  fun resetReturnsToIdle() {
    val timer = engine()
    timer.start(10_000L)
    nowMillis = 1_000L
    val reset = timer.reset()
    assertFalse(reset.running)
    assertFalse(reset.finished)
    assertEquals(0L, reset.remainingMillis)
    assertEquals(0L, reset.durationMillis)
  }

  @Test
  fun startReplacesRunningTimer() {
    val timer = engine()
    timer.start(60_000L)
    nowMillis = 5_000L
    timer.start(3_000L)
    nowMillis = 6_000L
    assertEquals(2_000L, timer.tick().remainingMillis)
  }

  @Test
  fun resumeWithoutPauseIsNoop() {
    val timer = engine()
    timer.start(5_000L)
    nowMillis = 1_000L
    val state = timer.resume()
    assertTrue(state.running)
    assertEquals(4_000L, state.remainingMillis)
  }

  @Test
  fun tickOnIdleTimerIsIdle() {
    val timer = engine()
    val state = timer.tick()
    assertFalse(state.running)
    assertFalse(state.finished)
    assertEquals(0L, state.remainingMillis)
  }
}
