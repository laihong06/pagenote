package io.pagenote.feature.annotations

import java.nio.file.Files
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReviewStoreTest {
  @Test
  fun createDeleteRoundTrip() {
    val dir = Files.createTempDirectory("pagenote-reviews").toFile()
    val first = ReviewStore.create(dir, "document", "selection", 2, "补一个例子")
    val second = ReviewStore.create(dir, "note", "region", 2, "重讲公式")
    assertEquals(1, first.jsonObject["number"]?.jsonPrimitive?.content?.toInt())
    assertEquals(2, second.jsonObject["number"]?.jsonPrimitive?.content?.toInt())
    assertEquals("open", first.jsonObject["status"]?.jsonPrimitive?.content)
    val id = first.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
    assertTrue(ReviewStore.delete(dir, id))
    assertFalse(ReviewStore.delete(dir, id))
  }

  @Test
  fun appliedRequiresSuccessfulRewrite() {
    val dir = Files.createTempDirectory("pagenote-reviews").toFile()
    val record = ReviewStore.create(dir, "document", "selection", 1, "补例子")
    val id = record.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
    try {
      ReviewStore.transition(dir, id, "applied", noteRewritten = false)
      fail("expected applied guard")
    } catch (_: IllegalArgumentException) {
    }
    val applied = ReviewStore.transition(dir, id, "applied", noteRewritten = true)
    assertEquals("applied", applied.jsonObject["status"]?.jsonPrimitive?.content)
  }

  @Test
  fun dismissedAndReopenFlow() {
    val dir = Files.createTempDirectory("pagenote-reviews").toFile()
    val record = ReviewStore.create(dir, "note", "overall", 1, "整体润色")
    val id = record.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
    assertEquals("dismissed", ReviewStore.transition(dir, id, "dismissed").jsonObject["status"]?.jsonPrimitive?.content)
    assertEquals("open", ReviewStore.transition(dir, id, "open").jsonObject["status"]?.jsonPrimitive?.content)
    try {
      ReviewStore.transition(dir, id, "done")
      fail("expected bad status rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun patchFieldLimitsMirrorDesktop() {
    val dir = Files.createTempDirectory("pagenote-reviews").toFile()
    val record = ReviewStore.create(dir, "document", "selection", 1, "初版")
    val id = record.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
    assertEquals("改后", ReviewStore.updateInstruction(dir, id, "改后").jsonObject["instruction"]?.jsonPrimitive?.content)
    try {
      ReviewStore.updateInstruction(dir, id, "  ")
      fail("expected blank instruction rejection")
    } catch (_: IllegalArgumentException) {
    }
    ReviewStore.updateBoardPosition(dir, id, 10.0, 20.0)
    try {
      ReviewStore.updateBoardPosition(dir, id, -1.0, 0.0)
      fail("expected bad board position rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun createDetailsKeepsDesktopLimits() {
    val dir = Files.createTempDirectory("pagenote-reviews").toFile()
    val record = ReviewStore.createDetails(
      dir,
      io.pagenote.feature.annotations.ReviewCreate(
        side = "document",
        kind = "selection",
        page = 2,
        instruction = "补一个例子",
        quote = "q".repeat(100001),
        context = "c".repeat(1000001),
        revision = "r".repeat(64),
        anchor = buildJsonObject { put("start", JsonPrimitive(1)) },
      ),
    )
    assertEquals(100000, record.jsonObject["quote"]?.jsonPrimitive?.content?.length)
    assertEquals(1000000, record.jsonObject["context"]?.jsonPrimitive?.content?.length)
    assertEquals(1, record.jsonObject["anchor"]?.jsonObject?.get("start")?.jsonPrimitive?.content?.toInt())
    try {
      ReviewStore.createDetails(
        dir,
        io.pagenote.feature.annotations.ReviewCreate(
          side = "document",
          kind = "selection",
          page = 2,
          instruction = "过大 anchor",
          anchor = JsonPrimitive("x".repeat(500001)),
        ),
      )
      fail("expected anchor limit rejection")
    } catch (_: IllegalArgumentException) {
    }
  }
}
