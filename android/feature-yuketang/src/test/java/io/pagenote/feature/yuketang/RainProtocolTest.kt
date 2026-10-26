package io.pagenote.feature.yuketang

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RainProtocolTest {
  @Test
  fun current_lesson_fixture_keeps_only_active_class_with_string_ids() {
    val payload = JSONObject(fixture("current-lessons.json").readText())

    assertEquals(
      listOf(
        RainCurrentLesson(
          lessonId = "9282",
          classroomId = "fixture-classroom-1",
          courseId = "7351",
          name = "Fixture live lecture",
        ),
      ),
      normalizeCurrentLessons(payload),
    )
  }

  @Test
  fun current_lesson_without_status_is_active_and_uses_course_name_fallbacks() {
    val payload = JSONObject(fixture("current-lessons-no-status.json").readText())

    assertEquals(
      listOf(
        RainCurrentLesson(
          lessonId = "9283",
          classroomId = "fixture-classroom-2",
          courseId = null,
          name = "Fixture course without status",
        ),
      ),
      normalizeCurrentLessons(payload),
    )
  }

  @Test
  fun reconnect_timeline_replays_only_events_after_last_checkpoint() {
    val message = normalizeLiveMessage(
      JSONObject(fixture("live-reconnect-timeline.json").readText()),
    )

    assertEquals(
      listOf(
        RainLiveSlideEvent("fixture-presentation-a", 0),
        RainLiveSlideEvent("fixture-presentation-a", 1),
        RainLiveSlideEvent("fixture-presentation-b", 0),
        RainLiveSlideEvent("fixture-presentation-b", 1),
      ),
      message.slides,
    )
    assertEquals(
      message.slides,
      selectTimelineReplay(message.slides, null, initialConnection = true).slides,
    )

    val resumed = selectTimelineReplay(
      message.slides,
      "fixture-presentation-a:0",
      initialConnection = false,
    )
    assertEquals(false, resumed.possibleGap)
    assertEquals(
      listOf(
        RainLiveSlideEvent("fixture-presentation-a", 1),
        RainLiveSlideEvent("fixture-presentation-b", 0),
        RainLiveSlideEvent("fixture-presentation-b", 1),
      ),
      resumed.slides,
    )

    val missing = selectTimelineReplay(
      message.slides,
      "older-presentation:9",
      initialConnection = false,
    )
    assertEquals(true, missing.possibleGap)
    assertEquals(listOf(RainLiveSlideEvent("fixture-presentation-b", 1)), missing.slides)
  }

  @Test
  fun initial_timeline_keeps_sid_references_and_ignores_problem_entries() {
    val message = normalizeLiveMessage(JSONObject("""
      {"op":"hello","presentation":"deck-b","slideindex":0,"timeline":[
        {"type":"slide","pres":"deck-a","sid":"a0"},
        {"type":"problem","pres":"deck-a","sid":"quiz"},
        {"type":"slide","pres":"deck-a","sid":"a1"},
        {"type":"slide","pres":"deck-b","sid":"b0"}
      ]}
    """.trimIndent()))
    assertEquals(4, message.slides.size)
    assertEquals(listOf("a0", "a1", "b0", null), message.slides.map { it.pageId })
    val resumed = selectTimelineReplay(message.slides, "deck-a:1", false, "deck-a:page:a1")
    assertEquals(false, resumed.possibleGap)
    assertEquals(message.slides.drop(2), resumed.slides)
  }

  @Test
  fun sid_reference_resolves_actual_page_without_guessing_its_index() {
    val pages = listOf(
      RainSlide("a1", 1, "deck", "https://qn-scd1.yuketang.cn/page1"),
      RainSlide("a4", 4, "deck", "https://qn-scd1.yuketang.cn/page4"),
    )
    assertEquals(pages[1], resolveRainLiveSlide(RainLiveSlideEvent("deck", null, "a4"), pages))
    assertThrows(java.io.IOException::class.java) {
      resolveRainLiveSlide(RainLiveSlideEvent("deck", null, "unavailable"), pages)
    }
  }

  @Test
  fun initial_replay_deduplicates_repeated_visits_without_dropping_earlier_decks() {
    val a = RainLiveSlideEvent("deck-a", null, "a0")
    val b = RainLiveSlideEvent("deck-b", null, "b0")
    assertEquals(listOf(a, b), selectTimelineReplay(listOf(a, a, b, a), null, true).slides)
  }

  @Test
  fun current_lesson_course_matching_rejects_conflicting_identifiers() {
    val courses = listOf(
      RainCourse("c1", "room1", "Course 1"),
      RainCourse("c2", "room2", "Course 2"),
    )

    assertEquals(
      courses[0],
      matchCurrentLessonCourse(
        RainCurrentLesson("l1", "room1", "c1", "Live lecture"),
        courses,
      ),
    )
    assertEquals(
      null,
      matchCurrentLessonCourse(
        RainCurrentLesson("l1", "missing", "c1", "Live lecture"),
        courses,
      ),
    )
  }

  @Test
  fun current_lesson_prefers_matching_classroom_when_course_id_is_stale() {
    val courses = listOf(
      RainCourse("c1", "room1", "Course 1"),
      RainCourse("c2", "room2", "Course 2"),
    )

    assertEquals(
      courses[0],
      matchCurrentLessonCourse(
        RainCurrentLesson("l1", "room1", "stale-course-id", "Live lecture"),
        courses,
      ),
    )
  }

  @Test
  fun rain_api_headers_include_web_client_auth_and_bearer_prefix_once() {
    val headers = rainApiHeaders(
      cookie = "sessionid=fixture",
      authorization = "bearer access-token",
      referer = "https://www.yuketang.cn/web",
      userAgent = "fixture-agent",
    )

    assertEquals("sessionid=fixture", headers["Cookie"])
    assertEquals("Bearer access-token", headers["Authorization"])
    assertEquals("ykt", headers["xtbz"])
    assertEquals("h5", headers["X-Client"])
    assertEquals("https://www.yuketang.cn", headers["Origin"])
    assertEquals("fixture-agent", headers["User-Agent"])
    assertEquals(null, bearerAuthorization(null))
    assertEquals(null, bearerAuthorization("Bearer "))
  }

  @Test
  fun http_error_reports_safe_endpoint_path_without_query_parameters() {
    assertEquals(
      "雨课堂请求失败（HTTP 401，/api/v3/lesson/presentation/fetch）",
      rainHttpError(
        "https://www.yuketang.cn/api/v3/lesson/presentation/fetch?presentation_id=secret-id",
        401,
      ).message,
    )
  }

  @Test
  fun rain_page_identity_keeps_the_same_page_distinct_between_classrooms() {
    val firstClass = RainSourcePageIdentity(
      classroomId = "classroom-a",
      presentationId = "presentation-shared",
      pageId = "page-shared",
      sha256 = "a".repeat(64),
    )

    assertEquals(firstClass, firstClass.copy())
    assertNotEquals(firstClass, firstClass.copy(classroomId = "classroom-b"))
  }

  private fun fixture(name: String): File {
    var directory: File? = File(System.getProperty("user.dir") ?: ".")
    while (directory != null) {
      val candidate = File(directory, "docs/spec/yuketang/fixtures/$name")
      if (candidate.isFile) return candidate
      directory = directory.parentFile
    }
    throw IllegalStateException("Shared Yuketang fixture not found: $name")
  }
}
