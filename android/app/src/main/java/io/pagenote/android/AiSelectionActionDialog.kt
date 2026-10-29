package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 选中文本 → AI 动作菜单（移植差距清单 #22）：语气 / 翻译 / 总结。
 * 执行走现有 agent 客户端链路（由 [run] 回调承载），完成后用户选择
 * 「插回笔记」（由 [insert] 回调承载，内部执行 applied 守卫）或关闭。
 */
@Composable
internal fun AiSelectionActionDialog(
  task: AiSelectionTask,
  run: suspend (AiSelectionAction, AiSelectionTask) -> AiSelectionRunResult,
  insert: suspend (AiSelectionAction, AiSelectionTask, String, String?) -> Boolean,
  onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val missingConfigMessage = stringResource(R.string.page_tutor_model_missing)
  val appliedStatusMessage = stringResource(R.string.ai_action_applied)
  val keptOpenStatusMessage = stringResource(R.string.ai_action_kept_open)
  var running by remember { mutableStateOf(false) }
  var resultText by rememberSaveable { mutableStateOf<String?>(null) }
  var resultAction by rememberSaveable { mutableStateOf<AiSelectionAction?>(null) }
  var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var status by remember { mutableStateOf<String?>(null) }

  fun runAction(action: AiSelectionAction) {
    scope.launch {
      running = true
      error = null
      status = null
      try {
        when (val outcome = run(action, task)) {
          is AiSelectionRunResult.Success -> {
            resultText = outcome.text
            resultAction = action
            reviewId = outcome.reviewId
          }
          AiSelectionRunResult.MissingConfig -> error = missingConfigMessage
          is AiSelectionRunResult.Failure -> error = outcome.message
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        error = failure.message ?: failure.javaClass.simpleName
      } finally {
        running = false
      }
    }
  }

  AlertDialog(
    onDismissRequest = { if (!running) onDismiss() },
    title = { Text(stringResource(R.string.ai_action_menu_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(R.string.ai_action_quote_label),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          task.quote,
          modifier = Modifier.testTag("ai_action_quote"),
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 6,
        )
        if (resultText == null && !running) {
          Text(stringResource(R.string.ai_action_pick_hint), style = MaterialTheme.typography.bodyMedium)
          AiSelectionAction.entries.forEach { action ->
            OutlinedButton(
              modifier = Modifier.fillMaxWidth().testTag("ai_action_${action.id}"),
              onClick = { runAction(action) },
            ) { Text(aiActionLabel(action)) }
          }
        }
        if (running) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            Text(stringResource(R.string.ai_action_running))
          }
        }
        resultText?.let { text ->
          Text(
            text,
            modifier = Modifier.testTag("ai_action_result"),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        error?.let {
          Text(
            it,
            modifier = Modifier.testTag("ai_action_error"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        status?.let {
          Text(
            it,
            modifier = Modifier.testTag("ai_action_status"),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      if (resultText != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Button(
            modifier = Modifier.testTag("ai_action_insert"),
            enabled = !running,
            onClick = {
              scope.launch {
                running = true
                error = null
                status = null
                try {
                  val action = resultAction ?: return@launch
                  val text = resultText ?: return@launch
                  val applied = insert(action, task, text, reviewId)
                  status = if (applied) appliedStatusMessage else keptOpenStatusMessage
                  if (applied) {
                    onDismiss()
                  }
                } catch (cancelled: CancellationException) {
                  throw cancelled
                } catch (failure: Exception) {
                  error = failure.message ?: failure.javaClass.simpleName
                } finally {
                  running = false
                }
              }
            },
          ) { Text(stringResource(R.string.ai_action_insert)) }
          TextButton(
            enabled = !running,
            onClick = onDismiss,
          ) { Text(stringResource(R.string.cancel)) }
        }
      } else {
        TextButton(
          enabled = !running,
          onClick = onDismiss,
        ) { Text(stringResource(R.string.cancel)) }
      }
    },
  )
}

@Composable
internal fun aiActionLabel(action: AiSelectionAction): String = stringResource(
  when (action) {
    AiSelectionAction.TONE_FORMAL -> R.string.ai_action_tone_formal
    AiSelectionAction.TONE_CASUAL -> R.string.ai_action_tone_casual
    AiSelectionAction.TONE_CONCISE -> R.string.ai_action_tone_concise
    AiSelectionAction.TRANSLATE_ZH -> R.string.ai_action_translate_zh
    AiSelectionAction.TRANSLATE_EN -> R.string.ai_action_translate_en
    AiSelectionAction.SUMMARY -> R.string.ai_action_summary
  },
)

/** 动作执行期间给 review 记录的 instruction 文案（不带 UI 装饰）。 */
internal fun aiActionInstruction(action: AiSelectionAction): String = when (action) {
  AiSelectionAction.TONE_FORMAL -> "AI 动作：语气改写（正式）"
  AiSelectionAction.TONE_CASUAL -> "AI 动作：语气改写（轻松）"
  AiSelectionAction.TONE_CONCISE -> "AI 动作：语气改写（简洁）"
  AiSelectionAction.TRANSLATE_ZH -> "AI 动作：翻译为中文"
  AiSelectionAction.TRANSLATE_EN -> "AI 动作：翻译为英文"
  AiSelectionAction.SUMMARY -> "AI 动作：总结"
}
