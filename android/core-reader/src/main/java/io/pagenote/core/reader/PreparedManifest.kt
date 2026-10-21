package io.pagenote.core.reader

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class PreparedManifest(
  val version: Int = PreparedCache.VERSION,
  val documentId: String,
  val page: Int,
  val sourceRevision: String,
  val kind: String,
  val region: PageRegion,
  val cacheKey: String,
  val width: Int,
  val height: Int,
  val scale: Double,
  val originalPreserved: Boolean = true,
  val nativeCharacters: Int = 0,
  val ocrCharacters: Int = 0,
  val textCharacters: Int = 0,
  val blockCount: Int = 0,
  val tableCount: Int = 0,
  val ocr: JsonObject = JsonObject(emptyMap()),
  val warnings: List<String> = emptyList(),
  val visualReviewRequired: Boolean = true,
  val files: List<String> = emptyList(),
  val visuals: JsonArray = JsonArray(emptyList()),
)

object PreparedManifestStore {
  private val SOURCE_REVISION = Regex("^[a-f0-9]{64}$")

  fun validate(manifest: PreparedManifest) {
    require(manifest.version == PreparedCache.VERSION) { "bad manifest version" }
    require(manifest.documentId.isNotBlank()) { "bad documentId" }
    require(manifest.page >= 1) { "bad page" }
    require(SOURCE_REVISION.matches(manifest.sourceRevision)) { "bad sourceRevision" }
    require(manifest.kind == "pdf" || manifest.kind == "markdown") { "bad kind" }
    PreparedCache.validateRegion(manifest.region)
    require(manifest.cacheKey.isNotBlank()) { "bad cacheKey" }
    require(manifest.width > 0 && manifest.height > 0) { "bad page size" }
    require(manifest.scale.isFinite() && manifest.scale > 0) { "bad scale" }
    require(listOf(manifest.nativeCharacters, manifest.ocrCharacters, manifest.textCharacters,
      manifest.blockCount, manifest.tableCount).all { it >= 0 }) { "bad counts" }
    require(manifest.visualReviewRequired) { "visual review must stay required" }
    manifest.files.forEach(::validateProjectRelativeFile)
  }

  fun isHit(cached: PreparedManifest, expected: PreparedManifest): Boolean {
    return cached.version == expected.version &&
      cached.version == PreparedCache.VERSION &&
      cached.documentId == expected.documentId && cached.page == expected.page &&
      cached.sourceRevision == expected.sourceRevision && cached.region == expected.region &&
      cached.cacheKey == expected.cacheKey
  }

  fun isHit(cached: PreparedManifest, sourceRevision: String, cacheKey: String): Boolean {
    return cached.version == PreparedCache.VERSION &&
      cached.sourceRevision == sourceRevision &&
      cached.cacheKey == cacheKey
  }

  private fun validateProjectRelativeFile(file: String) {
    require(file.isNotBlank() && !file.startsWith('/') && !file.startsWith('\\')) {
      "bad project-relative file"
    }
    require(!Regex("^[a-zA-Z]:").containsMatchIn(file)) { "bad project-relative file" }
    require(file.split('/', '\\').all { it.isNotEmpty() && it != "." && it != ".." }) {
      "bad project-relative file"
    }
    require('\u0000' !in file) { "bad project-relative file" }
  }
}
