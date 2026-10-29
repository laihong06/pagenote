package io.pagenote.android

import kotlinx.serialization.json.*

internal data class BookAppearance(
  val title: String? = null,
  val cover: String = "default",
  val asset: String? = null,
  val page: Int = 0,
  val paper: String = "sage",
) {
  fun titleOr(original: String): String = title?.trim()?.takeIf(String::isNotEmpty) ?: original
  fun resolvedCover(extension: String): String = when {
    cover == "default" -> if (extension == "pdf") "pdf" else "paper"
    cover == "pdf" && extension != "pdf" -> "paper"
    else -> cover
  }

  fun encode(): String {
    validate()
    return buildJsonObject {
      put("version", 1)
      title?.let { put("title", it) }
      put("cover", cover)
      asset?.let { put("asset", it) }
      put("page", page)
      put("paper", paper)
    }.toString()
  }

  private fun validate() {
    require(title == null || title.length <= 200) { "Title exceeds 200 characters" }
    require(cover in setOf("default", "paper", "pdf", "image")) { "Unknown cover type" }
    require(page >= 0) { "Invalid PDF cover page" }
    require(paper in PAPER_COLORS) { "Unknown paper color" }
    require(asset == null || (asset.isNotBlank() && asset.length <= 200 &&
      asset != "." && asset != ".." && asset.none { it == '/' || it == '\\' || it.isISOControl() })) { "Unsafe cover asset" }
    require(cover != "image" || asset != null) { "Missing cover image" }
  }

  companion object {
    val PAPER_COLORS = listOf("sage", "rose", "sky", "graphite")
    fun decode(text: String): BookAppearance {
      try {
        val json = Json.parseToJsonElement(text).jsonObject
        require(json["version"]?.jsonPrimitive?.int == 1) { "Unknown bookshelf metadata version" }
        return BookAppearance(
          json["title"]?.jsonPrimitive?.content,
          json["cover"]?.jsonPrimitive?.content ?: "default",
          json["asset"]?.jsonPrimitive?.content,
          json["page"]?.jsonPrimitive?.int ?: 0,
          json["paper"]?.jsonPrimitive?.content ?: "sage",
        ).also { it.validate() }
      } catch (error: Exception) {
        throw IllegalArgumentException("Invalid bookshelf metadata: ${error.message}", error)
      }
    }
  }
}

internal fun bookshelfMatches(title: String, query: String): Boolean = title.contains(query.trim(), ignoreCase = true)
