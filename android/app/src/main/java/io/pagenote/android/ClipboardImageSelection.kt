package io.pagenote.android

import java.net.URI

internal data class ClipboardImageCandidate(val uri: String?, val mimeType: String?)

internal object ClipboardImageSelection {
  fun isContentUri(uri: String?): Boolean {
    val value = uri?.takeIf(String::isNotBlank) ?: return false
    val parsed = runCatching { URI(value) }.getOrNull() ?: return false
    return parsed.scheme.equals("content", ignoreCase = true) &&
      !parsed.isOpaque &&
      !parsed.rawAuthority.isNullOrBlank()
  }

  fun firstSupported(candidates: Iterable<ClipboardImageCandidate>): ClipboardImageCandidate? =
    candidates.firstOrNull { candidate ->
      isContentUri(candidate.uri) &&
        candidate.mimeType?.let(ProjectImageImporter::extensionForMimeType) != null
    }

  fun fallbackDisplayName(mimeType: String): String? =
    ProjectImageImporter.extensionForMimeType(mimeType)?.let { "clipboard-image.$it" }
}
