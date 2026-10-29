package io.pagenote.core.search

/** 索引请求：upsert / 按页删除 / 整项目删除。 */
sealed interface SearchIndexRequest {
  data class Upsert(val doc: SearchDoc) : SearchIndexRequest
  data class RemovePage(
    val projectId: String,
    val page: Int,
    val kinds: Set<SearchDocumentKind>,
  ) : SearchIndexRequest
  data class RemoveProject(val projectId: String) : SearchIndexRequest
}

/**
 * 增量索引的提交队列（线程安全、按键合并）：
 * - 同 (projectId, page, kind) 的 Upsert 合并保留最后一条（撤销/连续编辑不堆积）。
 * - RemovePage 丢弃该页该类别的待写 Upsert，再入队删除（删除语义优先于旧写）。
 * - RemoveProject 清空该项目的所有待写请求，再入队删除。
 * drain 返回当前快照并清空，顺序保持提交序。
 */
class SearchIndexQueue {
  private val lock = Any()
  private val pending = ArrayDeque<SearchIndexRequest>()

  val size: Int
    get() = synchronized(lock) { pending.size }

  fun offer(request: SearchIndexRequest) {
    synchronized(lock) {
      when (request) {
        is SearchIndexRequest.Upsert -> {
          removePendingUpsert { it.doc.key == request.doc.key }
          pending.addLast(request)
        }
        is SearchIndexRequest.RemovePage -> {
          removePendingUpsert { upsert ->
            upsert.doc.projectId == request.projectId &&
              upsert.doc.page == request.page &&
              upsert.doc.kind in request.kinds
          }
          pending.addLast(request)
        }
        is SearchIndexRequest.RemoveProject -> {
          pending.removeAll { candidate ->
            when (candidate) {
              is SearchIndexRequest.Upsert -> candidate.doc.projectId == request.projectId
              is SearchIndexRequest.RemovePage -> candidate.projectId == request.projectId
              is SearchIndexRequest.RemoveProject -> true
            }
          }
          pending.addLast(request)
        }
      }
    }
  }

  fun drain(): List<SearchIndexRequest> = synchronized(lock) {
    val snapshot = pending.toList()
    pending.clear()
    snapshot
  }

  private fun removePendingUpsert(predicate: (SearchIndexRequest.Upsert) -> Boolean) {
    pending.removeAll { request -> request is SearchIndexRequest.Upsert && predicate(request) }
  }
}
