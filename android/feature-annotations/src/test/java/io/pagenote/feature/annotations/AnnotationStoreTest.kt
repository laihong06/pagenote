package io.pagenote.feature.annotations

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnnotationStoreTest {
  private val json = Json { ignoreUnknownKeys = true }

  private fun itemsPayload(): JsonArray =
    json.parseToJsonElement(
      """[{"type":"pen","color":"#ff0000","width":2.0,"points":[[10,10],[20,20]]}]""",
    ).jsonArray

  @Test
  fun writeThenReadBySideAndPage() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    assertTrue(AnnotationStore.writePage(dir, "document", 1, itemsPayload()))
    val page = AnnotationStore.readPage(java.io.File(dir, "annotations.json"), "document", 1)
    assertEquals(itemsPayload(), page)
    assertEquals(JsonArray(emptyList()), AnnotationStore.readPage(java.io.File(dir, "annotations.json"), "note", 1))
  }

  @Test
  fun identicalContentIsDeduped() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    assertTrue(AnnotationStore.writePage(dir, "note", 2, itemsPayload()))
    val history = java.io.File(dir, "annotation-history")
    val backupsAfterFirst = history.listFiles()?.size ?: 0
    assertFalse(AnnotationStore.writePage(dir, "note", 2, itemsPayload()))
    assertEquals(backupsAfterFirst, history.listFiles()?.size ?: 0)
  }

  @Test
  fun fullReplaceKeepsOtherPages() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    AnnotationStore.writePage(dir, "document", 1, itemsPayload())
    val replacement = json.parseToJsonElement(
      """[{"type":"highlight","color":"#ffff00","width":8.0,"points":[[1,1],[2,2]]}]""",
    ).jsonArray
    assertTrue(AnnotationStore.writePage(dir, "document", 1, replacement))
    val file = java.io.File(dir, "annotations.json")
    assertEquals(replacement, AnnotationStore.readPage(file, "document", 1))
  }

  @Test
  fun deletePageOnlyRemovesTargetSideAndPage() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    AnnotationStore.writePage(dir, "document", 1, itemsPayload())
    AnnotationStore.writePage(dir, "note", 1, itemsPayload())
    assertTrue(AnnotationStore.deletePage(dir, "document", 1))
    val file = java.io.File(dir, "annotations.json")
    assertEquals(JsonArray(emptyList()), AnnotationStore.readPage(file, "document", 1))
    assertEquals(itemsPayload(), AnnotationStore.readPage(file, "note", 1))
    assertFalse(AnnotationStore.deletePage(dir, "document", 1))
  }

  @Test
  fun deletePageOnMissingFileIsNoop() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    assertFalse(AnnotationStore.deletePage(dir, "document", 1))
    assertFalse(java.io.File(dir, "annotations.json").exists())
  }

  @Test
  fun invalidItemsRejected() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    val bad = json.parseToJsonElement("""[{"type":"pen","color":"red","width":2.0,"points":[[1,1]]}]""").jsonArray
    try {
      AnnotationStore.writePage(dir, "document", 1, bad)
      fail("expected bad color rejection")
    } catch (_: IllegalArgumentException) {
    }
    try {
      AnnotationStore.writePage(dir, "paper", 1, itemsPayload())
      fail("expected bad side rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun shapeItemsRequirePoints() {
    val dir = Files.createTempDirectory("pagenote-annotations").toFile()
    listOf("line", "arrow", "rect", "ellipse").forEach { type ->
      val missing = json.parseToJsonElement(
        """[{"type":"$type","color":"#112233","width":2.0}]""",
      ).jsonArray
      try {
        AnnotationStore.writePage(dir, "document", 1, missing)
        fail("expected points rejection for $type")
      } catch (_: IllegalArgumentException) {
      }
      val ok = json.parseToJsonElement(
        """[{"type":"$type","color":"#112233","width":2.0,"points":[[1,2],[3,4]]}]""",
      ).jsonArray
      assertTrue(AnnotationStore.writePage(dir, "note", 1, ok))
    }
  }
}
