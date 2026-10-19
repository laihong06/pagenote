package io.pagenote.android

import io.pagenote.core.project.ProjectManifest
import io.pagenote.core.project.ProjectStore

internal object NewProjectManifestPolicy {
  private val invalidProjectNameCharacters = Regex("[<>:\"/\\\\|?*]")

  fun sourceExtension(displayName: String): String? {
    val trimmedName = displayName.trim()
    val extension = trimmedName.substringAfterLast('.', missingDelimiterValue = "")
      .lowercase()
    return extension.takeIf { it in setOf("pdf", "md", "markdown") }
  }

  fun suggestedProjectName(displayName: String): String {
    val trimmedName = displayName.trim()
    val baseName = if (sourceExtension(trimmedName) != null) {
      trimmedName.substringBeforeLast('.')
    } else {
      trimmedName
    }
    return baseName.replace(invalidProjectNameCharacters, "_")
      .trim()
      .ifBlank { "Imported document" }
  }

  fun create(name: String, sourceDisplayName: String? = null): ProjectManifest {
    val manifest = ProjectStore.newManifest(name)
    return if (sourceDisplayName == null) {
      manifest
    } else {
      val extension = sourceExtension(sourceDisplayName)
        ?: throw IllegalArgumentException("不支持的原文格式：$sourceDisplayName")
      manifest.copy(
        left = "src/document.$extension",
        leftName = sourceDisplayName,
      )
    }
  }
}
