package io.pagenote.android

import io.pagenote.core.project.PageNotes
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteHistoryMirrorTest {
  private val projectId = "b".repeat(64)

  @Test
  fun rejectsBadProjectId() {
    val filesDir = Files.createTempDirectory("pagenote-mirror-id").toFile()
    assertNull(NoteHistoryMirror.directory(filesDir, null))
    assertNull(NoteHistoryMirror.directory(filesDir, "not-hex"))
    assertTrue(NoteHistoryMirror.entries(filesDir, "not-hex", 1).isEmpty())
  }

  @Test
  fun snapshotRestoreRoundTripThroughMirror() {
    val filesDir = Files.createTempDirectory("pagenote-mirror").toFile()

    val created = NoteHistoryMirror.snapshotPage(filesDir, projectId, 1, "# v1")
    assertTrue(created != null)
    val duplicate = NoteHistoryMirror.snapshotPage(filesDir, projectId, 1, "# v1")
    assertNull(duplicate)

    val entries = NoteHistoryMirror.entries(filesDir, projectId, 1)
    assertEquals(1, entries.size)
    assertEquals("# v1", NoteHistoryMirror.readVersion(filesDir, projectId, entries[0].id))
    val restored = NoteHistoryMirror.restore(
      filesDir, projectId, entries[0].id, 1,
      PageNotes.revision("# now"), "# now",
    )
    assertEquals("# v1", restored)
  }

  @Test(expected = IllegalStateException::class)
  fun staleMirrorRestoreRejected() {
    val filesDir = Files.createTempDirectory("pagenote-mirror-stale").toFile()
    val created = NoteHistoryMirror.snapshotPage(filesDir, projectId, 2, "# v1")!!
    NoteHistoryMirror.restore(
      filesDir, projectId, created.name, 2,
      PageNotes.revision("# other"), "# now",
    )
  }
}
