package io.pagenote.feature.yuketang

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
      listOf(RainLiveSlideEvent("fixture-presentation-b", 1)),
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
