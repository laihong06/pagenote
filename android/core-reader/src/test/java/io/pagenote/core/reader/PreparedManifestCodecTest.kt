package io.pagenote.core.reader

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedManifestCodecTest {
  private fun manifest() = PreparedManifest(
    version = 2,
    documentId = "doc-1",
    page = 6,
    sourceRevision = "c".repeat(64),
    kind = "pdf",
    originalPreserved = true,
    region = PageRegion(12.5, 30.0, 450.0, 600.0),
    cacheKey = "k".repeat(64),
    width = 1200,
    height = 1600,
    scale = 1.5,
    nativeCharacters = 10,
    ocrCharacters = 20,
    textCharacters = 30,
    blockCount = 4,
    tableCount = 2,
    ocr = JsonObject(mapOf("engine" to JsonPrimitive("offline"), "extra" to JsonPrimitive(true))),
    warnings = listOf("review crop"),
    visualReviewRequired = true,
    files = listOf("src/prepared/6-key/page.png", "src/prepared/6-key/blocks.json"),
    visuals = JsonArray(listOf(JsonObject(mapOf("kind" to JsonPrimitive("region"))))),
  )

  @Test
  fun versionTwoManifest_roundTripsEveryContractField() {
    val expected = manifest()
    assertEquals(expected, PreparedManifestCodec.decode(PreparedManifestCodec.encode(expected)))
    assertTrue(PreparedManifestStore.isHit(expected, expected.copy()))
    assertFalse(PreparedManifestStore.isHit(expected, expected.copy(page = expected.page + 1)))
    assertFalse(PreparedManifestStore.isHit(expected, expected.copy(documentId = "other")))
    assertFalse(PreparedManifestStore.isHit(expected, expected.copy(region = PageRegion(0.0, 0.0, 1.0, 1.0))))
  }

  @Test
  fun manifestRejectsInvalidIdentityRegionAndCounts() {
    val expected = manifest()
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(page = 0))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(documentId = " "))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(sourceRevision = "not-a-revision"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(region = PageRegion(990.0, 0.0, 20.0, 1.0)))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(nativeCharacters = -1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(ocrCharacters = -1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(textCharacters = -1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(blockCount = -1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(tableCount = -1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(files = listOf("../outside.png")))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(files = listOf("C:\\outside.png")))
    }
    assertThrows(IllegalArgumentException::class.java) {
      PreparedManifestCodec.encode(expected.copy(files = listOf("C:/outside.png")))
    }
  }

  @Test
  fun unknownManifestFieldsAreIgnored() {
    val json = PreparedManifestCodec.encode(manifest()).dropLast(1) + ",\"futureField\":{\"value\":1}}"
    assertEquals(manifest(), PreparedManifestCodec.decode(json))
  }
}
