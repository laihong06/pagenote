package io.pagenote.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSaveCoordinatorTest {
  @Test fun flushWritesEditsMadeDuringAnEarlierWrite() = runBlocking {
    val coordinator = NoteSaveCoordinator()
    var draft = "first"
    var saved = ""
    val writes = mutableListOf<String>()
    val result = coordinator.flush({ draft != saved }) {
      val snapshot = draft
      writes += snapshot
      if (writes.size == 1) draft = "second"
      saved = snapshot
      true
    }
    assertTrue(result)
    assertEquals(listOf("first", "second"), writes)
    assertEquals("second", saved)
  }

  @Test fun failedWriteStopsFlushAndLeavesDraftPending() = runBlocking {
    val coordinator = NoteSaveCoordinator()
    var attempts = 0
    val result = coordinator.flush({ true }) { attempts++; false }
    assertFalse(result)
    assertEquals(1, attempts)
  }

  @Test fun simultaneousFlushesDoNotWriteTheSameDraftTwice() = runBlocking {
    val coordinator = NoteSaveCoordinator()
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var pending = true
    var writes = 0
    val first = async {
      coordinator.flush({ pending }) {
        writes++
        started.complete(Unit)
        release.await()
        pending = false
        true
      }
    }
    started.await()
    val second = async { coordinator.flush({ pending }) { writes++; pending = false; true } }
    release.complete(Unit)
    assertTrue(first.await())
    assertTrue(second.await())
    assertEquals(1, writes)
  }
}
