package io.pagenote.core.project

import java.io.File
import java.util.UUID

object ProjectStore {
  fun newManifest(name: String): ProjectManifest {
    require(name.isNotBlank() && !Regex("[<>:\"/\\\\|?*]").containsMatchIn(name)) { "bad project name" }
    return ProjectManifest(
      format = "pagenote-project",
      version = 1,
      id = sha256(UUID.randomUUID().toString()),
      name = name,
      created = java.time.Instant.now().toString(),
      left = "src/left.md",
      right = "src/right.md",
    )
  }

  fun create(root: File, name: String): File {
    val manifest = newManifest(name)
    val dir = File(root, name)
    require(!dir.exists()) { "project already exists" }
    val src = File(dir, "src")
    val assets = File(src, "assets")
    assets.mkdirs()
    File(src, "left.md").writeText("# ${manifest.name}\n", Charsets.UTF_8)
    File(src, "right.md").writeText("", Charsets.UTF_8)
    ProjectValidation.validate(manifest)
    File(dir, "project.pagenote").writeText(manifestJson(manifest), Charsets.UTF_8)
    return dir
  }

  fun readNotes(rightMd: File): Map<Int, String> {
    val pages = PageNotes.split(rightMd.readText(Charsets.UTF_8))
    return pages.mapIndexed { index, text -> index + 1 to text }.toMap()
  }

  fun writeNotes(rightMd: File, notes: Map<Int, String>) {
    val count = maxOf(1, *(notes.keys.toIntArray()))
    val pages = (1..count).map { notes[it].orEmpty() }
    atomicWrite(rightMd, PageNotes.join(pages))
  }

  fun saveNote(rightMd: File, page: Int, markdown: String, expectedRevision: String?) {
    require(page >= 1)
    val notes = readNotes(rightMd).toMutableMap()
    val old = notes[page].orEmpty()
    if (expectedRevision != null && PageNotes.revision(old) != expectedRevision) {
      throw IllegalStateException("note changed, read again")
    }
    if (old == markdown) return
    notes[page] = markdown
    writeNotes(rightMd, notes)
  }

  private fun atomicWrite(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + "." + UUID.randomUUID() + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (!tmp.renameTo(file)) {
      tmp.copyTo(file, overwrite = true)
      tmp.delete()
    }
  }

  private fun sha256(text: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }

  private fun manifestJson(m: ProjectManifest): String {
    return buildString {
      append("{\n")
      append("  \"format\": \"${m.format}\",\n")
      append("  \"version\": ${m.version},\n")
      append("  \"id\": \"${m.id}\",\n")
      append("  \"name\": \"${m.name}\",\n")
      append("  \"created\": \"${m.created}\",\n")
      append("  \"left\": \"${m.left}\",\n")
      append("  \"right\": \"${m.right}\"\n")
      append("}\n")
    }
  }
}
