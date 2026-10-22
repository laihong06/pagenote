package io.pagenote.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.pagenote.feature.yuketang.RainClassroomClient
import io.pagenote.feature.yuketang.RainCourse
import io.pagenote.feature.yuketang.RainCurrentLesson
import io.pagenote.feature.yuketang.RainLesson
import io.pagenote.feature.yuketang.matchCurrentLessonCourse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RainClassroomDialog(
  client: RainClassroomClient,
  canCaptureLive: Boolean,
  busy: Boolean,
  waitingForFirstPage: String?,
  onImport: (RainCourse, RainLesson) -> Unit,
  onStartLive: (RainCourse, RainLesson) -> Unit,
  onCreateLiveProject: (RainCourse, RainLesson, Boolean) -> Unit,
  onRetryLive: () -> Unit,
  onStopLive: () -> Unit,
  onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val login by produceState(client.login.value, client) {
    client.login.collect { value = it }
  }
  val live by produceState(client.live.value, client) {
    client.live.collect { value = it }
  }
  var courses by remember { mutableStateOf(emptyList<RainCourse>()) }
  var lessons by remember { mutableStateOf(emptyList<RainLesson>()) }
  var currentLessons by remember { mutableStateOf(emptyList<RainCurrentLesson>()) }
  var course by remember { mutableStateOf<RainCourse?>(null) }
  var lesson by remember { mutableStateOf<RainLesson?>(null) }
  var expandedCourses by remember { mutableStateOf(false) }
  var expandedLessons by remember { mutableStateOf(false) }
  var currentLesson by remember { mutableStateOf<RainCurrentLesson?>(null) }
  var expandedCurrentLessons by remember { mutableStateOf(false) }
  var allowAttendance by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  suspend fun loadCourses() {
    loading = true
    try {
      courses = withContext(Dispatchers.IO) { client.listCourses() }
      try {
        currentLessons = withContext(Dispatchers.IO) { client.listCurrentLessons() }
        currentLesson = currentLessons.singleOrNull()
        error = null
      } catch (failure: Exception) {
        currentLessons = emptyList()
        error = failure.message
      }
    } catch (failure: Exception) {
      error = failure.message
    } finally {
      loading = false
    }
  }

  LaunchedEffect(Unit) {
    runCatching { withContext(Dispatchers.IO) { client.refreshLogin() } }
      .onFailure { error = it.message }
  }

  LaunchedEffect(login.status) {
    if (login.status == "authenticated") loadCourses()
  }

  fun matchingCourse(item: RainCurrentLesson): RainCourse? = matchCurrentLessonCourse(item, courses)

  val liveCourse = currentLesson?.let { item ->
    if (item.classroomId != null || item.courseId != null) matchingCourse(item) else course
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.rain_classroom_title)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(when (login.status) {
          "authenticated" -> stringResource(R.string.rain_account_logged_in, login.accountName.orEmpty())
          "waiting_scan" -> stringResource(R.string.rain_login_waiting)
          "connecting", "loading_qr" -> stringResource(R.string.rain_login_connecting)
          "expired" -> stringResource(R.string.rain_login_expired)
          "failed" -> login.error ?: stringResource(R.string.rain_login_failed)
          else -> stringResource(R.string.rain_login_signed_out)
        }, style = MaterialTheme.typography.bodyMedium)
        val qrImage = login.qrImage
        if (login.status == "waiting_scan" && qrImage != null) {
          val bitmap = remember(qrImage) { BitmapFactory.decodeByteArray(qrImage, 0, qrImage.size) }
          if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "雨课堂登录二维码", modifier = Modifier.size(208.dp).align(Alignment.CenterHorizontally))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = {
            error = null
            client.startLogin()
          }) { Text(if (login.status == "waiting_scan" || login.status == "connecting") stringResource(R.string.rain_refresh_qr) else stringResource(R.string.rain_login_action)) }
          if (login.status == "authenticated") {
            TextButton(onClick = {
              scope.launch {
                runCatching { withContext(Dispatchers.IO) { client.logout() } }
                  .onSuccess {
                    courses = emptyList()
                    lessons = emptyList()
                    currentLessons = emptyList()
                    currentLesson = null
                    error = null
                  }
                  .onFailure { error = it.message }
              }
            }) {
              Text(stringResource(R.string.rain_logout))
            }
            TextButton(enabled = !loading, onClick = { scope.launch { loadCourses() } }) { Text(stringResource(R.string.rain_refresh_courses)) }
          }
        }
        Text(stringResource(R.string.rain_history_section), style = MaterialTheme.typography.titleSmall)
        Box {
          OutlinedButton(enabled = courses.isNotEmpty(), onClick = { expandedCourses = true }, modifier = Modifier.fillMaxWidth()) {
            Text(course?.name ?: if (login.status == "authenticated") stringResource(R.string.rain_choose_course) else stringResource(R.string.rain_choose_course_first))
          }
          DropdownMenu(expanded = expandedCourses, onDismissRequest = { expandedCourses = false }) {
            courses.forEach { item ->
              DropdownMenuItem(text = { Text(item.name) }, onClick = {
                course = item
                lesson = null
                lessons = emptyList()
                expandedCourses = false
                loading = true
                scope.launch {
                  try { lessons = withContext(Dispatchers.IO) { client.listLessons(item.classroomId) } }
                  catch (failure: Exception) { error = failure.message }
                  finally { loading = false }
                }
              })
            }
          }
        }
        Box {
          OutlinedButton(enabled = lessons.isNotEmpty(), onClick = { expandedLessons = true }, modifier = Modifier.fillMaxWidth()) {
            Text(lesson?.name ?: if (course == null) stringResource(R.string.rain_choose_lesson_first) else stringResource(R.string.rain_choose_lesson))
          }
          DropdownMenu(expanded = expandedLessons, onDismissRequest = { expandedLessons = false }) {
            lessons.forEach { item ->
              DropdownMenuItem(text = { Text(item.name, maxLines = 2) }, onClick = {
                lesson = item
                expandedLessons = false
              })
            }
          }
        }
        Text(stringResource(R.string.rain_import_info))
        Button(enabled = lesson != null && !loading && !busy && live.status in setOf("stopped", "finished") && live.pendingPages == 0, onClick = {
          val selectedCourse = course ?: return@Button
          val selectedLesson = lesson ?: return@Button
          onImport(selectedCourse, selectedLesson)
        }) { Text(stringResource(R.string.rain_import_button)) }
        Text(stringResource(R.string.rain_live_section), style = MaterialTheme.typography.titleSmall)
        Box {
          OutlinedButton(
            enabled = currentLessons.isNotEmpty(),
            onClick = { expandedCurrentLessons = true },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(currentLesson?.name ?: stringResource(
              if (error != null && currentLessons.isEmpty()) R.string.rain_current_lessons_unavailable
              else if (currentLessons.isEmpty()) R.string.rain_current_lessons_empty
              else R.string.rain_choose_current_lesson,
            ))
          }
          DropdownMenu(expanded = expandedCurrentLessons, onDismissRequest = { expandedCurrentLessons = false }) {
            currentLessons.forEach { item ->
              val matching = matchingCourse(item)
              DropdownMenuItem(
                text = { Text(matching?.let { "${it.name} · ${item.name}" } ?: item.name, maxLines = 2) },
                onClick = {
                  currentLesson = item
                  expandedCurrentLessons = false
                },
              )
            }
          }
        }
        if (currentLesson != null &&
          (currentLesson?.classroomId != null || currentLesson?.courseId != null) && liveCourse == null
        ) {
          Text(stringResource(R.string.rain_current_lesson_unmatched), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.rain_live_warning), color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = allowAttendance, onCheckedChange = { allowAttendance = it })
          Text(stringResource(R.string.rain_live_consent))
        }
        val liveLabel = stringResource(when (live.status) {
          "joining" -> R.string.rain_live_joining
          "connecting" -> R.string.rain_live_connecting
          "active" -> R.string.rain_live_active
          "reconnecting" -> R.string.rain_live_reconnecting
          "finished" -> R.string.rain_live_finished
          else -> R.string.rain_live_stopped
        })
        Text(stringResource(R.string.rain_live_state, liveLabel) + live.error?.let { " · $it" }.orEmpty())
        waitingForFirstPage?.let { Text(stringResource(R.string.rain_live_waiting_page, it)) }
        if (live.backfillPendingPages > 0) Text(stringResource(R.string.rain_live_backfilling, live.backfillPendingPages))
        if (live.possibleGap) {
          Text(stringResource(R.string.rain_live_possible_gap), color = MaterialTheme.colorScheme.error)
        }
        fun selectedLiveLesson(): RainLesson? = currentLesson?.let { selected ->
          liveCourse?.let { matched -> RainLesson(selected.classroomId ?: matched.classroomId, selected.lessonId, "", selected.name) }
        }
        RainLiveActions(
          live = live, hasSelection = liveCourse != null && currentLesson != null,
          canAppend = canCaptureLive, consent = allowAttendance, busy = busy || loading,
          onCreateProject = {
            val selected = selectedLiveLesson()
            if (selected != null && liveCourse != null) onCreateLiveProject(liveCourse, selected, allowAttendance)
          },
          onAppend = {
            val selected = selectedLiveLesson()
            if (selected != null && liveCourse != null) onStartLive(liveCourse, selected)
          },
          onRetry = onRetryLive, onStop = onStopLive,
        )
        if (loading) Text(stringResource(R.string.rain_loading))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rain_close)) } },
  )
}
