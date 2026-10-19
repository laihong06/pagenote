package io.pagenote.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object ProjectAttachmentPolicy {
  private val extensions = setOf(
    "pdf", "png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "md", "markdown", "txt", "docx", "pptx",
  )
  private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp")
  private val assetNamePattern = Regex(
    "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.([a-z0-9]+)",
    RegexOption.IGNORE_CASE,
  )

  enum class DeleteEligibility {
    ALLOWED,
    REFERENCED,
    UNKNOWN_REFERENCES,
    UNSAFE_NAME,
  }

  fun extensionForDisplayName(displayName: String): String? {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return extension.takeIf(extensions::contains)
  }

  fun isSafeAssetName(assetName: String): Boolean {
    val extension = assetNamePattern.matchEntire(assetName)?.groupValues?.getOrNull(1)?.lowercase()
      ?: return false
    return extension in extensions
  }

  fun createImageReference(altText: String, assetName: String): String {
    require(isSafeAssetName(assetName) && extension(assetName) in imageExtensions) {
      "Invalid project image asset name"
    }
    return MarkdownImageInsertion.createReference(altText, assetName)
  }

  fun deletionEligibility(
    assetName: String,
    allPageNotes: String,
    annotationsJson: String?,
  ): DeleteEligibility {
    if (!isSafeAssetName(assetName)) return DeleteEligibility.UNSAFE_NAME
    if (containsMarkdownReference(allPageNotes, assetName)) return DeleteEligibility.REFERENCED
    if (annotationsJson.isNullOrBlank()) return DeleteEligibility.ALLOWED

    val annotations = runCatching { Json.parseToJsonElement(annotationsJson) }.getOrNull()
      ?: return DeleteEligibility.UNKNOWN_REFERENCES
    return if (containsAnnotationReference(annotations, assetName)) {
      DeleteEligibility.REFERENCED
    } else {
      DeleteEligibility.ALLOWED
    }
  }

  private fun extension(assetName: String): String = assetName.substringAfterLast('.').lowercase()

  private fun containsMarkdownReference(markdown: String, assetName: String): Boolean {
    val reference = Regex(
      "(?:assets/|/attachments/)${Regex.escape(assetName)}(?![a-z0-9_.-])",
      RegexOption.IGNORE_CASE,
    )
    return reference.containsMatchIn(markdown)
  }

  private fun containsAnnotationReference(element: JsonElement, assetName: String): Boolean = when (element) {
    is JsonObject -> {
      val url = element["url"]?.jsonPrimitive?.contentOrNull
      PdfAnnotationImageReference.assetName(url.orEmpty()) == assetName ||
        element.values.any { containsAnnotationReference(it, assetName) }
    }
    is JsonArray -> element.any { containsAnnotationReference(it, assetName) }
    else -> false
  }
}
