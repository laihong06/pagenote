package io.pagenote.agent

/**
 * 交接缓存（任务 10 / design 4.8 agent 职责）。
 *
 * 对齐桌面 `ai-handoffs.json` 语义（design 3.3）：
 * - 按页存 summary / noteRevision / sourceRevision / updated；
 * - revision 不一致即 stale。
 *
 * agent 侧只做内存视图与 stale 判定，落盘读写归 feature-annotations 的
 * HandoffStore（不重复实现、不碰他模块）。纯内存，可单测。
 */
object HandoffCache {
  data class Entry(
    val page: Int,
    val summary: String,
    val noteRevision: String,
    val sourceRevision: String,
    val updated: String,
  )

  data class View(
    val entry: Entry?,
    val stale: Boolean,
  )

  /** 缺交接不算 stale；任一 revision 不一致即 stale。 */
  fun view(
    entries: Map<Int, Entry>,
    page: Int,
    currentNoteRevision: String,
    currentSourceRevision: String,
  ): View {
    require(page >= 1) { "bad page" }
    val entry = entries[page] ?: return View(null, false)
    val stale = entry.noteRevision != currentNoteRevision ||
      entry.sourceRevision != currentSourceRevision
    return View(entry, stale)
  }

  /** 写交接：summary 非空，revision 随当前值快照。 */
  fun write(
    entries: Map<Int, Entry>,
    page: Int,
    summary: String,
    noteRevision: String,
    sourceRevision: String,
    updated: String,
  ): Map<Int, Entry> {
    require(page >= 1) { "bad page" }
    require(summary.isNotBlank()) { "empty summary" }
    require(noteRevision.isNotBlank()) { "empty noteRevision" }
    require(sourceRevision.isNotBlank()) { "empty sourceRevision" }
    return entries + (page to Entry(page, summary, noteRevision, sourceRevision, updated))
  }
}
