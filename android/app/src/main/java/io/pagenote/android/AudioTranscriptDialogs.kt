package io.pagenote.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.pagenote.feature.audio.AudioCapsule
import io.pagenote.feature.audio.AudioPlaybackPolicy
import io.pagenote.feature.audio.AudioTranscriptFormatter
import io.pagenote.feature.audio.AudioTranscriptStatus

/**
 * 转写列表 + 回听跳转（移植批次 8）：全部胶囊按页号/时间排序，行内展示
 * 状态、时长与预览；点行播放/暂停，点转写片段回听跳转到该句起点，
 * 工具按钮跳页（阅读器）与重试转写。
 */
@Composable
internal fun AudioTranscriptListDialog(
  capsules: List<AudioCapsule>,
  playingCapsuleId: String?,
  playbackPhase: AudioPlaybackPolicy.Phase,
  playbackPositionMs: Long,
  playbackDurationMs: Long,
  onPlayCapsule: (AudioCapsule) -> Unit,
  onSeekSegment: (AudioCapsule, Long) -> Unit,
  onSeekPosition: (AudioCapsule, Long) -> Unit,
  onJumpToPage: (AudioCapsule) -> Unit,
  onRetranscribe: (AudioCapsule) -> Unit,
  onDelete: (AudioCapsule) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.audio_transcript_list_title)) },
    text = {
      Column {
        if (capsules.isEmpty()) {
          Text(
            stringResource(R.string.audio_transcript_list_empty),
            modifier = Modifier.testTag("audio_transcript_list_empty"),
            style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          LazyColumn(
            modifier = Modifier.heightIn(max = 460.dp).testTag("audio_transcript_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(capsules, key = { it.id }) { capsule ->
              AudioCapsuleListRow(
                capsule = capsule,
                playing = capsule.id == playingCapsuleId,
                playbackPhase = if (capsule.id == playingCapsuleId) playbackPhase else AudioPlaybackPolicy.Phase.IDLE,
                playbackPositionMs = if (capsule.id == playingCapsuleId) playbackPositionMs else 0L,
                playbackDurationMs = if (capsule.id == playingCapsuleId) playbackDurationMs else capsule.durationMs,
                onTogglePlay = { onPlayCapsule(capsule) },
                onSeekSegment = { targetMs -> onSeekSegment(capsule, targetMs) },
                onSeekPosition = { targetMs -> onSeekPosition(capsule, targetMs) },
                onJumpToPage = { onJumpToPage(capsule) },
                onRetranscribe = { onRetranscribe(capsule) },
                onDelete = { onDelete(capsule) },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("audio_transcript_list_close"),
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun AudioCapsuleListRow(
  capsule: AudioCapsule,
  playing: Boolean,
  playbackPhase: AudioPlaybackPolicy.Phase,
  playbackPositionMs: Long,
  playbackDurationMs: Long,
  onTogglePlay: () -> Unit,
  onSeekSegment: (Long) -> Unit,
  onSeekPosition: (Long) -> Unit,
  onJumpToPage: () -> Unit,
  onRetranscribe: () -> Unit,
  onDelete: () -> Unit,
) {
  var sliderValue by remember(capsule.id, playing) { mutableFloatStateOf(playbackPositionMs.toFloat()) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp)
      .testTag("audio_capsule_row_${capsule.id}"),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "${stringResource(R.string.audio_capsule_page_prefix, capsule.page)} · " +
          AudioPlaybackPolicy.clockLabel(capsule.durationMs),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleSmall,
      )
      TranscriptStatusChip(capsule.transcriptStatus)
    }
    val meta = AudioTranscriptFormatter.metaLabel(capsule.language, capsule.model)
    if (meta != null) {
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    when (capsule.transcriptStatus) {
      AudioTranscriptStatus.PENDING -> {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
      }
      AudioTranscriptStatus.OK -> {
        Text(
          text = AudioTranscriptFormatter.preview(capsule.transcript.orEmpty()),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(vertical = 2.dp),
        )
      }
      AudioTranscriptStatus.FAILED -> {
        Text(
          text = stringResource(R.string.audio_transcript_failed_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(vertical = 2.dp),
        )
      }
      AudioTranscriptStatus.NONE -> Unit
    }
    // 转写片段（回听跳转）：点句跳到该句起点并继续播放。
    if (capsule.segments.isNotEmpty()) {
      Column(modifier = Modifier.padding(top = 2.dp)) {
        capsule.segments.take(AudioTranscriptListPolicy.MAX_SEGMENT_ROWS).forEach { segment ->
          Text(
            text = "[${AudioPlaybackPolicy.clockLabel(segment.startMs)}] " +
              AudioTranscriptFormatter.preview(
                segment.text,
                AudioTranscriptFormatter.PREVIEW_MAX_CODE_POINTS,
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSeekSegment(AudioPlaybackPolicy.seekTargetForSegment(segment, capsule.durationMs)) }
              .padding(vertical = 2.dp)
              .testTag("audio_segment_${segment.startMs}"),
          )
        }
        if (capsule.segments.size > AudioTranscriptListPolicy.MAX_SEGMENT_ROWS) {
          Text(
            text = stringResource(
              R.string.audio_segment_more,
              capsule.segments.size - AudioTranscriptListPolicy.MAX_SEGMENT_ROWS,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    if (playing) {
      Slider(
        value = sliderValue.coerceIn(0f, playbackDurationMs.coerceAtLeast(1L).toFloat()),
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onSeekPosition(sliderValue.toLong()) },
        valueRange = 0f..playbackDurationMs.coerceAtLeast(1L).toFloat(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      )
      Text(
        text = "${AudioPlaybackPolicy.clockLabel(playbackPositionMs)} / " +
          AudioPlaybackPolicy.clockLabel(playbackDurationMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row {
      TextButton(onClick = onTogglePlay, modifier = Modifier.testTag("audio_capsule_play")) {
        Text(
          stringResource(
            when {
              playing && playbackPhase == AudioPlaybackPolicy.Phase.PLAYING -> R.string.audio_pause
              else -> R.string.audio_play
            },
          ),
        )
      }
      TextButton(onClick = onJumpToPage) { Text(stringResource(R.string.audio_jump_to_page)) }
      if (capsule.transcriptStatus in setOf(AudioTranscriptStatus.FAILED, AudioTranscriptStatus.NONE)) {
        TextButton(onClick = onRetranscribe, modifier = Modifier.testTag("audio_capsule_retranscribe")) {
          Text(stringResource(R.string.audio_transcribe_retry))
        }
      }
      TextButton(onClick = onDelete, modifier = Modifier.testTag("audio_capsule_delete")) {
        Text(
          stringResource(R.string.audio_delete),
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun TranscriptStatusChip(status: AudioTranscriptStatus) {
  val label = when (status) {
    AudioTranscriptStatus.NONE -> stringResource(R.string.audio_status_none)
    AudioTranscriptStatus.PENDING -> stringResource(R.string.audio_status_pending)
    AudioTranscriptStatus.OK -> stringResource(R.string.audio_status_ok)
    AudioTranscriptStatus.FAILED -> stringResource(R.string.audio_status_failed)
  }
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    color = when (status) {
      AudioTranscriptStatus.OK -> MaterialTheme.colorScheme.primary
      AudioTranscriptStatus.FAILED -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    },
  )
}

/** 转写列表展示策略：片段行数上限（完整内容经片段跳转/播放消费）。 */
internal object AudioTranscriptListPolicy {
  const val MAX_SEGMENT_ROWS = 6
}
