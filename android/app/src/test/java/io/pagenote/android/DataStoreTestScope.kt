package io.pagenote.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

internal suspend fun CoroutineScope.cancelAndAwaitDataStore() {
  requireNotNull(coroutineContext[Job]).cancelAndJoin()
}
