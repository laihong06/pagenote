package io.pagenote.feature.annotations

import java.nio.file.Files
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewRecordTest {
  @Test
  fun storeRoundTripKeepsDetailFields() {
    val dir = Files.createTempDirectory("pagenote-review-record").toFile()
    val stored = ReviewStore.createDetails(
      dir,
      ReviewCreate(
        side = "document",
        kind = "selection",
        page = 2,
        instruction = "补一个例子",
        quote = "原文引用",
        context = "上下文",
        revision = "r".repeat(64),
        anchor = buildJsonObject { put("start", JsonPrimitive(3)) },
      ),
    )
    val record = ReviewRecords.fromJson(stored)
    assertEquals("原文引用", record.quote)
    assertEquals("上下文", record.context)
    assertEquals("r".repeat(64), record.revision)
    assertEquals(3, record.anchor?.jsonObject?.get("start")?.jsonPrimitive?.content?.toInt())
    assertEquals("open", record.status)
    val back = ReviewRecords.toJson(record)
    assertEquals(stored.jsonObject["id"]?.jsonPrimitive?.content, back["id"]?.jsonPrimitive?.content)
    assertEquals("补一个例子", back["instruction"]?.jsonPrimitive?.content)
  }

  @Test
  fun nullRevisionAndAnchorSurvive() {
    val dir = Files.createTempDirectory("pagenote-review-record").toFile()
    val stored = ReviewStore.create(dir, "note", "overall", 1, "整体润色")
    val record = ReviewRecords.fromJson(stored)
    assertNull(record.revision)
    assertNull(record.anchor)
    assertNull(record.updated)
    assertNull(record.boardPosition)
  }
}
