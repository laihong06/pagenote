package io.pagenote.android

import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PreparedManifest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedManifestApiVisibilityTest {
  @Test
  fun appCanConstructManifestAndReadPublicJsonFields() {
    val ocr = JsonObject(mapOf("engine" to JsonPrimitive("offline")))
    val visuals = JsonArray(listOf(JsonPrimitive("review")))
    val manifest = PreparedManifest(
      documentId = "doc",
      page = 1,
      sourceRevision = "a".repeat(64),
      kind = "pdf",
      region = PageRegion(0.0, 0.0, 1000.0, 1000.0),
      cacheKey = "b".repeat(64),
      width = 1200,
      height = 1600,
      scale = 1.0,
      ocr = ocr,
      visuals = visuals,
    )

    assertEquals("offline", manifest.ocr["engine"]?.let { (it as JsonPrimitive).content })
    assertEquals("review", manifest.visuals.single().let { (it as JsonPrimitive).content })
  }
}
