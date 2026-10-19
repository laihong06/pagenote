package io.pagenote.core.project

import java.io.File

/** Desktop projects.mjs exportAssets/importAssets mirror for shared contract tests. */
object ProjectAssets {
  private val EXPORT = Regex("/attachments/([a-f0-9-]+\\.[a-z0-9]+)", RegexOption.IGNORE_CASE)
  private val IMPORT = Regex("(?<!/)assets/([a-f0-9-]+\\.[a-z0-9]+)", RegexOption.IGNORE_CASE)

  fun exportAttachments(text: String, uploadsDir: File, projectRoot: File): String {
    val names = EXPORT.findAll(text).map { it.groupValues[1] }.toSet()
    names.forEach { name ->
      val segments = ProjectValidation.resolveLinkSegments("src/assets/$name")
      val destination = segments.fold(projectRoot, ::File)
      val source = File(uploadsDir, name)
      if (source.isFile) {
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
      }
    }
    return EXPORT.replace(text) { "assets/${it.groupValues[1]}" }
  }

  fun importAssets(text: String, projectRoot: File, uploadsDir: File): String {
    val names = IMPORT.findAll(text).map { it.groupValues[1] }.toSet()
    names.forEach { name ->
      val segments = ProjectValidation.resolveLinkSegments("src/assets/$name")
      val source = segments.fold(projectRoot, ::File)
      if (source.isFile) {
        uploadsDir.mkdirs()
        source.copyTo(File(uploadsDir, name), overwrite = true)
      }
    }
    return IMPORT.replace(text) { "/attachments/${it.groupValues[1]}" }
  }
}
