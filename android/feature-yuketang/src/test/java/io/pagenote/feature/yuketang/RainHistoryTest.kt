package io.pagenote.feature.yuketang

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class RainHistoryTest {
  @Test
  fun history_classroom_activity_uses_courseware_id_as_lesson_id_not_presentation_id() {
    val lessons = normalizeHistoryLessons(JSONObject("""
      {"errcode":0,"data":{"activities":[
        {"type":14,"id":901,"courseware_id":"1785174499833394999","title":"Lecture"},
        {"type":3,"id":902,"courseware_id":"legacy-lesson","title":"Legacy lecture"},
        {"type":4,"id":903,"courseware_id":"quiz-id","title":"Quiz"},
        {"type":6,"id":904,"courseware_id":"notice-id","title":"Notice"}
      ]}}
    """.trimIndent()), "room")

    assertEquals(listOf("1785174499833394999", "legacy-lesson"), lessons.map { it.lessonId })
    assertEquals(listOf("report-1785174499833394999", "report-legacy-lesson"), lessons.map { it.presentationId })
  }

  @Test
  fun historical_import_uses_report_decks_and_visible_pages_in_source_order() = runBlocking {
    val lesson = RainLesson("room", "lesson-1", "report-lesson-1", "Lecture")
    val requests = mutableListOf<String>()
    val slides = loadRainImportSlides(lesson) { path ->
      requests.add(path)
      when (path) {
        "/api/v3/classroom-report/student/lesson-info?lesson_id=lesson-1" -> JSONObject("""
          {"code":0,"data":{"presentationIds":["deck-a","deck-b"]}}
        """)
        "/api/v3/classroom-report/student/ppt?lesson_id=lesson-1&presentationId=deck-a" -> JSONObject("""
          {"code":0,"data":{"slideList":[
            {"id":"a2","index":2,"presentationId":"deck-a","visible":true,"cover":"https://qn-scd1.yuketang.cn/pageA2"},
            {"id":"hidden","index":0,"visible":false,"cover":"https://qn-scd1.yuketang.cn/hidden"},
            {"id":"a1","index":1,"presentationId":"deck-a","visible":true,"cover":"https://qn-scd1.yuketang.cn/pageA1"}
          ],"presentation":{"cover":"https://qn-scd1.yuketang.cn/thumbnail"}}}
        """)
        "/api/v3/classroom-report/student/ppt?lesson_id=lesson-1&presentationId=deck-b" -> JSONObject("""
          {"code":0,"data":{"slideList":[
            {"id":"b0","index":0,"visible":true,"cover":"https://qn-scd1.yuketang.cn/pageB0"}
          ]}}
        """)
        else -> throw IOException("Wrong endpoint: legacy live presentation fetch returns HTTP 401")
      }
    }

    assertEquals(listOf("a1", "a2", "b0"), slides.map { it.pageId })
    assertEquals(listOf("deck-a", "deck-a", "deck-b"), slides.map { it.presentationId })
    assertEquals(listOf(1, 2, 0), slides.map { it.sourceIndex })
    assertEquals(3, requests.size)
  }

  @Test
  fun history_service_error_is_not_treated_as_empty_success() {
    assertThrows(IOException::class.java) {
      normalizeHistoryLessons(JSONObject("""{"errcode":401,"errmsg":"Session expired"}"""), "room")
    }
  }

  @Test
  fun report_only_imports_explicitly_visible_pages() = runBlocking {
    val slides = loadRainImportSlides(RainLesson("room", "lesson", "", "Lecture")) { path ->
      if (path.contains("lesson-info")) JSONObject("""{"code":0,"data":{"presentationIds":["deck"]}}""")
      else JSONObject("""{"code":0,"data":{"slideList":[
        {"id":"open","index":0,"visible":true,"cover":"https://qn-scd1.yuketang.cn/open"},
        {"id":"missing","index":1,"cover":"https://qn-scd1.yuketang.cn/missing"},
        {"id":"null","index":2,"visible":null,"cover":"https://qn-scd1.yuketang.cn/null"},
        {"id":"closed","index":3,"visible":false,"cover":"https://qn-scd1.yuketang.cn/closed"}
      ]}}""")
    }
    assertEquals(listOf("open"), slides.map { it.pageId })
  }

  @Test
  fun malformed_visible_page_cannot_silently_produce_partial_import() {
    assertThrows(IOException::class.java) {
      runBlocking {
        loadRainImportSlides(RainLesson("room", "lesson", "", "Lecture")) { path ->
          if (path.contains("lesson-info")) JSONObject("""{"code":0,"data":{"presentationIds":["deck"]}}""")
          else JSONObject("""{"code":0,"data":{"slideList":[
            {"id":"open","index":0,"visible":true,"cover":"https://qn-scd1.yuketang.cn/open"},
            {"id":"incomplete","index":1,"visible":true}
          ]}}""")
        }
      }
    }
  }

  @Test
  fun report_without_decks_is_an_error_not_an_empty_success() {
    assertThrows(IOException::class.java) {
      runBlocking {
        loadRainImportSlides(RainLesson("room", "lesson", "", "Lecture")) {
          JSONObject("""{"code":0,"data":{"presentationIds":[]}}""")
        }
      }
    }
  }

  @Test
  fun ongoing_report_failure_is_distinguishable_from_authentication_failure() {
    val error = assertThrows(IOException::class.java) {
      rainObjectData(JSONObject("""{"code":50080,"msg":"LESSON_ON","data":{}}"""))
    }
    assertEquals("RainLessonOngoingException", error.javaClass.simpleName)
  }
}
