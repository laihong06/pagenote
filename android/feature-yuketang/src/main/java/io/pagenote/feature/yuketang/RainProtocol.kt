package io.pagenote.feature.yuketang

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL

data class RainCurrentLesson(
  val lessonId: String,
  val classroomId: String?,
  val courseId: String?,
  val name: String,
)

data class RainLiveSlideEvent(val presentationId: String, val sourceIndex: Int?, val pageId: String? = null) {
  val key: String get() = if (sourceIndex != null) "$presentationId:$sourceIndex" else "$presentationId:page:$pageId"
}

data class RainLiveMessage(
  val finished: Boolean = false,
  val timeline: Boolean = false,
  val slides: List<RainLiveSlideEvent> = emptyList(),
)

data class RainTimelineReplay(
  val slides: List<RainLiveSlideEvent>,
  val possibleGap: Boolean,
)

data class RainSourcePageIdentity(
  val classroomId: String,
  val presentationId: String,
  val pageId: String,
  val sha256: String,
)

internal fun bearerAuthorization(value: String?): String? {
  val value = value?.trim()?.takeIf(String::isNotBlank) ?: return null
  val token = if (value.startsWith("Bearer", ignoreCase = true)) {
    value.substring("Bearer".length).trim()
  } else {
    value
  }
  return token.takeIf(String::isNotBlank)?.let { "Bearer $it" }
}

internal fun rainApiHeaders(
  cookie: String,
  authorization: String?,
  referer: String,
  userAgent: String,
): Map<String, String> = buildMap {
  put("Accept", "application/json")
  put("Referer", referer)
  put("Origin", "https://www.yuketang.cn")
  put("User-Agent", userAgent)
  put("xtbz", "ykt")
  put("X-Client", "h5")
  if (cookie.isNotBlank()) put("Cookie", cookie)
  bearerAuthorization(authorization)?.let { put("Authorization", it) }
}

internal fun rainHttpError(
  url: String,
  status: Int,
  operation: String = "雨课堂请求失败",
): IOException {
  val path = runCatching { URL(url).path }.getOrDefault("/未知接口")
  return IOException("$operation（HTTP $status，$path）")
}

internal fun normalizeCurrentLessons(payload: JSONObject): List<RainCurrentLesson> {
  val code = payload.optInt("code", 0)
  if (code != 0) throw IOException("雨课堂请求失败：${payload.optString("msg", "code $code")}")
  val dataValue = payload.opt("data")
  val data = dataValue as? JSONObject ?: payload
  val list = data.optJSONArray("onLessonClassrooms")
    ?: data.optJSONArray("on_lesson_classrooms")
    ?: data.optJSONArray("result")
    ?: dataValue as? JSONArray
    ?: throw IOException("无法识别雨课堂当前课堂列表")
  return buildList {
    for (index in 0 until list.length()) {
      val item = list.optJSONObject(index) ?: continue
      val status = item.opt("status")
      if (status != null && status != JSONObject.NULL && status != true && status.toString() != "1") continue
      val lessonId = item.stringId("lessonId", "lesson_id", "id") ?: continue
      val course = item.optJSONObject("course") ?: JSONObject()
      val classroom = item.optJSONObject("classroom") ?: JSONObject()
      val name = item.firstText("name", "title", "lessonName", "lesson_name")
        ?: course.firstText("name")
        ?: item.firstText("classroomName", "classroom_name", "courseName", "course_name")
        ?: "正在进行的课堂"
      add(
        RainCurrentLesson(
          lessonId = lessonId,
          classroomId = item.stringId("classroomId", "classroom_id") ?: classroom.stringId("id"),
          courseId = item.stringId("courseId", "course_id") ?: course.stringId("courseId", "course_id", "id"),
          name = name,
        ),
      )
    }
  }
}

fun matchCurrentLessonCourse(
  lesson: RainCurrentLesson,
  courses: List<RainCourse>,
): RainCourse? {
  if (lesson.classroomId == null && lesson.courseId == null) return null
  if (lesson.classroomId != null) {
    return courses.singleOrNull { it.classroomId == lesson.classroomId }
  }
  return courses.singleOrNull { it.courseId == lesson.courseId }
}

internal fun normalizeLiveMessage(message: JSONObject): RainLiveMessage {
  val op = message.optString("op")
  if (op == "lessonfinished") return RainLiveMessage(finished = true)
  if (op == "hello" || op == "fetchtimeline") {
    val slides = buildList {
      val timeline = message.optJSONArray("timeline") ?: JSONArray()
      for (index in 0 until timeline.length()) {
        val item = timeline.optJSONObject(index) ?: continue
        if (item.has("type") && item.optString("type") != "slide") continue
        val presentationId = item.stringId("pres") ?: continue
        val sourceIndex = item.intValue("si")
        val pageId = if (sourceIndex == null) item.stringId("sid") else null
        if (sourceIndex == null && pageId == null) continue
        add(RainLiveSlideEvent(presentationId, sourceIndex, pageId))
      }
      val currentPresentation = message.stringId("presentation")
      val currentIndex = message.intValue("slideindex")
      if (currentPresentation != null && currentIndex != null) {
        val current = RainLiveSlideEvent(currentPresentation, currentIndex)
        if (lastOrNull() != current) add(current)
      }
    }
    return RainLiveMessage(timeline = true, slides = slides)
  }

  if (op !in setOf("showpresentation", "presentationupdated", "presentationcreated", "showfinished", "slidenav"))
    return RainLiveMessage()
  val slide = message.optJSONObject("slide")
  val presentationId = message.stringId("presentation") ?: slide?.stringId("pres")
  val sourceIndex = message.intValue("slideindex") ?: slide?.intValue("si")
  val pageId = if (sourceIndex == null) slide?.stringId("sid") else null
  return if (presentationId != null && (sourceIndex != null || pageId != null)) {
    RainLiveMessage(slides = listOf(RainLiveSlideEvent(presentationId, sourceIndex, pageId)))
  } else {
    RainLiveMessage()
  }
}

internal fun selectTimelineReplay(
  slides: List<RainLiveSlideEvent>,
  cursor: String?,
  initialConnection: Boolean,
  pageCursor: String? = null,
): RainTimelineReplay {
  if (slides.isEmpty()) return RainTimelineReplay(emptyList(), possibleGap = false)
  if (initialConnection) return RainTimelineReplay(slides.distinctBy { it.key }, possibleGap = false)
  if (cursor != null || pageCursor != null) {
    val cursorIndex = slides.indexOfLast { it.key == cursor || it.key == pageCursor }
    if (cursorIndex >= 0) return RainTimelineReplay(slides.drop(cursorIndex + 1), possibleGap = false)
  }
  return RainTimelineReplay(listOf(slides.last()), possibleGap = true)
}

internal fun resolveRainLiveSlide(event: RainLiveSlideEvent, slides: List<RainSlide>): RainSlide =
  slides.firstOrNull {
    it.presentationId == event.presentationId &&
      if (event.sourceIndex != null) it.sourceIndex == event.sourceIndex else it.pageId == event.pageId
  } ?: throw IOException("课件中找不到时间线指定的页面")

private fun JSONObject.intValue(key: String): Int? {
  if (!has(key) || isNull(key)) return null
  val value = opt(key)
  return when (value) {
    is Number -> value.toInt().takeIf { value.toDouble() == it.toDouble() }
    is String -> value.toIntOrNull()
    else -> null
  }?.takeIf { it >= 0 }
}

private fun JSONObject.stringId(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
  if (!has(key) || isNull(key)) null else opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.firstText(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
  if (!has(key) || isNull(key)) null else opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
}
