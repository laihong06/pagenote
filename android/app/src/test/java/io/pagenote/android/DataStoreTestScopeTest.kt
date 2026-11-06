package io.pagenote.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Test

class DataStoreTestScopeTest {
  @Test fun shutdownWaitsForTheStoreScopeToFinishClosing() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cleanupStarted = CompletableDeferred<Unit>()
    val allowCleanup = CompletableDeferred<Unit>()
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      } finally {
        withContext(NonCancellable) {
          cleanupStarted.complete(Unit)
          allowCleanup.await()
        }
      }
    }
    val shutdown = async { scope.cancelAndAwaitDataStore() }
    try {
      withTimeout(5_000) { cleanupStarted.await() }
      yield()
      assertFalse("scope shutdown must wait until asynchronous cleanup completes", shutdown.isCompleted)
    } finally {
      allowCleanup.complete(Unit)
      shutdown.join()
      scope.coroutineContext[Job]!!.join()
    }
  }
}
