package io.pagenote.android.flashcard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pagenote.android.R
import kotlinx.coroutines.launch

/**
 * 闪卡 UI（移植批次 11 / 差距清单 #20）：
 * - [FlashcardCreateDialog] 选区/笔记段落 → 卡片（正面预览 + 背面 + 标签）；
 * - [FlashcardReviewDialog] 复习界面（翻面 → 四级评分，含进度与完成态）；
 * - [FlashcardLibraryDialog] 卡片库（统计 + 成就 + 定位回跳/删除）。
 */

@Composable
internal fun FlashcardCreateDialog(
  draft: FlashcardCapture.Draft,
  busy: Boolean,
  error: String?,
  onCreate: suspend (back: String, tags: String) -> Boolean,
  onDismiss: () -> Unit,
) {
  var back by rememberSaveable { mutableStateOf("") }
  var tags by rememberSaveable { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  AlertDialog(
    modifier = Modifier.testTag("flashcard_create_dialog"),
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(stringResource(R.string.flashcard_create_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(R.string.flashcard_front_label),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          draft.front,
          modifier = Modifier.testTag("flashcard_front"),
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 6,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          stringResource(flashcardSourceLabelRes(draft.sourceKind)),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = back,
          onValueChange = { back = it },
          modifier = Modifier.fillMaxWidth().testTag("flashcard_back_input"),
          label = { Text(stringResource(R.string.flashcard_back_label)) },
          minLines = 2,
        )
        OutlinedTextField(
          value = tags,
          onValueChange = { tags = it },
          modifier = Modifier.fillMaxWidth().testTag("flashcard_tags_input"),
          label = { Text(stringResource(R.string.flashcard_tags_label)) },
          singleLine = true,
        )
        if (busy) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
          }
        }
        error?.let {
          Text(
            it,
            modifier = Modifier.testTag("flashcard_create_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("flashcard_create_confirm"),
        enabled = !busy && draft.front.isNotBlank(),
        onClick = { scope.launch { onCreate(back, tags) } },
      ) { Text(stringResource(R.string.flashcard_create_confirm)) }
    },
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("flashcard_create_cancel"),
        enabled = !busy,
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
internal fun FlashcardReviewDialog(
  cards: List<FlashcardCard>,
  index: Int,
  revealed: Boolean,
  reviewedInSession: Int,
  busy: Boolean,
  error: String?,
  onReveal: () -> Unit,
  onGrade: (ReviewScheduler.ReviewOutcome) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    modifier = Modifier.testTag("flashcard_review_dialog"),
    onDismissRequest = { if (!busy) onDismiss() },
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.flashcard_review_title))
        if (cards.isNotEmpty() && index < cards.size) {
          Text(
            stringResource(R.string.flashcard_review_progress, index + 1, cards.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val current = cards.getOrNull(index)
        when {
          current == null -> {
            Text(
              if (reviewedInSession > 0) {
                stringResource(R.string.flashcard_review_done, reviewedInSession)
              } else {
                stringResource(R.string.flashcard_review_empty)
              },
              modifier = Modifier.testTag(
                if (reviewedInSession > 0) "flashcard_review_done" else "flashcard_review_empty",
              ),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          else -> {
            FlashcardMetaRow(current)
            Text(
              current.front,
              modifier = Modifier.testTag("flashcard_card_front"),
              style = MaterialTheme.typography.bodyLarge,
            )
            if (revealed) {
              HorizontalDivider()
              if (current.back.isNotBlank()) {
                Text(
                  current.back,
                  modifier = Modifier.testTag("flashcard_card_back"),
                  style = MaterialTheme.typography.bodyMedium,
                )
              } else {
                Text(
                  stringResource(R.string.flashcard_no_back),
                  modifier = Modifier.testTag("flashcard_card_back"),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewScheduler.ReviewOutcome.entries.forEach { outcome ->
                  OutlinedButton(
                    modifier = Modifier.testTag("flashcard_grade_${outcome.name.lowercase()}"),
                    enabled = !busy,
                    onClick = { onGrade(outcome) },
                  ) { Text(stringResource(flashcardOutcomeLabelRes(outcome))) }
                }
              }
            } else {
              Button(
                modifier = Modifier.testTag("flashcard_reveal"),
                enabled = !busy,
                onClick = onReveal,
              ) { Text(stringResource(R.string.flashcard_show_back)) }
            }
          }
        }
        if (busy) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
          }
        }
        error?.let {
          Text(
            it,
            modifier = Modifier.testTag("flashcard_review_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("flashcard_review_close"),
        enabled = !busy,
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
internal fun FlashcardLibraryDialog(
  cards: List<FlashcardCard>,
  stats: ReviewStats.Stats?,
  achievements: List<ReviewStats.Achievement>,
  busy: Boolean,
  error: String?,
  onLocate: (FlashcardCard) -> Unit,
  onDelete: (FlashcardCard) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    modifier = Modifier.testTag("flashcard_library_dialog"),
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(stringResource(R.string.flashcard_library_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        stats?.let { stat ->
          Column(modifier = Modifier.testTag("flashcard_stats").verticalScroll(rememberScrollState())) {
            Text(
              stringResource(R.string.flashcard_stats_total, stat.totalCards) + " · " +
                stringResource(R.string.flashcard_stats_due, stat.dueNow),
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              stringResource(R.string.flashcard_stats_reviews_today, stat.reviewedToday) + " · " +
                stringResource(R.string.flashcard_stats_accuracy, stat.accuracyTodayPercent),
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              stringResource(R.string.flashcard_stats_streak, stat.streakDays) + " · " +
                stringResource(R.string.flashcard_stats_mastered, stat.masteredCards),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        if (achievements.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            achievements.forEach { achievement ->
              AssistChip(
                modifier = Modifier.testTag("flashcard_achievement_${achievement.name.lowercase()}"),
                onClick = {},
                label = { Text(stringResource(flashcardAchievementLabelRes(achievement))) },
              )
            }
          }
        }
        HorizontalDivider()
        if (cards.isEmpty()) {
          Text(
            stringResource(R.string.flashcard_empty_library),
            modifier = Modifier.testTag("flashcard_empty_library"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("flashcard_cards"),
          ) {
            items(cards, key = { it.id }) { card ->
              FlashcardLibraryItem(
                card = card,
                busy = busy,
                onLocate = { onLocate(card) },
                onDelete = { onDelete(card) },
              )
            }
          }
        }
        if (busy) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
          }
        }
        error?.let {
          Text(
            it,
            modifier = Modifier.testTag("flashcard_library_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("flashcard_library_close"),
        enabled = !busy,
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun FlashcardMetaRow(card: FlashcardCard) {
  val sourceKind = card.sourceKind()
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(R.string.flashcard_page_label, card.page),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    sourceKind?.let {
      Text(
        stringResource(flashcardSourceLabelRes(it)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    card.tags.take(3).forEach { tag ->
      Text(
        "#$tag",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun FlashcardLibraryItem(
  card: FlashcardCard,
  busy: Boolean,
  onLocate: () -> Unit,
  onDelete: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("flashcard_card_item"),
  ) {
    FlashcardMetaRow(card)
    Text(
      card.front,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      TextButton(enabled = !busy, onClick = onLocate) {
        Text(stringResource(R.string.flashcard_locate))
      }
      TextButton(enabled = !busy, onClick = onDelete) {
        Text(
          stringResource(R.string.flashcard_delete),
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

internal fun flashcardSourceLabelRes(kind: FlashcardSourceKind): Int = when (kind) {
  FlashcardSourceKind.NOTE_SELECTION -> R.string.flashcard_source_selection
  FlashcardSourceKind.NOTE_PARAGRAPH -> R.string.flashcard_source_paragraph
  FlashcardSourceKind.MIND_MAP -> R.string.flashcard_source_mind_map
}

internal fun flashcardOutcomeLabelRes(outcome: ReviewScheduler.ReviewOutcome): Int = when (outcome) {
  ReviewScheduler.ReviewOutcome.AGAIN -> R.string.flashcard_grade_again
  ReviewScheduler.ReviewOutcome.HARD -> R.string.flashcard_grade_hard
  ReviewScheduler.ReviewOutcome.GOOD -> R.string.flashcard_grade_good
  ReviewScheduler.ReviewOutcome.EASY -> R.string.flashcard_grade_easy
}

internal fun flashcardAchievementLabelRes(achievement: ReviewStats.Achievement): Int = when (achievement) {
  ReviewStats.Achievement.FIRST_CARD -> R.string.flashcard_achievement_first_card
  ReviewStats.Achievement.CARD_COLLECTOR -> R.string.flashcard_achievement_card_collector
  ReviewStats.Achievement.FIRST_REVIEW -> R.string.flashcard_achievement_first_review
  ReviewStats.Achievement.STREAK_THREE -> R.string.flashcard_achievement_streak_three
  ReviewStats.Achievement.MASTERED_TEN -> R.string.flashcard_achievement_mastered_ten
}
