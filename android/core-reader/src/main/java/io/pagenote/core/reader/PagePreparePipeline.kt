package io.pagenote.core.reader

object PagePreparePipeline {
  data class Prepared(val text: String, val fromCache: Boolean)

  fun prepare(
    page: Int,
    region: PageRegion,
    mode: String,
    refresh: Boolean,
    sourceRevision: String,
    cachedRevision: String?,
    engine: String,
    work: () -> Prepared,
  ): Prepared {
    require(page >= 1) { "bad page" }
    require(mode.isNotBlank()) { "bad mode" }
    require(sourceRevision.isNotBlank()) { "bad source revision" }
    require(engine.isNotBlank()) { "bad engine" }
    PreparedCache.validateRegion(region)
    // 缓存 key 本身即身份：sourceRevision 漂移则 key 失配，不得复用旧缓存。
    PreparedCache.cacheKey(sourceRevision, mode, region, engine)
    if (!refresh && cachedRevision != null && cachedRevision == sourceRevision) {
      return Prepared("cached", true)
    }
    val fresh = work()
    require(!fresh.fromCache) { "work must not claim cache" }
    return fresh
  }
}
