package io.pagenote.android

import io.pagenote.core.project.ProjectManifest
import io.pagenote.core.project.ProjectStore

internal object NewProjectManifestPolicy {
  fun create(name: String, pdfDisplayName: String? = null): ProjectManifest {
    val manifest = ProjectStore.newManifest(name)
    return if (pdfDisplayName == null) {
      manifest
    } else {
      manifest.copy(
        left = "src/document.pdf",
        leftName = pdfDisplayName.ifBlank { "document.pdf" },
      )
    }
  }
}
