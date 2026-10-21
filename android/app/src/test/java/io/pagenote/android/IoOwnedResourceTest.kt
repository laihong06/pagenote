package io.pagenote.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class IoOwnedResourceTest {
  @Test
  fun disposesResourceWhenRenderFinishesAfterCancellation() = runBlocking {
    val renderStarted = CountDownLatch(1)
    val finishRender = CountDownLatch(1)
    val disposeCalls = AtomicInteger()
    val useCalled = AtomicBoolean(false)
    val job = launch(Dispatchers.Default) {
      withIoOwnedResource(
        render = {
          renderStarted.countDown()
          finishRender.await()
          Any()
        },
        dispose = { disposeCalls.incrementAndGet() },
        use = {
          useCalled.set(true)
        },
      )
    }

    assertTrue(renderStarted.await(2, TimeUnit.SECONDS))
    job.cancel()
    finishRender.countDown()
    job.join()

    assertFalse(useCalled.get())
    assertEquals(1, disposeCalls.get())
  }
}
