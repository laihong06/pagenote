package io.pagenote.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQuotaRecordingPlaybackPolicyTest {
  // ---- AudioQuotaPolicy ----

  @Test
  fun quotaSanitizeClampsToRange() {
    assertEquals(200, AudioQuotaPolicy.sanitizeMb(200))
    assertEquals(AudioQuotaPolicy.DEFAULT_MB, AudioQuotaPolicy.sanitizeMb(10))
    assertEquals(AudioQuotaPolicy.DEFAULT_MB, AudioQuotaPolicy.sanitizeMb(99_999))
    assertEquals(AudioQuotaPolicy.MIN_MB, AudioQuotaPolicy.sanitizeMb(AudioQuotaPolicy.MIN_MB))
  }

  @Test
  fun quotaValidateRejectsOutOfRange() {
    try {
      AudioQuotaPolicy.validateMb(10)
      throw AssertionError("expected rejection")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("quota"))
    }
    AudioQuotaPolicy.validateMb(AudioQuotaPolicy.MAX_MB) // 上界可用
  }

  @Test
  fun remainingBytesNeverNegative() {
    assertEquals(0L, AudioQuotaPolicy.remainingBytes(50, usedBytes = 200L * 1024 * 1024))
    assertEquals(
      AudioQuotaPolicy.quotaBytes(50) - 1024,
      AudioQuotaPolicy.remainingBytes(50, usedBytes = 1024),
    )
  }

  @Test
  fun maxRecordingCappedByAbsoluteLimit() {
    val hugeQuota = AudioQuotaPolicy.sanitizeMb(AudioQuotaPolicy.MAX_MB)
    assertEquals(
      AudioQuotaPolicy.MAX_RECORDING_BYTES,
      AudioQuotaPolicy.maxRecordingBytes(hugeQuota, usedBytes = 0),
    )
    // 字节预算 ÷ 4000B/s = 60 分钟。
    assertEquals(
      AudioCapsulePolicy.MAX_DURATION_MS,
      AudioQuotaPolicy.maxRecordingDurationMs(hugeQuota, usedBytes = 0),
    )
  }

  @Test
  fun cannotStartWhenBudgetBelowMinimumClip() {
    // 50MB 配额已用满 → 不允许开始。
    assertFalse(AudioQuotaPolicy.canStartRecording(50, AudioQuotaPolicy.quotaBytes(50)))
    // 剩余预算足够最短有效录音（500ms × 4000B/s = 2000B）→ 允许。
    assertTrue(AudioQuotaPolicy.canStartRecording(50, AudioQuotaPolicy.quotaBytes(50) - 2048))
    // 剩余不足最短录音 → 拒绝（避免录完即弃）。
    assertFalse(AudioQuotaPolicy.canStartRecording(50, AudioQuotaPolicy.quotaBytes(50) - 1000))
  }

  @Test
  fun usageFilterCountsOnlyRecordings() {
    assertTrue(AudioQuotaPolicy.countsTowardUsage("01234567-89ab-cdef-0123-456789abcdef.m4a"))
    assertFalse(AudioQuotaPolicy.countsTowardUsage("notes.json"))
    assertFalse(AudioQuotaPolicy.countsTowardUsage("../escape.m4a"))
  }

  // ---- AudioRecordingPolicy ----

  @Test
  fun recordingStateMachineGuards() {
    val idle = AudioRecordingPolicy.Phase.IDLE
    val preparing = AudioRecordingPolicy.Phase.PREPARING
    val recording = AudioRecordingPolicy.Phase.RECORDING
    assertTrue(AudioRecordingPolicy.canStart(idle, permissionGranted = true, quotaAllowed = true))
    assertFalse(AudioRecordingPolicy.canStart(recording, true, true))
    assertFalse(AudioRecordingPolicy.canStart(idle, permissionGranted = false, quotaAllowed = true))
    assertFalse(AudioRecordingPolicy.canStart(idle, true, quotaAllowed = false))

    assertEquals(recording, AudioRecordingPolicy.onPrepared(preparing, prepared = true))
    assertEquals(idle, AudioRecordingPolicy.onPrepared(preparing, prepared = false))
    assertEquals(idle, AudioRecordingPolicy.onPrepared(idle, prepared = true)) // 只有 PREPARING 可推进
    assertEquals(idle, AudioRecordingPolicy.onStopped(recording))
    assertEquals(idle, AudioRecordingPolicy.onStopped(preparing))
    assertEquals(idle, AudioRecordingPolicy.onStopped(idle))
  }

  @Test
  fun elapsedLabelFormats() {
    assertEquals("00:00", AudioRecordingPolicy.elapsedLabel(0))
    assertEquals("00:59", AudioRecordingPolicy.elapsedLabel(59_999))
    assertEquals("01:00", AudioRecordingPolicy.elapsedLabel(60_000))
    assertEquals("1:01:05", AudioRecordingPolicy.elapsedLabel(3_665_000))
    assertEquals("00:00", AudioRecordingPolicy.elapsedLabel(-5))
  }

  @Test
  fun amplitudeLevelIsLogarithmicAndClamped() {
    assertEquals(0f, AudioRecordingPolicy.amplitudeLevel(0))
    assertEquals(0f, AudioRecordingPolicy.amplitudeLevel(-3))
    assertEquals(1f, AudioRecordingPolicy.amplitudeLevel(32_767))
    assertEquals(1f, AudioRecordingPolicy.amplitudeLevel(99_999))
    val quiet = AudioRecordingPolicy.amplitudeLevel(327) // 约 -40 dB
    val loud = AudioRecordingPolicy.amplitudeLevel(3_276) // 约 -20 dB
    assertTrue(quiet in 0f..1f && loud in 0f..1f && loud > quiet)
  }

  // ---- AudioPlaybackPolicy ----

  @Test
  fun playbackToggleRequiresSource() {
    val idle = AudioPlaybackPolicy.Phase.IDLE
    val playing = AudioPlaybackPolicy.Phase.PLAYING
    val paused = AudioPlaybackPolicy.Phase.PAUSED
    assertEquals(paused, AudioPlaybackPolicy.toggle(playing, hasSource = true))
    assertEquals(playing, AudioPlaybackPolicy.toggle(paused, hasSource = true))
    assertEquals(idle, AudioPlaybackPolicy.toggle(idle, hasSource = true))
    assertEquals(paused, AudioPlaybackPolicy.toggle(paused, hasSource = false)) // 无源切换是 no-op
    assertEquals(idle, AudioPlaybackPolicy.onCompleted(playing))
  }

  @Test
  fun seekTargetClampsToDuration() {
    assertEquals(0L, AudioPlaybackPolicy.seekTarget(-10, 5_000))
    assertEquals(4_000L, AudioPlaybackPolicy.seekTarget(9_999, 4_000))
    assertEquals(9_999L, AudioPlaybackPolicy.seekTarget(9_999, null))
    // duration 0 视为未知 → 只做非负夹取。
    assertEquals(5_000L, AudioPlaybackPolicy.seekTarget(5_000, 0))
  }

  @Test
  fun segmentSeekUsesSegmentStart() {
    assertEquals(
      3_200L,
      AudioPlaybackPolicy.seekTargetForSegment(TranscriptSegment(3_200, 7_800, "b"), 10_000),
    )
  }

  @Test
  fun progressFractionClamped() {
    assertEquals(0f, AudioPlaybackPolicy.progressFraction(0, 0))
    assertEquals(0.5f, AudioPlaybackPolicy.progressFraction(500, 1_000))
    assertEquals(1f, AudioPlaybackPolicy.progressFraction(2_000, 1_000))
    assertEquals(0f, AudioPlaybackPolicy.progressFraction(-1, 1_000))
  }

  @Test
  fun clockLabelFormats() {
    assertEquals("00:00", AudioPlaybackPolicy.clockLabel(0))
    assertEquals("00:03", AudioPlaybackPolicy.clockLabel(3_200))
    assertEquals("1:00:00", AudioPlaybackPolicy.clockLabel(3_600_000))
  }
}
