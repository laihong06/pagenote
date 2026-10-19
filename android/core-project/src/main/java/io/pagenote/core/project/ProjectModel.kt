package io.pagenote.core.project

data class ProjectManifest(
  val format: String,
  val version: Int,
  val id: String,
  val name: String,
  val created: String? = null,
  val modified: String? = null,
  val left: String,
  val right: String,
  val leftName: String? = null,
  val preview: String? = null,
)

object ProjectValidation {
  private val ID = Regex("^[a-f0-9]{64}$")

  fun validate(manifest: ProjectManifest) {
    require(manifest.format == "pagenote-project" || manifest.format == "page-notes-project") { "not a pagenote project" }
    require(manifest.version == 1) { "unsupported project version" }
    require(ID.matches(manifest.id)) { "bad project id" }
    require(manifest.name.isNotBlank()) { "bad project name" }
    resolveLinkSegments(manifest.left)
    resolveLinkSegments(manifest.right)
    manifest.preview?.let(::resolveLinkSegments)
  }

  fun resolveLinkSegments(link: String): List<String> {
    require(link.isNotBlank() && !link.startsWith('/') && !link.contains('\u0000')) {
      "project link must be relative"
    }
    val segments = mutableListOf<String>()
    link.split('/').forEach { segment ->
      when (segment) {
        "", "." -> Unit
        ".." -> {
          require(segments.isNotEmpty()) { "project link escapes project root" }
          segments.removeAt(segments.lastIndex)
        }
        else -> segments.add(segment)
      }
    }
    require(segments.size >= 2 && segments.first() == "src") {
      "project link must stay under src/"
    }
    return segments
  }

  fun kindOf(left: String): String {
    val ext = left.substringAfterLast('.', "").lowercase()
    return if (ext == "md" || ext == "markdown") "markdown" else "pdf"
  }
}
