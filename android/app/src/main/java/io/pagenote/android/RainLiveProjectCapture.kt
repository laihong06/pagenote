package io.pagenote.android

import android.content.Context
import android.net.Uri
import io.pagenote.feature.yuketang.RainCourse
import io.pagenote.feature.yuketang.RainLesson
import io.pagenote.feature.yuketang.RainSlideImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class RainLiveProjectWrite(
  val target: ProjectOpenTarget,
  val project: OpenedProject,
  val pageNumber: Int?,
  val focusPage: Int = pageNumber ?: 1,
  val pageMap: Map<Int, Int> = emptyMap(),
)

internal class RainLiveProjectCapture(
  private val context: Context,
  private val parentTreeUri: Uri,
  private val name: String,
  private val course: RainCourse,
  private val lesson: RainLesson,
) {
  private val mutex = Mutex()
  private var target: ProjectOpenTarget? = null

  suspend fun prepare() = withContext(Dispatchers.IO) {
    RainProjectWriter(context).validateDestination(parentTreeUri, name)
  }

  suspend fun accept(slide: RainSlideImage): RainLiveProjectWrite = withContext(Dispatchers.IO) {
    mutex.withLock {
      val previous = target
      val writer = RainProjectWriter(context)
      val destination = previous ?: writer.create(parentTreeUri, name, course, lesson) { emit ->
        emit(slide)
      }.also { target = it }
      val opened = ProjectDocumentOpener(context.contentResolver).open(destination.accessTreeUri, destination.rootDocumentId)
      val write = if (previous == null) RainPageWrite(1, true) else writer.insert(opened, course, lesson, slide)
      RainLiveProjectWrite(destination, opened, if (write.added) write.pageNumber else null, write.pageNumber, write.pageMap)
    }
  }
}
