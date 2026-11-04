package io.pagenote.android

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes writers and drains edits made while a previous snapshot was being written. */
internal class NoteSaveCoordinator {
  private val mutex = Mutex()

  suspend fun flush(hasPending: () -> Boolean, writePending: suspend () -> Boolean): Boolean =
    mutex.withLock {
      while (hasPending()) {
        if (!writePending()) return@withLock false
      }
      true
    }
}
