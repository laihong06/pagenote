package io.pagenote.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PagePrefetchCoordinatorTest {
  @Test
  fun windowIncludesCurrentPageAndClampsAtDocumentEnd() {
    assertEquals(listOf(0, 1, 2), PagePrefetchPolicy.window(0, 69, 3))
    assertEquals(listOf(1, 2, 3), PagePrefetchPolicy.window(1, 69, 3))
    assertEquals(listOf(67, 68), PagePrefetchPolicy.window(67, 69, 3))
    assertEquals(emptyList<Int>(), PagePrefetchPolicy.window(0, 69, 0))
  }

  @Test
  fun overlappingWindowsPrepareOnlyNewPagesSequentially() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val prefetcher = ReaderPagePrefetcher<String>()
    val identity = PagePreparationIdentity("project", "a".repeat(64))
    val prepared = mutableListOf<Int>()
    var active = 0
    var maximumActive = 0
    try {
      prefetcher.schedule(scope, identity, 0, 69, 3) { key ->
        active += 1
        maximumActive = maxOf(maximumActive, active)
        delay(1)
        prepared += key.pageIndex
        active -= 1
        "page-${key.pageIndex}"
      }.join()

      prefetcher.schedule(scope, identity, 1, 69, 3) { key ->
        prepared += key.pageIndex
        "page-${key.pageIndex}"
      }.join()

      assertEquals(listOf(0, 1, 2, 3), prepared)
      assertEquals(1, maximumActive)
      assertEquals("page-1", prefetcher.cached(identity, 1))
      assertEquals("page-3", prefetcher.cached(identity, 3))
    } finally {
      prefetcher.cancel()
      scope.cancel()
    }
  }

  @Test
  fun changingProjectOrRevisionCancelsOldWorkWithoutCachingItUnderNewIdentity() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val prefetcher = ReaderPagePrefetcher<String>()
    val oldIdentity = PagePreparationIdentity("project", "a".repeat(64))
    val newIdentity = PagePreparationIdentity("project", "b".repeat(64))
    val oldStarted = CompletableDeferred<Unit>()
    val neverReleased = CompletableDeferred<Unit>()
    try {
      val oldJob = prefetcher.schedule(scope, oldIdentity, 0, 5, 2) {
        oldStarted.complete(Unit)
        neverReleased.await()
        "stale"
      }
      oldStarted.await()

      prefetcher.schedule(scope, newIdentity, 0, 5, 1) { "fresh" }.join()
      oldJob.join()

      assertNull(prefetcher.cached(oldIdentity, 0))
      assertEquals("fresh", prefetcher.cached(newIdentity, 0))
    } finally {
      prefetcher.cancel()
      scope.cancel()
    }
  }

  @Test
  fun failedPageIsReportedAndDoesNotStopLaterPages() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val prefetcher = ReaderPagePrefetcher<String>()
    val identity = PagePreparationIdentity("project", "d".repeat(64))
    val failures = mutableListOf<Int>()
    val prepared = mutableListOf<Int>()
    try {
      prefetcher.schedule(
        scope = scope,
        identity = identity,
        currentPageIndex = 0,
        pageCount = 3,
        prepareAheadPages = 3,
        onFailure = { key, _ -> failures += key.pageIndex },
        prepare = { key ->
          if (key.pageIndex == 0) error("offline OCR failed")
          prepared += key.pageIndex
          "page-${key.pageIndex}"
        },
      ).join()

      assertEquals(listOf(0), failures)
      assertEquals(listOf(1, 2), prepared)
      assertNull(prefetcher.cached(identity, 0))
    } finally {
      prefetcher.cancel()
      scope.cancel()
    }
  }

  @Test
  fun zeroWindowDoesNotStartPreparation() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val prefetcher = ReaderPagePrefetcher<String>()
    var started = false
    try {
      prefetcher.schedule(scope, PagePreparationIdentity("project", "c".repeat(64)), 0, 5, 0) {
        started = true
        "unexpected"
      }.join()

      assertFalse(started)
    } finally {
      prefetcher.cancel()
      scope.cancel()
    }
  }
}
