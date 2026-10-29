package io.pagenote.android.flashcard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.pagenote.android.R
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 闪卡会话控制器（移植批次 11 / 差距清单 #20）：制卡入口、复习队列、
 * 卡片库/统计状态。DB 访问统一在 IO 线程，状态以 Compose state 暴露。
 * 状态提示复用 readerSession.noteStatus（与搜索控制器同语义）。
 */
class FlashcardController(
  private val context: Context,
  private val repository: FlashcardRepository,
) {
  var showCreate by mutableStateOf(false)
    private set
  var createDraft by mutableStateOf<FlashcardCapture.Draft?>(null)
    private set
  var showReview by mutableStateOf(false)
    private set
  var showLibrary by mutableStateOf(false)
    private set

  var busy by mutableStateOf(false)
    private set
  var createError by mutableStateOf<String?>(null)
    private set
  var reviewError by mutableStateOf<String?>(null)
    private set

  /** 复习队列与进度。 */
  var dueCards by mutableStateOf<List<FlashcardCard>>(emptyList())
    private set
  var reviewIndex by mutableIntStateOf(0)
    private set
  var revealed by mutableStateOf(false)
    private set
  var reviewedInSession by mutableIntStateOf(0)
    private set

  /** 卡片库与统计（打开管理面板时加载）。 */
  var libraryCards by mutableStateOf<List<FlashcardCard>>(emptyList())
    private set
  var stats by mutableStateOf<ReviewStats.Stats?>(null)
    private set

  /** 制卡入口：捕获草稿（选区或光标段落）→ 打开创建对话框。 */
  fun requestCreate(draft: FlashcardCapture.Draft) {
    createError = null
    createDraft = draft
    showCreate = true
  }

  suspend fun createCard(projectId: String, back: String, tags: String): Boolean {
    val draft = createDraft ?: return false
    busy = true
    createError = null
    try {
      withContext(Dispatchers.IO) { repository.create(projectId, draft, back, tags, System.currentTimeMillis()) }
      showCreate = false
      createDraft = null
      return true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      createError = context.getString(R.string.flashcard_create_failed, error.message ?: "")
      return false
    } finally {
      busy = false
    }
  }

  fun dismissCreate() {
    showCreate = false
    createDraft = null
    createError = null
  }

  /** 打开复习：加载到期卡片。 */
  suspend fun startReview(projectId: String): Boolean {
    busy = true
    reviewError = null
    try {
      val cards = withContext(Dispatchers.IO) {
        repository.dueCards(projectId, System.currentTimeMillis())
      }
      dueCards = cards
      reviewIndex = 0
      reviewedInSession = 0
      revealed = false
      showReview = true
      return true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      reviewError = error.message ?: error.javaClass.simpleName
      return false
    } finally {
      busy = false
    }
  }

  fun reveal() {
    revealed = true
  }

  /** 评分：当前卡排程后前进；队列走完后自动刷新剩余到期卡。 */
  suspend fun grade(projectId: String, outcome: ReviewScheduler.ReviewOutcome): Boolean {
    val card = dueCards.getOrNull(reviewIndex) ?: return false
    busy = true
    reviewError = null
    try {
      withContext(Dispatchers.IO) { repository.review(card, outcome, System.currentTimeMillis()) }
      reviewedInSession += 1
      revealed = false
      val nextIndex = reviewIndex + 1
      if (nextIndex >= dueCards.size) {
        val remaining = withContext(Dispatchers.IO) {
          repository.dueCards(projectId, System.currentTimeMillis())
        }
        dueCards = remaining
        reviewIndex = 0
      } else {
        reviewIndex = nextIndex
      }
      return true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      reviewError = error.message ?: error.javaClass.simpleName
      return false
    } finally {
      busy = false
    }
  }

  fun dismissReview() {
    showReview = false
    reviewError = null
  }

  /** 打开卡片库/统计面板：加载全部卡片与统计。 */
  suspend fun openLibrary(projectId: String): Boolean {
    busy = true
    reviewError = null
    try {
      withContext(Dispatchers.IO) {
        libraryCards = repository.allCards(projectId)
        stats = repository.stats(projectId, System.currentTimeMillis(), ZoneId.systemDefault())
      }
      showLibrary = true
      return true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      reviewError = error.message ?: error.javaClass.simpleName
      return false
    } finally {
      busy = false
    }
  }

  suspend fun deleteCard(projectId: String, card: FlashcardCard): Boolean {
    busy = true
    reviewError = null
    try {
      withContext(Dispatchers.IO) { repository.deleteCard(card) }
      libraryCards = withContext(Dispatchers.IO) { repository.allCards(projectId) }
      stats = withContext(Dispatchers.IO) {
        repository.stats(projectId, System.currentTimeMillis(), ZoneId.systemDefault())
      }
      return true
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      reviewError = error.message ?: error.javaClass.simpleName
      return false
    } finally {
      busy = false
    }
  }

  fun dismissLibrary() {
    showLibrary = false
    reviewError = null
  }

  fun dismissErrors() {
    createError = null
    reviewError = null
  }
}
