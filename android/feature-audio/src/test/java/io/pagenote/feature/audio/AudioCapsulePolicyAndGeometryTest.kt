package io.pagenote.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCapsulePolicyAndGeometryTest {
  private fun capsuleAt(
    x: Double,
    y: Double,
    createdAt: Long = 0,
    page: Int = 1,
  ): AudioCapsule = AudioCapsule(
    id = "00000000-0000-0000-0000-00000000000${page}",
    page = page,
    anchorX = x,
    anchorY = y,
    asset = "00000000-0000-0000-0000-00000000000${page}.m4a",
    durationMs = 1_000,
    createdAtMillis = createdAt,
    transcript = null,
    transcriptStatus = AudioTranscriptStatus.NONE,
    segments = emptyList(),
  )

  @Test
  fun clampAnchorKeepsCapsuleInsidePage() {
    assertEquals(0.0 to 0.0, AudioCapsulePolicy.clampAnchor(-10.0, -5.0))
    assertEquals(
      (1000.0 - AudioCapsulePolicy.CAPSULE_WIDTH) to 12.0,
      AudioCapsulePolicy.clampAnchor(1200.0, 12.0),
    )
    assertEquals(30.0 to 0.0, AudioCapsulePolicy.clampAnchor(30.0, 0.0))
  }

  @Test
  fun clampAnchorRejectsNonFinite() {
    assertNull(AudioCapsulePolicy.clampAnchor(Double.NaN, 0.0))
    assertNull(AudioCapsulePolicy.clampAnchor(0.0, Double.POSITIVE_INFINITY))
    assertNull(AudioCapsulePolicy.clampAnchor(2_000_000.0, 0.0))
  }

  @Test
  fun hitTestMatchesInsideRectWithTolerance() {
    val capsule = capsuleAt(100.0, 200.0, createdAt = 1)
    val rect = AudioCapsuleGeometry.rect(capsule)
    assertEquals(100.0, rect[0], 0.0)
    assertEquals(200.0, rect[1], 0.0)
    assertEquals(100.0 + AudioCapsulePolicy.CAPSULE_WIDTH, rect[2], 0.0)
    assertEquals(200.0 + AudioCapsulePolicy.CAPSULE_HEIGHT, rect[3], 0.0)

    assertEquals(
      capsule,
      AudioCapsuleGeometry.hitTest(listOf(capsule), 100.0 + AudioCapsulePolicy.CAPSULE_WIDTH / 2, 220.0),
    )
    // 容差边（框外 HIT_TOLERANCE 内）仍然命中。
    assertEquals(
      capsule,
      AudioCapsuleGeometry.hitTest(
        listOf(capsule),
        100.0 - AudioCapsulePolicy.HIT_TOLERANCE / 2,
        220.0,
      ),
    )
    assertNull(
      AudioCapsuleGeometry.hitTest(
        listOf(capsule),
        100.0 - AudioCapsulePolicy.HIT_TOLERANCE - 1,
        220.0,
      ),
    )
  }

  @Test
  fun hitTestPrefersLatestCapsuleWhenOverlapping() {
    val older = capsuleAt(100.0, 200.0, createdAt = 1)
    val newer = capsuleAt(110.0, 210.0, createdAt = 2)
    assertEquals(
      newer,
      AudioCapsuleGeometry.hitTest(listOf(older, newer), 115.0, 215.0),
    )
  }

  @Test
  fun hitTestRejectsNonFiniteInput() {
    assertNull(AudioCapsuleGeometry.hitTest(listOf(capsuleAt(0.0, 0.0)), Double.NaN, 0.0))
  }

  @Test
  fun recordingDurationGuards() {
    assertFalse(AudioCapsulePolicy.shouldKeepRecording(0))
    assertFalse(AudioCapsulePolicy.shouldKeepRecording(AudioCapsulePolicy.MIN_DURATION_MS - 1))
    assertTrue(AudioCapsulePolicy.shouldKeepRecording(AudioCapsulePolicy.MIN_DURATION_MS))
    assertFalse(AudioCapsulePolicy.shouldKeepRecording(AudioCapsulePolicy.MAX_DURATION_MS + 1))
  }

  @Test
  fun pixelMappingUsesPageWidthScale() {
    val (x, y) = AudioCapsuleGeometry.toPixels(500.0, 250.0, 0.5)
    assertEquals(250.0, x, 0.0)
    assertEquals(125.0, y, 0.0)
  }

  @Test
  fun indexUpsertRemoveAndQueries() {
    val a = capsuleAt(0.0, 0.0, createdAt = 1, page = 1)
    val b = capsuleAt(10.0, 10.0, createdAt = 2, page = 2)
    var index = AudioCapsuleIndex(listOf(a, b))
    assertEquals(2, index.size)
    assertEquals(listOf(a, b), index.orderedForList())
    assertEquals(listOf(b), index.forPage(2))

    val updated = a.copy(transcriptStatus = AudioTranscriptStatus.PENDING)
    index = index.upsert(updated)
    assertEquals(updated, index.byId(a.id))
    assertEquals(2, index.size)

    index = index.remove(b.id)
    assertNull(index.byId(b.id))
    assertEquals(listOf(updated), index.orderedForList())
  }
}
