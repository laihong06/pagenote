package io.pagenote.feature.yuketang

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder

class RainLessonOngoingException : IOException("该课堂正在上课，请新建项目并开始实时采集")

internal fun normalizeHistoryLessons(payload: JSONObject, classroomId: String): List<RainLesson> {
  val data = rainObjectData(payload)
  val activities = data.optJSONArray("activities") ?: throw IOException("无法识别雨课堂历史课件")
  return buildList {
    for (index in 0 until activities.length()) {
      val item = activities.optJSONObject(index) ?: continue
      val type = item.rainId("type")?.toIntOrNull()
      val presentation = item.rainId("presentation_id", "presentationId")
      if (type != null && type !in setOf(3, 14) && presentation == null) continue
      val lessonId = item.rainId("lesson_id", "lessonId")
        ?: if (type == 3 || type == 14) item.rainId("courseware_id") else null
      if (lessonId == null && presentation == null) continue
      add(RainLesson(
        classroomId = classroomId,
        lessonId = lessonId,
        presentationId = presentation ?: "report-$lessonId",
        name = item.optString("title", item.optString("name", "未命名课件")),
        createdAt = item.optString("create_time", item.optString("created_at", "")).ifBlank { null },
      ))
    }
  }
}

internal suspend fun loadRainImportSlides(
  lesson: RainLesson,
  requestJson: suspend (String) -> JSONObject,
): List<RainSlide> {
  val lessonId = lesson.lessonId
  if (!lessonId.isNullOrBlank()) {
    val lessonQuery = "lesson_id=${encodeRainId(lessonId)}"
    val report = rainObjectData(requestJson("/api/v3/classroom-report/student/lesson-info?$lessonQuery"))
    val presentations = report.optJSONArray("presentationIds")
      ?: throw IOException("无法识别雨课堂课后报告课件列表")
    val presentationIds = (0 until presentations.length()).mapNotNull { index ->
      presentations.opt(index)?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank)
    }.distinct()
    val slides = mutableListOf<RainSlide>()
    for (presentationId in presentationIds) {
      val path = "/api/v3/classroom-report/student/ppt?$lessonQuery&presentationId=${encodeRainId(presentationId)}"
      val deck = rainObjectData(requestJson(path))
      val pages = deck.optJSONArray("slideList") ?: throw IOException("无法识别雨课堂课后课件页面")
      slides.addAll(normalizeSlides(pages, presentationId, visibleOnly = true).sortedBy { it.sourceIndex })
    }
    return slides.distinctBy { it.presentationId to it.pageId }
      .also { if (it.isEmpty()) throw IOException("该课堂没有已开放的课件页面") }
  }
  val path = "/api/v3/lesson/presentation/fetch?presentation_id=${encodeRainId(lesson.presentationId)}"
  return normalizeRainPresentation(requestJson(path), lesson.presentationId)
}

internal fun normalizeRainPresentation(payload: JSONObject, presentationId: String): List<RainSlide> {
  val slides = rainObjectData(payload).optJSONArray("slides") ?: throw IOException("无法识别雨课堂课件页面")
  return normalizeSlides(slides, presentationId, visibleOnly = false)
    .also { if (it.isEmpty()) throw IOException("课件没有可用页面") }
}

private fun normalizeSlides(slides: JSONArray, presentationId: String, visibleOnly: Boolean): List<RainSlide> =
  buildList {
    for (order in 0 until slides.length()) {
      val item = slides.optJSONObject(order) ?: continue
      if (visibleOnly && item.opt("visible")?.toString()?.lowercase() !in setOf("true", "1")) continue
      val pageId = item.rainId("id", "pageId", "page_id", "slideId", "slide_id")
      val image = when (val cover = item.opt("cover")) {
        is String -> cover
        is JSONObject -> cover.optString("url", cover.optString("src", cover.optString("image_url")))
        else -> item.optString("imageUrl", item.optString("image_url", ""))
      }
      if (pageId == null || image.isBlank()) {
        if (visibleOnly) throw IOException("雨课堂已开放的课件页面数据不完整")
        continue
      }
      add(RainSlide(pageId, item.rainId("index")?.toIntOrNull() ?: order, presentationId,
        URL(URL("https://www.yuketang.cn"), image).toString()))
    }
  }

internal fun rainObjectData(payload: JSONObject): JSONObject {
  val code = payload.optInt("code", payload.optInt("errcode", 0))
  if (code != 0 && payload.optString("msg", payload.optString("errmsg")) == "LESSON_ON") {
    throw RainLessonOngoingException()
  }
  if (code != 0) throw IOException("雨课堂请求失败：${payload.optString("msg", payload.optString("errmsg", "code $code"))}")
  return payload.optJSONObject("data") ?: payload
}

private fun encodeRainId(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun JSONObject.rainId(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
  if (!has(key) || isNull(key)) null else opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
}
