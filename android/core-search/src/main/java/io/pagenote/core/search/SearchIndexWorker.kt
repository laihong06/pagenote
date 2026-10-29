package io.pagenote.core.search

/** 索引存储抽象：Room 仓储在 app 模块实现，单测用内存假件。 */
interface SearchIndexStore {
  /** 已索引文档的 revision；未索引返回 null。 */
  suspend fun revisionOf(key: SearchDocKey): String?

  suspend fun put(doc: SearchDoc)

  suspend fun removePage(projectId: String, page: Int, kinds: Set<SearchDocumentKind>)

  suspend fun removeProject(projectId: String)
}

/**
 * 增量索引工作器：批量消费队列快照。
 * 增量闸门：Upsert 的 revision 与已索引一致时跳过（幂等），避免整页重复写入。
 */
object SearchIndexWorker {
  suspend fun apply(requests: List<SearchIndexRequest>, store: SearchIndexStore) {
    requests.forEach { request ->
      when (request) {
        is SearchIndexRequest.Upsert -> {
          if (store.revisionOf(request.doc.key) != request.doc.revision) {
            store.put(request.doc)
          }
        }
        is SearchIndexRequest.RemovePage ->
          store.removePage(request.projectId, request.page, request.kinds)
        is SearchIndexRequest.RemoveProject -> store.removeProject(request.projectId)
      }
    }
  }
}
