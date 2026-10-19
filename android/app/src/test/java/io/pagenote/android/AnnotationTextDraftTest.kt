package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationTextDraftTest {
  @Test
  fun buildsTextItem() {
    val item = AnnotationTextDraft.toItem(10.0, 20.0, "#FF0000", 2.0, "hello")

    assertEquals("text", item["type"])
    assertEquals("#FF0000", item["color"])
    assertEquals(2.0, item["width"])
    assertEquals(listOf(listOf(10.0, 20.0)), item["points"])
    assertEquals("hello", item["text"])
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsBlankText() {
    AnnotationTextDraft.toItem(10.0, 20.0, "#FF0000", 2.0, "  ")
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsBadColor() {
    AnnotationTextDraft.toItem(10.0, 20.0, "red", 2.0, "hi")
  }

  @Test
  fun truncatesLongText() {
    val item = AnnotationTextDraft.toItem(0.0, 0.0, "#000000", 1.0, "x".repeat(12000))

    assertEquals(10000, (item["text"] as String).length)
  }
}
