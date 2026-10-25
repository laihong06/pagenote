package io.pagenote.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCapsuleCodecTest {
  private val id = "01234567-89ab-cdef-0123-456789abcdef"
  private val asset = "$id.m4a"

  private fun capsule(
    transcriptStatus: AudioTranscriptStatus = AudioTranscriptStatus.NONE,
    transcript: String? = null,
    segments: List<TranscriptSegment> = emptyList(),
    suffix: String = "000000000001",
  ): AudioCapsule = AudioCapsule(
    id = "01234567-89ab-cdef-0123-$suffix",
    page = 3,
    anchorX = 440.0,
    anchorY = 300.0,
    asset = "01234567-89ab-cdef-0123-$suffix.m4a",
    durationMs = 52_300,
    createdAtMillis = 1_730_000_000_000,
    transcript = transcript,
    transcriptStatus = transcriptStatus,
    segments = segments,
    language = if (transcriptStatus == AudioTranscriptStatus.OK) "zh" else null,
    model = if (transcriptStatus == AudioTranscriptStatus.OK) "zipformer-paraformer-zh-en" else null,
  )

  @Test
  fun roundTripPreservesAllFields() {
    val original = listOf(
      capsule(),
      capsule(
        transcriptStatus = AudioTranscriptStatus.OK,
        transcript = "第一行\n第二行",
        segments = listOf(
          TranscriptSegment(0, 3200, "第一行"),
          TranscriptSegment(3200, 7800, "第二行"),
        ),
        suffix = "000000000002",
      ),
      capsule(transcriptStatus = AudioTranscriptStatus.FAILED, suffix = "000000000003"),
    )
    val decoded = AudioCapsuleCodec.decode(AudioCapsuleCodec.encode(original))
    assertEquals(original, decoded)
  }

  @Test
  fun encodeProducesVersionOneManifest() {
    val encoded = AudioCapsuleCodec.encode(listOf(capsule()))
    assertTrue(encoded.contains("\"version\":1"))
    assertTrue(encoded.contains("\"transcriptStatus\":\"none\""))
    assertTrue(encoded.contains("\"asset\":\"01234567-89ab-cdef-0123-000000000001.m4a\""))
  }

  @Test
  fun newCapsuleAssignsUuidAndValidates() {
    val created = AudioCapsuleCodec.newCapsule(
      page = 1,
      anchorX = 10.0,
      anchorY = 5.0,
      asset = asset,
      durationMs = 1_000,
      createdAtMillis = 42,
    )
    assertTrue(AudioCapsulePolicy.isValidId(created.id))
    assertEquals(AudioTranscriptStatus.NONE, created.transcriptStatus)
  }

  @Test
  fun newCapsuleRejectsBadAsset() {
    try {
      AudioCapsuleCodec.newCapsule(1, 0.0, 0.0, "../escape.m4a", 1_000, 42)
      throw AssertionError("expected rejection")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("asset"))
    }
  }

  @Test
  fun decodeSkipsInvalidEntriesAndDuplicates() {
    val good = capsule()
    val other = capsule(suffix = "000000000009")
    // 两份合法条目 + 手工复制的同 id 副本（同 id 保留首条）+ 非法条目。
    val withDuplicate = AudioCapsuleCodec.encode(listOf(good, other))
      .replace(other.id, good.id)
    val encoded = withDuplicate.replace(
      "\"capsules\":[",
      """ "capsules":[
         {"id":"bad","page":1},
         {"id":"${good.id}","page":0,"anchorX":0,"anchorY":0,"asset":"x.m4a","durationMs":1,"createdAt":0},""",
    )
    val decoded = AudioCapsuleCodec.decode(encoded)
    assertEquals(listOf(good), decoded)
  }

  @Test
  fun decodeRejectsUnknownVersion() {
    try {
      AudioCapsuleCodec.decode("""{"version":99,"capsules":[]}""")
      throw AssertionError("expected rejection")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("version"))
    }
  }

  @Test
  fun decodeAcceptsMissingVersionAndCapsules() {
    assertTrue(AudioCapsuleCodec.decode("{}").isEmpty())
    assertTrue(AudioCapsuleCodec.decode("""{"version":1}""").isEmpty())
  }

  @Test
  fun okStatusRequiresTranscriptAndSegments() {
    try {
      AudioCapsuleCodec.validate(listOf(capsule(transcriptStatus = AudioTranscriptStatus.OK)))
      throw AssertionError("expected rejection")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("segments"))
    }
  }

  @Test
  fun segmentsMustBeSortedAndNonEmpty() {
    try {
      AudioCapsuleCodec.validate(
        listOf(
          capsule(
            transcriptStatus = AudioTranscriptStatus.OK,
            transcript = "b a",
            segments = listOf(
              TranscriptSegment(100, 200, "b"),
              TranscriptSegment(50, 90, "a"),
            ),
          ),
        ),
      )
      throw AssertionError("expected rejection")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("sorted"))
    }
  }

  @Test
  fun decodeDropsOkCapsuleWithoutSegments() {
    val encoded = AudioCapsuleCodec.encode(listOf(capsule()))
      .replace("\"transcriptStatus\":\"none\"", "\"transcriptStatus\":\"ok\"")
    assertTrue(AudioCapsuleCodec.decode(encoded).isEmpty())
  }

  @Test
  fun withTranscriptFillsOkResultOrRejectsDirtyData() {
    val base = capsule()
    val ok = AudioCapsulePolicy.withTranscript(
      base,
      AudioTranscriptStatus.OK,
      "你好",
      listOf(TranscriptSegment(0, 900, "你好")),
      "zh",
      "zipformer-paraformer-zh-en",
    )!!
    assertEquals("你好", ok.transcript)
    assertEquals(1, ok.segments.size)

    assertNull(
      AudioCapsulePolicy.withTranscript(
        base,
        AudioTranscriptStatus.OK,
        "你好",
        emptyList(), // OK 状态必须有片段
        "zh",
        "zipformer-paraformer-zh-en",
      ),
    )
  }

  @Test
  fun capacityLimitsEnforced() {
    try {
      AudioCapsuleCodec.validate(List(AudioCapsuleCodec.MAX_CAPSULES + 1) { index ->
        capsule().copy(id = "00000000-0000-0000-0000-%012x".format(index))
      })
      throw AssertionError("expected rejection")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("too many"))
    }
  }
}
