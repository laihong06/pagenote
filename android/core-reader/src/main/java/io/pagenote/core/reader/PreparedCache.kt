package io.pagenote.core.reader

import java.security.MessageDigest

data class PageRegion(val x: Double, val y: Double, val width: Double, val height: Double)

object PreparedCache {
  const val VERSION = 2

  fun validateRegion(region: PageRegion) {
    require(listOf(region.x, region.y, region.width, region.height).all { it.isFinite() })
    require(region.x >= 0 && region.y >= 0 && region.width > 0 && region.height > 0)
    require(region.x + region.width <= 1000 && region.y + region.height <= 1000) {
      "region must stay in 1000x1000"
    }
  }

  fun cacheKey(sourceRevision: String, mode: String, region: PageRegion, engine: String): String {
    validateRegion(region)
    val payload = "{\"version\":$VERSION,\"source\":\"$sourceRevision\",\"mode\":\"$mode\"," +
      "\"region\":{\"x\":${region.x},\"y\":${region.y},\"width\":${region.width},\"height\":${region.height}}," +
      "\"engine\":\"$engine\"}"
    return sha256(payload)
  }

  fun folderName(page: Int, key: String): String {
    require(page >= 1)
    return "$page-${key.take(24)}"
  }

  private fun sha256(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
}
