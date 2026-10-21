package io.pagenote.feature.yuketang

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class RainCurrentLesson(
  val lessonId: String,
  val classroomId: String?,
  val courseId: String?,
  val name: String,
)

data class RainLiveSlideEvent(val presentationId: String, val sourceIndex: Int)

data class RainLiveMessage(
  val finished: Boolean = false,
  val timeline: Boolean = false,
  val slides: List<RainLiveSlideEvent> = emptyList(),
)

data class RainTimelineReplay(
  val slides: List<RainLiveSlideEvent>,
  val possibleGap: Boolean,
)

internal fun normalizeCurrentLessons(payload: JSONObject): List<RainCurrentLesson> {
  val code = payload.optInt("code", 0)
  if (code != 0) throw IOException("雨课堂请求失败：${payload.optString("msg", "code $code")}")
  val dataValue = payload.opt("data")
  val data = dataValue as? JSONObject ?: payload
  val list = data.optJSONArray("onLessonClassrooms")
    ?: data.optJSONArray("result")
    ?: dataValue as? JSONArray
    ?: throw IOException("无法识别雨课堂当前课堂列表")
  return buildList {
    for (index in 0 until list.length()) {
      val item = list.optJSONObject(index) ?: continue
      if (item.stringId("status") != "1") continue
      val lessonId = item.stringId("lessonId", "lesson_id", "id") ?: continue
      val course = item.optJSONObject("course") ?: JSONObject()
      add(
        RainCurrentLesson(
          lessonId = lessonId,
          classroomId = item.stringId("classroomId", "classroom_id"),
          courseId = item.stringId("courseId", "course_id") ?: course.stringId("id"),
          name = item.optString(
            "name",
            item.optString("title", item.optString("lesson_name", course.optString("name", "正在进行的课堂"))),
          ).ifBlank { "正在进行的课堂" },
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
  return courses.singleOrNull { course ->
    (lesson.classroomId == null || course.classroomId == lesson.classroomId) &&
      (lesson.courseId == null || course.courseId == lesson.courseId)
  }
}

internal fun normalizeLiveMessage(message: JSONObject): RainLiveMessage {
  val op = message.optString("op")
  if (op == "lessonfinished") return RainLiveMessage(finished = true)
  if (op == "hello" || op == "fetchtimeline") {
    val slides = buildList {
      val timeline = message.optJSONArray("timeline") ?: JSONArray()
      for (index in 0 until timeline.length()) {
        val item = timeline.optJSONObject(index) ?: continue
        val presentationId = item.stringId("pres") ?: continue
        val sourceIndex = item.intValue("si") ?: continue
        add(RainLiveSlideEvent(presentationId, sourceIndex))
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
  return if (presentationId != null && sourceIndex != null) {
    RainLiveMessage(slides = listOf(RainLiveSlideEvent(presentationId, sourceIndex)))
  } else {
    RainLiveMessage()
  }
}

internal fun selectTimelineReplay(
  slides: List<RainLiveSlideEvent>,
  cursor: String?,
  initialConnection: Boolean,
): RainTimelineReplay {
  if (slides.isEmpty()) return RainTimelineReplay(emptyList(), possibleGap = false)
  if (initialConnection) return RainTimelineReplay(listOf(slides.last()), possibleGap = false)
  if (cursor != null) {
    val cursorIndex = slides.indexOfLast { "${it.presentationId}:${it.sourceIndex}" == cursor }
    if (cursorIndex >= 0) return RainTimelineReplay(slides.drop(cursorIndex + 1), possibleGap = false)
  }
  return RainTimelineReplay(listOf(slides.last()), possibleGap = true)
}

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
