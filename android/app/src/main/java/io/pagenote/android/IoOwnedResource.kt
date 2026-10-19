package io.pagenote.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

internal suspend fun <R : Any, T> withIoOwnedResource(
  render: () -> R,
  dispose: (R) -> Unit,
  use: suspend (R) -> T,
): T {
  val owned = AtomicReference<R?>()
  try {
    val resource = withContext(Dispatchers.IO) { render().also(owned::set) }
    return use(resource)
  } finally {
    owned.getAndSet(null)?.let(dispose)
  }
}
