package io.pagenote.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class CancellationSafeContextResultTest {
  @Test
  fun disposesAResultWhenCancellationWinsTheContextHandoff() = runBlocking {
    val resultProduced = CompletableDeferred<Unit>()
    val finishRender = CountDownLatch(1)
    val disposed = AtomicBoolean(false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val job = scope.launch {
      withCancellationSafeContextResult(
        context = Dispatchers.IO,
        dispose = { disposed.set(true) },
      ) {
        resultProduced.complete(Unit)
        finishRender.await()
        Any()
      }
    }

    resultProduced.await()
    job.cancel()
    finishRender.countDown()
    job.join()
    scope.cancel()

    assertTrue("the result produced during cancellation must be disposed", disposed.get())
  }
}
