package io.pagenote.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialPagesTest {
  @Test
  fun allDoneAggregatesScope() = runBlocking {
    val report = SerialPages.run(listOf(2, 3)) { page ->
      AgentEvent.Done(pages = listOf(page), updated = listOf(page))
    }
    assertEquals(listOf(2, 3), report.completed)
    assertEquals(listOf(2, 3), report.updated)
    assertTrue(report.incomplete.isEmpty())
    assertFalse(report.stoppedByRateLimit)
  }

  @Test
  fun singleFailureContinuesRest() = runBlocking {
    val report = SerialPages.run(listOf(2, 3, 4)) { page ->
      if (page == 3) AgentEvent.Failed("tool failed") else AgentEvent.Done(listOf(page), emptyList())
    }
    assertEquals(listOf(2, 4), report.completed)
    assertEquals(listOf(3), report.incomplete)
    assertFalse(report.stoppedByRateLimit)
  }

  @Test
  fun rateLimitStopsAndMarksRemaining() = runBlocking {
    val report = SerialPages.run(listOf(2, 3, 4)) { page ->
      if (page == 3) AgentEvent.Failed("rate limited at step 2")
      else AgentEvent.Done(listOf(page), emptyList())
    }
    assertEquals(listOf(2), report.completed)
    assertEquals(listOf(3, 4), report.incomplete)
    assertTrue(report.stoppedByRateLimit)
  }

  @Test
  fun ignoresCompletionAndUpdatesOutsideCurrentRequestedPage() = runBlocking {
    val report = SerialPages.run(listOf(2, 3)) { page ->
      if (page == 2) {
        AgentEvent.Done(pages = listOf(3, 99), updated = listOf(3, 99))
      } else {
        AgentEvent.Done(pages = listOf(3), updated = listOf(3))
      }
    }

    assertEquals(listOf(3), report.completed)
    assertEquals(listOf(3), report.updated)
    assertEquals(listOf(2), report.incomplete)
  }

  @Test
  fun thrownPageFailureDoesNotPreventLaterPages() = runBlocking {
    val attempted = mutableListOf<Int>()
    val report = SerialPages.run(listOf(2, 3, 4)) { page ->
      attempted.add(page)
      if (page == 3) throw IllegalStateException("page save failed")
      AgentEvent.Done(pages = listOf(page), updated = emptyList())
    }

    assertEquals(listOf(2, 3, 4), attempted)
    assertEquals(listOf(2, 4), report.completed)
    assertEquals(listOf(3), report.incomplete)
  }

  @Test(expected = CancellationException::class)
  fun cancellationPropagatesToCaller(): Unit = runBlocking {
    SerialPages.run(listOf(2, 3)) {
      throw CancellationException("cancelled")
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun duplicatePagesRejected(): Unit = runBlocking {
    SerialPages.run(listOf(2, 2)) { page -> AgentEvent.Done(listOf(page), emptyList()) }
  }
}
