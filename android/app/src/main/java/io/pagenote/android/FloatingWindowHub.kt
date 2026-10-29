package io.pagenote.android

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 悬浮笔记草稿：锁定项目、页码和打开时的笔记基线，避免跨项目或覆盖并发编辑。 */
data class FloatingNoteDraft(
  val page: Int,
  val text: String,
  val projectKey: String = "",
  val baseText: String = text,
  val baseRevision: String = "",
)

/**
 * 悬浮笔记小窗回写语义（移植差距清单 #24）：悬浮窗编辑的是「打开时的
 * 那一页」草稿；主界面收集保存请求时项目、页码与笔记基线都必须一致，
 * 防止主界面切项目/修改笔记后把悬浮窗旧草稿写到当前会话。
 */
object FloatingNoteWindowPolicy {
  /** 打开悬浮窗时注入当前页草稿。 */
  fun initialDraft(
    currentPage: Int,
    noteDraft: String,
    projectKey: String = "",
    noteRevision: String = "",
  ): FloatingNoteDraft {
    require(currentPage >= 1) { "bad note page" }
    return FloatingNoteDraft(
      page = currentPage,
      text = noteDraft,
      projectKey = projectKey,
      baseText = noteDraft,
      baseRevision = noteRevision,
    )
  }

  /** 悬浮窗编辑：只更新文本，页码保持绑定。 */
  fun updateText(draft: FloatingNoteDraft, text: String): FloatingNoteDraft =
    draft.copy(text = text)

  /** 保存判定：草稿存在且页码与当前页一致才受理。 */
  fun canSave(draft: FloatingNoteDraft?, currentPage: Int): Boolean =
    draft != null && draft.page == currentPage

  /** 受检保存：项目、页码、revision 未变，且主界面仍是原基线或悬浮窗待重试文本。 */
  fun canSave(
    draft: FloatingNoteDraft?,
    currentProjectKey: String,
    currentPage: Int,
    currentText: String,
    currentRevision: String,
  ): Boolean = draft != null &&
    draft.projectKey == currentProjectKey &&
    draft.page == currentPage &&
    draft.baseRevision == currentRevision &&
    (draft.baseText == currentText || draft.text == currentText)
}

/**
 * 悬浮窗服务 ↔ 主界面通信枢纽（同进程 companion 状态，模式同
 * [ScreenCaptureService.phase]）。服务写草稿/发请求；主界面注入初始
 * 草稿并消费保存与截屏请求。
 */
object FloatingWindowHub {
  enum class Phase { IDLE, ACTIVE }

  private val _phase = MutableStateFlow(Phase.IDLE)
  val phase: StateFlow<Phase> = _phase.asStateFlow()

  private val _noteDraft = MutableStateFlow<FloatingNoteDraft?>(null)
  val noteDraft: StateFlow<FloatingNoteDraft?> = _noteDraft.asStateFlow()

  private val _noteSaveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
  val noteSaveRequests: SharedFlow<Unit> = _noteSaveRequests.asSharedFlow()

  private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
  val captureRequests: SharedFlow<Unit> = _captureRequests.asSharedFlow()

  /** 服务生命周期：进入前台面板时 ACTIVE，销毁时回 IDLE 并清空草稿。 */
  fun setActive(active: Boolean) {
    _phase.value = if (active) Phase.ACTIVE else Phase.IDLE
    if (!active) _noteDraft.value = null
  }

  /** 主界面打开悬浮窗：注入当前页草稿（悬浮窗 EditText 的初始内容）。 */
  fun attach(
    currentPage: Int,
    noteDraft: String,
    projectKey: String = "",
    noteRevision: String = "",
  ): FloatingNoteDraft {
    val draft = FloatingNoteWindowPolicy.initialDraft(currentPage, noteDraft, projectKey, noteRevision)
    _noteDraft.value = draft
    return draft
  }

  /** 悬浮窗编辑回传（页码绑定不变）。 */
  fun updateNoteText(text: String) {
    val current = _noteDraft.value ?: return
    _noteDraft.value = FloatingNoteWindowPolicy.updateText(current, text)
  }

  /** 保存成功后推进主界面基线；若服务端草稿又有新输入，只推进基线不覆盖输入。 */
  fun markNoteSaved(savedText: String, noteRevision: String) {
    val current = _noteDraft.value ?: return
    _noteDraft.value = current.copy(baseText = savedText, baseRevision = noteRevision)
  }

  /** 悬浮窗「保存」按钮：受理返回 true（主界面收集 [noteSaveRequests]）。 */
  fun requestNoteSave(): Boolean = _noteSaveRequests.tryEmit(Unit)

  /** 悬浮球「截屏」按钮：受理返回 true（主界面收集 [captureRequests]）。 */
  fun requestCapture(): Boolean = _captureRequests.tryEmit(Unit)
}

/**
 * 悬浮截屏入口（批次 10 预留 [FloatingCaptureEntry] 的 #24 实现）：
 * 悬浮窗服务运行中即视为可用；点悬浮球截屏按钮经 hub 请求主界面发起
 * MediaProjection 授权（授权必须由 Activity 弹出，见
 * [ScreenCaptureLauncher.launchViaActivity]）。
 */
internal class FloatingWindowCaptureEntry : FloatingCaptureEntry {
  override fun isAvailable(): Boolean = FloatingWindowHub.phase.value == FloatingWindowHub.Phase.ACTIVE

  override fun requestLaunch(): Boolean {
    if (!isAvailable()) return false
    return FloatingWindowHub.requestCapture()
  }
}
