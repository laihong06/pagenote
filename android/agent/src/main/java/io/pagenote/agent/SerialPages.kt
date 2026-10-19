package io.pagenote.agent

import kotlinx.coroutines.CancellationException

/**
 * 单页串行编排（任务 10 / design 错误处理：模型限流）。
 *
 * 约定：
 * - 限流时降级为单页串行：逐页调用单页执行器，不并发、不批量全章；
 * - 报告完成页与未完成页：聚合每页 Done/Failed，不伪称完成；
 * - 限流失败（reason 含 rate limited）即停，后续页记未完成；
 *   其他单页失败记未完成但继续下一页。
 *
 * 纯内存编排，单页执行由调用方注入，可单测。
 */
object SerialPages {
  data class Report(
    val completed: List<Int>,
    val updated: List<Int>,
    val incomplete: List<Int>,
    val stoppedByRateLimit: Boolean,
  )

  suspend fun run(
    pages: List<Int>,
    execute: suspend (page: Int) -> AgentEvent,
  ): Report {
    require(pages.isNotEmpty()) { "empty pages" }
    require(pages.all { it >= 1 }) { "bad page" }
    require(pages.toSet().size == pages.size) { "duplicate pages" }
    val completed = LinkedHashSet<Int>()
    val updated = LinkedHashSet<Int>()
    val incomplete = LinkedHashSet<Int>()
    var stoppedByRateLimit = false
    for ((index, page) in pages.withIndex()) {
      val event = try {
        execute(page)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        AgentEvent.Failed(error.message ?: "page execution failed")
      }
      when (event) {
        is AgentEvent.Done -> {
          if (page in event.pages) {
            completed.add(page)
            if (page in event.updated) updated.add(page)
          } else {
            incomplete.add(page)
          }
        }
        is AgentEvent.Failed -> {
          incomplete.add(page)
          if (isRateLimit(event)) {
            stoppedByRateLimit = true
            incomplete.addAll(pages.drop(index + 1))
            break
          }
        }
        is AgentEvent.ToolCall -> {
          incomplete.add(page)
        }
      }
    }
    return Report(
      completed = completed.toList(),
      updated = updated.toList(),
      incomplete = incomplete.toList(),
      stoppedByRateLimit = stoppedByRateLimit,
    )
  }

  private fun isRateLimit(failed: AgentEvent.Failed): Boolean {
    val reason = failed.reason.lowercase()
    return reason.contains("rate limited") || reason.contains("429")
  }
}
