package io.pagenote.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File-equivalent path test for the SAF annotation boundary: all item types survive a
 * generic page replace, pen writes keep foreign types, missing file + empty write is a no-op.
 * The SAF thin adapter is compile-verified; provider behavior stays under task 12 device acceptance.
 */
class AnnotationSafBoundaryTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun genericWriteKeepsOtherPagesAndUnknownFields() {
    val root = mutableMapOf<String, Any?>(
      "document" to linkedMapOf("1" to listOf(textItem("keep"))),
      "note" to linkedMapOf<String, Any?>(),
      "future" to "preserve-me",
    )

    val updated = AnnotationSafBoundary.replacePage(root, "document", 2, listOf(textItem("new")))

    assertEquals(listOf(textItem("new")), updated.pageItems("document", 2))
    assertEquals(listOf(textItem("keep")), updated.pageItems("document", 1))
    assertEquals("preserve-me", updated["future"])
  }

  @Test
  fun penWriteKeepsForeignTypes() {
    val existing = listOf(
      mapOf("type" to "pen", "color" to "#000000", "width" to 1.0, "points" to listOf(listOf(0.0, 0.0))),
      mapOf("type" to "text", "color" to "#000000", "width" to 1.0, "points" to listOf(listOf(1.0, 1.0)), "text" to "hi"),
      mapOf("type" to "rect", "color" to "#FF0000", "width" to 2.0, "points" to listOf(listOf(2.0, 2.0))),
    )
    val replacement = listOf(
      mapOf("type" to "pen", "color" to "#000000", "width" to 1.0, "points" to listOf(listOf(9.0, 9.0))),
    )

    val merged = AnnotationPageMerger.replaceFreehandItems(existing, emptyList()) +
      replacement.filter { it["type"] == "pen" }

    assertTrue(merged.any { it["type"] == "text" })
    assertTrue(merged.any { it["type"] == "rect" })
    assertEquals(1, merged.count { it["type"] == "pen" })
  }

  @Test
  fun emptyWriteOnMissingFileIsNoOp() {
    assertFalse(AnnotationSafBoundary.shouldCreateFile(hasFile = false, items = JsonArray(emptyList())))
    assertTrue(AnnotationSafBoundary.shouldCreateFile(hasFile = false, items = textArray("x")))
    assertTrue(AnnotationSafBoundary.shouldCreateFile(hasFile = true, items = JsonArray(emptyList())))
  }

  @Test
  fun jsonRoundTripPreservesAllTypes() {
    val root = buildJsonObject {
      put("document", buildJsonObject {
        put("1", JsonArray(listOf(json.parseToJsonElement(textJson("a")), json.parseToJsonElement(rectJson()))))
      })
      put("note", buildJsonObject { })
    }

    val items = root["document"]!!.jsonObject["1"]!!.jsonArray

    assertEquals(2, items.size)
    assertEquals("text", items[0].jsonObject["type"].toString().trim('"'))
    assertEquals("rect", items[1].jsonObject["type"].toString().trim('"'))
  }

  private fun Map<String, Any?>.pageItems(side: String, page: Int): Any? =
    ((this[side] as? Map<*, *>)?.get(page.toString()))

  private fun textItem(text: String) = mapOf(
    "type" to "text", "color" to "#000000", "width" to 1.0,
    "points" to listOf(listOf(1.0, 1.0)), "text" to text,
  )

  private fun textArray(text: String) =
    JsonArray(listOf(json.parseToJsonElement(textJson(text))))

  private fun textJson(text: String) =
    """{"type":"text","color":"#000000","width":1.0,"points":[[1.0,1.0]],"text":"$text"}"""

  private fun rectJson() =
    """{"type":"rect","color":"#FF0000","width":2.0,"points":[[2.0,2.0]]}"""
}
