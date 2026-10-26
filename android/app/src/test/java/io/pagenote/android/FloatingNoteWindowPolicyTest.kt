package io.pagenote.android

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 悬浮笔记小窗回写语义 + 服务↔主界面枢纽（移植差距清单 #24）。
 *
 * [FloatingWindowHub] 是进程级单例：每个用例前后重置状态。
 */
class FloatingNoteWindowPolicyTest {

  @Before
  fun resetHub() {
    FloatingWindowHub.setActive(false)
  }

  @After
  fun tearDown() {
    FloatingWindowHub.setActive(false)
  }

  // --- 草稿绑定语义 -----------------------------------------------------------

  @Test
  fun initialDraftBindsCurrentPage() {
    val draft = FloatingNoteWindowPolicy.initialDraft(3, "hello")
    assertEquals(3, draft.page)
    assertEquals("hello", draft.text)
  }

  @Test
  fun initialDraftRejectsPageBelowOne() {
    try {
      FloatingNoteWindowPolicy.initialDraft(0, "x")
      throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun updateTextKeepsPageBinding() {
    val draft = FloatingNoteWindowPolicy.initialDraft(7, "old")
    val updated = FloatingNoteWindowPolicy.updateText(draft, "new text")
    assertEquals(7, updated.page)
    assertEquals("new text", updated.text)
  }

  @Test
  fun canSaveOnlyWhenPageStillCurrent() {
    val draft = FloatingNoteWindowPolicy.initialDraft(5, "text")
    assertTrue(FloatingNoteWindowPolicy.canSave(draft, currentPage = 5))
    assertFalse(FloatingNoteWindowPolicy.canSave(draft, currentPage = 6))
    assertFalse(FloatingNoteWindowPolicy.canSave(null, currentPage = 5))
  }

  // --- 服务↔主界面枢纽 --------------------------------------------------------

  @Test
  fun attachPublishesDraftForTheService() = runBlocking {
    val draft = FloatingWindowHub.attach(2, "page two draft")
    assertEquals(draft, FloatingWindowHub.noteDraft.value)
    assertEquals(2, FloatingWindowHub.noteDraft.value?.page)
  }

  @Test
  fun updateNoteTextEditsBoundDraft() = runBlocking {
    FloatingWindowHub.attach(4, "start")
    FloatingWindowHub.updateNoteText("edited")
    assertEquals(4, FloatingWindowHub.noteDraft.value?.page)
    assertEquals("edited", FloatingWindowHub.noteDraft.value?.text)
  }

  @Test
  fun updateNoteTextWithoutAttachIsIgnored() {
    FloatingWindowHub.updateNoteText("ghost")
    assertNull(FloatingWindowHub.noteDraft.value)
  }

  @Test
  fun setActiveClearsDraftOnStop() = runBlocking {
    FloatingWindowHub.setActive(true)
    FloatingWindowHub.attach(1, "draft")
    FloatingWindowHub.setActive(false)
    assertEquals(FloatingWindowHub.Phase.IDLE, FloatingWindowHub.phase.value)
    assertNull(FloatingWindowHub.noteDraft.value)
  }

  @Test
  fun noteSaveRequestsAreEmitted() = runBlocking {
    FloatingWindowHub.setActive(true)
    val request = async(start = CoroutineStart.UNDISPATCHED) {
      FloatingWindowHub.noteSaveRequests.first()
    }
    assertTrue(FloatingWindowHub.requestNoteSave())
    assertEquals(Unit, request.await())
  }

  @Test
  fun captureRequestsRequireActiveService() = runBlocking {
    // 服务未运行：悬浮截屏入口不可用，请求不受理（回退 Activity 路径）。
    assertFalse(FloatingWindowCaptureEntry().isAvailable())
    assertFalse(FloatingWindowCaptureEntry().requestLaunch())
    // 服务运行：入口可用，请求经 hub 受理并广播。
    FloatingWindowHub.setActive(true)
    assertTrue(FloatingWindowCaptureEntry().isAvailable())
    val request = async(start = CoroutineStart.UNDISPATCHED) {
      FloatingWindowHub.captureRequests.first()
    }
    assertTrue(FloatingWindowCaptureEntry().requestLaunch())
    assertEquals(Unit, request.await())
  }
}
