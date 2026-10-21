package io.pagenote.feature.annotations

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HandoffStoreTest {
  private val noteRevision = "a".repeat(64)
  private val sourceRevision = "b".repeat(64)

  @Test
  fun missingHandoffIsNotStale() {
    val dir = Files.createTempDirectory("pagenote-handoffs").toFile()
    val view = HandoffStore.view(File(dir, "ai-handoffs.json"), 1, noteRevision, sourceRevision)
    assertNull(view.record)
    assertFalse(view.stale)
  }

  @Test
  fun matchingRevisionsAreFresh() {
    val dir = Files.createTempDirectory("pagenote-handoffs").toFile()
    HandoffStore.write(dir, 2, "summary", noteRevision, sourceRevision, noteRevision)
    val view = HandoffStore.view(File(dir, "ai-handoffs.json"), 2, noteRevision, sourceRevision)
    assertFalse(view.stale)
    assertEquals("summary", view.record?.summary)
  }

  @Test
  fun eitherRevisionMismatchIsStale() {
    val dir = Files.createTempDirectory("pagenote-handoffs").toFile()
    HandoffStore.write(dir, 1, "summary", noteRevision, sourceRevision, noteRevision)
    val file = File(dir, "ai-handoffs.json")
    assertTrue(HandoffStore.view(file, 1, "c".repeat(64), sourceRevision).stale)
    assertTrue(HandoffStore.view(file, 1, noteRevision, "d".repeat(64)).stale)
  }

  @Test
  fun writeRejectsStaleExpectedRevision() {
    val dir = Files.createTempDirectory("pagenote-handoffs").toFile()
    try {
      HandoffStore.write(dir, 1, "summary", "e".repeat(64), sourceRevision, noteRevision)
      fail("expected expectedRevision conflict")
    } catch (_: IllegalStateException) {
    }
    try {
      HandoffStore.write(dir, 1, "summary", "bad", sourceRevision, "bad")
      fail("expected bad revision rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun deletePageOnlyRemovesTargetPage() {
    val dir = Files.createTempDirectory("pagenote-handoffs").toFile()
    HandoffStore.write(dir, 1, "p1", noteRevision, sourceRevision, noteRevision)
    HandoffStore.write(dir, 2, "p2", noteRevision, sourceRevision, noteRevision)
    assertTrue(HandoffStore.deletePage(dir, 1))
    val file = File(dir, "ai-handoffs.json")
    assertNull(HandoffStore.readAll(file)[1])
    assertEquals("p2", HandoffStore.readAll(file)[2]?.summary)
    assertFalse(HandoffStore.deletePage(dir, 1))
  }

  @Test
  fun deletePageOnMissingFileIsNoop() {
    val dir = Files.createTempDirectory("pagenote-handoffs").toFile()
    assertFalse(HandoffStore.deletePage(dir, 1))
    assertFalse(File(dir, "ai-handoffs.json").exists())
  }
}
