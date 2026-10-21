package io.pagenote.android

import io.pagenote.core.project.NoteHistory
import io.pagenote.core.project.NoteVersion
import java.io.File

/**
 * File-backed per-page history mirror under app-private storage.
 *
 * SAF projects are not written in place for history; the app keeps a mirror keyed by the
 * project manifest id so read-only providers simply get no history directory.
 */
object NoteHistoryMirror {
  fun directory(filesDir: File, projectId: String?): File? {
    if (projectId.isNullOrBlank() || !projectId.matches(Regex("^[a-f0-9]{64}$"))) return null
    return File(File(filesDir, "note-history"), projectId)
  }

  fun snapshotPage(filesDir: File, projectId: String?, page: Int, markdown: String): File? {
    val dir = directory(filesDir, projectId) ?: return null
    return NoteHistory.snapshotOnce(dir, page, markdown)
  }

  fun entries(filesDir: File, projectId: String?, page: Int): List<NoteVersion> {
    val dir = directory(filesDir, projectId) ?: return emptyList()
    return NoteHistory.entries(dir, page)
  }

  fun restore(
    filesDir: File,
    projectId: String?,
    version: String,
    page: Int,
    expectedRevision: String,
    current: String,
  ): String {
    val dir = directory(filesDir, projectId) ?: throw IllegalStateException("note history unavailable")
    return NoteHistory.restore(dir, version, page, expectedRevision, current)
  }

  fun readVersion(filesDir: File, projectId: String?, version: String): String {
    val dir = directory(filesDir, projectId) ?: throw IllegalStateException("note history unavailable")
    return File(dir, version).readText(Charsets.UTF_8)
  }
}
