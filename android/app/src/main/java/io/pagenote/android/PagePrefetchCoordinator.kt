package io.pagenote.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PagePreparationIdentity(val projectId: String, val sourceRevision: String) {
  init {
    require(projectId.isNotBlank())
    require(sourceRevision.isNotBlank())
  }
}

data class PreparedPageKey(val identity: PagePreparationIdentity, val pageIndex: Int)

object PagePrefetchPolicy {
  const val MAX_AHEAD_PAGES = 10

  fun window(currentPageIndex: Int, pageCount: Int, prepareAheadPages: Int): List<Int> {
    require(prepareAheadPages in 0..MAX_AHEAD_PAGES) { "bad preparation window" }
    if (prepareAheadPages == 0 || pageCount == 0) return emptyList()
    require(currentPageIndex in 0 until pageCount) { "current page is out of range" }
    return (currentPageIndex until minOf(pageCount, currentPageIndex + prepareAheadPages)).toList()
  }
}

class ReaderPagePrefetcher<T>(private val maxCachedPages: Int = 20) {
  private val cache = LinkedHashMap<PreparedPageKey, T>(16, 0.75f, true)
  @Volatile private var activeIdentity: PagePreparationIdentity? = null
  private var activeJob: Job? = null

  init {
    require(maxCachedPages > 0)
  }

  fun cached(identity: PagePreparationIdentity, pageIndex: Int): T? = synchronized(cache) {
    cache[PreparedPageKey(identity, pageIndex)]
  }

  fun remember(identity: PagePreparationIdentity, pageIndex: Int, value: T) {
    synchronized(cache) {
      cache[PreparedPageKey(identity, pageIndex)] = value
      trimCache()
    }
  }

  fun schedule(
    scope: CoroutineScope,
    identity: PagePreparationIdentity,
    currentPageIndex: Int,
    pageCount: Int,
    prepareAheadPages: Int,
    onFailure: (PreparedPageKey, Exception) -> Unit = { _, _ -> },
    prepare: suspend (PreparedPageKey) -> T,
  ): Job {
    activeJob?.cancel()
    activeIdentity = identity
    val keys = PagePrefetchPolicy.window(currentPageIndex, pageCount, prepareAheadPages)
      .map { PreparedPageKey(identity, it) }
    val missing = synchronized(cache) { keys.filterNot(cache::containsKey) }
    val job = scope.launch {
      for (key in missing) {
        try {
          val value = prepare(key)
          currentCoroutineContext().ensureActive()
          synchronized(cache) {
            if (activeIdentity == identity) {
              cache[key] = value
              trimCache()
            }
          }
        } catch (cancelled: CancellationException) {
          if (!currentCoroutineContext().isActive) throw cancelled
          currentCoroutineContext().ensureActive()
          onFailure(key, cancelled)
        } catch (error: Exception) {
          currentCoroutineContext().ensureActive()
          if (activeIdentity == identity) onFailure(key, error)
        }
      }
    }
    activeJob = job
    return job
  }

  fun cancel() {
    activeJob?.cancel()
    activeJob = null
    activeIdentity = null
  }

  private fun trimCache() {
    while (cache.size > maxCachedPages) {
      val oldest = cache.entries.iterator()
      oldest.next()
      oldest.remove()
    }
  }
}
