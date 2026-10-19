package io.pagenote.core.project

import java.io.File
import java.util.UUID

data class NoteVersion(val id: String, val created: Long, val bytes: Long)

object NoteHistory {
  private val NAME = Regex("^(\\d{13})-[a-f0-9\\-]{36}-page(\\d+)\\.md$")

  fun fileName(page: Int, stamp: Long = System.currentTimeMillis()): String {
    require(page >= 1)
    return "$stamp-${UUID.randomUUID()}-page$page.md"
  }

  fun snapshot(historyDir: File, page: Int, markdown: String): File {
    require(page >= 1)
    historyDir.mkdirs()
    val file = File(historyDir, fileName(page))
    file.writeText(markdown, Charsets.UTF_8)
    return file
  }

  /**
   * Snapshot unless the latest entry for [page] already holds identical content.
   * Returns the new file, or null when skipped as a duplicate.
   */
  fun snapshotOnce(historyDir: File, page: Int, markdown: String): File? {
    require(page >= 1)
    val latest = entries(historyDir, page).firstOrNull()
    if (latest != null && File(historyDir, latest.id).readText(Charsets.UTF_8) == markdown) {
      return null
    }
    return snapshot(historyDir, page, markdown)
  }

  fun entries(historyDir: File, page: Int): List<NoteVersion> {
    require(page >= 1)
    val files = historyDir.listFiles() ?: return emptyList()
    return files.mapNotNull { file ->
      val match = NAME.matchEntire(file.name) ?: return@mapNotNull null
      if (match.groupValues[2].toInt() != page) return@mapNotNull null
      NoteVersion(file.name, match.groupValues[1].toLong(), file.length())
    }.sortedByDescending { it.created }
  }

  fun restore(historyDir: File, version: String, page: Int, expectedRevision: String, current: String): String {
    val entry = entries(historyDir, page).find { it.id == version }
      ?: throw IllegalArgumentException("version not found")
    if (PageNotes.revision(current) != expectedRevision) {
      throw IllegalStateException("note changed, read again")
    }
    return File(historyDir, entry.id).readText(Charsets.UTF_8)
  }
}
