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
      val page = if (previous == null) 1 else writer.append(opened, course, lesson, slide)
      RainLiveProjectWrite(destination, opened, page)
    }
  }
}
