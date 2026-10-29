package io.pagenote.android.search

import io.pagenote.core.search.SearchDoc
import io.pagenote.core.search.SearchDocumentKind
import io.pagenote.core.search.SearchIndexQueue
import io.pagenote.core.search.SearchIndexRequest
import io.pagenote.core.search.SearchIndexSettings
import io.pagenote.core.search.SearchIndexStore
import io.pagenote.core.search.SearchIndexWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 增量索引线程（移植批次 7）：专用单线程串行消费合并队列，页面变更钩子
 * 只做非阻塞入队，索引写入不与 UI/IO 竞争。范围设置快照在提交侧生效。
 */
class ProjectSearchIndexer(
  private val store: SearchIndexStore,
  initialSettings: SearchIndexSettings = SearchIndexSettings(),
) {
  private val queue = SearchIndexQueue()
  private val signal = Channel<Unit>(Channel.CONFLATED)

  @Volatile
  var settings: SearchIndexSettings = initialSettings

  private val executor: ExecutorService =
    Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "search-index-worker") }
  private val workerScope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

  @Volatile
  private var started = false

  /** 启动工作循环；重复调用为空操作。 */
  fun start() {
    if (started) return
    started = true
    workerScope.launch {
      while (isActive) {
        signal.receive()
        val batch = queue.drain()
        if (batch.isNotEmpty()) SearchIndexWorker.apply(batch, store)
      }
    }
  }

  /** 范围过滤后的非阻塞提交；类别不在索引范围内时丢弃。 */
  fun submit(doc: SearchDoc) {
    if (doc.kind !in settings.kinds()) return
    queue.offer(SearchIndexRequest.Upsert(doc))
    signal.trySend(Unit)
  }

  fun removePage(projectId: String, page: Int, kinds: Set<SearchDocumentKind> = SearchDocumentKind.ALL) {
    if (kinds.isEmpty()) return
    queue.offer(SearchIndexRequest.RemovePage(projectId, page, kinds))
    signal.trySend(Unit)
  }

  fun removeProject(projectId: String) {
    queue.offer(SearchIndexRequest.RemoveProject(projectId))
    signal.trySend(Unit)
  }

  /** 同步排空当前队列（不经工作线程，测试与重建流程使用）。 */
  suspend fun drainNow() {
    val batch = queue.drain()
    if (batch.isNotEmpty()) SearchIndexWorker.apply(batch, store)
  }

  /** 排队中的请求条数（诊断用）。 */
  fun pendingCount(): Int = queue.size

  fun close() {
    workerScope.cancel()
    executor.shutdown()
  }
}
