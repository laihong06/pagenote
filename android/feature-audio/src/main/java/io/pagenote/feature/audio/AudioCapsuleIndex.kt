package io.pagenote.feature.audio

/**
 * 音频胶囊内存索引（移植批次 8）：会话内的不可变列表操作 + 查询，供
 * overlay/转写列表/搜索跳转共用。存储以 audio.json 全量读写为主，本类
 * 不做持久化。
 */
class AudioCapsuleIndex(initial: List<AudioCapsule> = emptyList()) {
  private val capsules: List<AudioCapsule> = initial

  val all: List<AudioCapsule>
    get() = capsules

  val size: Int
    get() = capsules.size

  fun byId(id: String): AudioCapsule? = capsules.firstOrNull { it.id == id }

  fun byAsset(asset: String): AudioCapsule? = capsules.firstOrNull { it.asset == asset }

  /** 某页的胶囊，按创建时间升序（画布绘制从旧到新，命中取最上层）。 */
  fun forPage(page: Int): List<AudioCapsule> =
    capsules.filter { it.page == page }.sortedBy(AudioCapsule::createdAtMillis)

  /** 转写列表全集：页号升序，页内按创建时间升序。 */
  fun orderedForList(): List<AudioCapsule> =
    capsules.sortedWith(compareBy({ it.page }, { it.createdAtMillis }))

  /** 已转写胶囊（回听跳转列表默认只看有产物的）。 */
  fun withTranscript(): List<AudioCapsule> = orderedForList().filter(AudioCapsule::hasTranscript)

  /** 插入/同 id 覆盖；返回新索引。 */
  fun upsert(capsule: AudioCapsule): AudioCapsuleIndex {
    val next = if (capsules.any { it.id == capsule.id }) {
      capsules.map { if (it.id == capsule.id) capsule else it }
    } else {
      capsules + capsule
    }
    return AudioCapsuleIndex(next)
  }

  fun remove(id: String): AudioCapsuleIndex =
    AudioCapsuleIndex(capsules.filterNot { it.id == id })
}
