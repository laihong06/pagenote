package io.pagenote.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import io.pagenote.feature.audio.AudioRecordingPolicy

/**
 * 音频会话的组合接线（移植批次 8）：把录音/转写的组合样板（控制器记忆、
 * 权限 launcher、对话框宿主）从 ReaderHome 抽出，控制其方法体积——ReaderHome
 * 已逼近 JVM 64KB 方法上限（批次 7 曾为同一原因抽出 ReaderSearchController）。
 */

/** ReaderHome 持有的音频控制器（remember 封装；settings 经闭包取最新值）。 */
@Composable
internal fun rememberReaderAudioController(
  context: Context,
  session: ReaderSessionViewModel,
  settings: () -> ReaderSettings,
): AudioSessionController {
  val scope = rememberCoroutineScope()
  val latestSettings by rememberUpdatedState(settings())
  return remember {
    AudioSessionController(
      context = context,
      scope = scope,
      settingsProvider = { latestSettings },
      statusReporter = { status -> session.noteStatus.value = status },
    )
  }
}

internal class AudioRecordStartRequest(
  val page: Int,
  val anchorX: Double,
  val anchorY: Double,
)

/**
 * 录音权限 launcher：返回 `launch(page, anchorX, anchorY)`——已授权直接开始，
 * 否则请求权限并在结果回调里继续（拒绝/失败通过回调上报状态文案）。
 */
@Composable
internal fun rememberAudioRecordLauncher(
  context: Context,
  controller: AudioSessionController,
  onDenied: () -> Unit,
  onStartFailed: () -> Unit,
): (Int, Double, Double) -> Unit {
  var pending by remember { mutableStateOf<AudioRecordStartRequest?>(null) }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    val request = pending
    pending = null
    when {
      request == null -> Unit
      granted -> controller.startRecording(request.page, request.anchorX, request.anchorY)
      else -> onDenied()
    }
  }
  return { page, anchorX, anchorY ->
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    if (granted) {
      controller.startRecording(page, anchorX, anchorY)
    } else {
      pending = AudioRecordStartRequest(page, anchorX, anchorY)
      try {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      } catch (_: Exception) {
        pending = null
        onStartFailed()
      }
    }
  }
}

/**
 * 音频会话对话框宿主：录音模态 + 转写列表。可见性状态在控制器上
 * （[AudioSessionController.transcriptListVisible]），菜单命令与画布点按
 * 共用同一个开关。
 */
@Composable
internal fun AudioSessionDialogs(
  controller: AudioSessionController,
  hasProject: Boolean,
  onJumpToPage: (Int) -> Unit,
) {
  if (controller.recordingPhase != AudioRecordingPolicy.Phase.IDLE) {
    AudioRecordingDialog(
      preparing = controller.recordingPhase == AudioRecordingPolicy.Phase.PREPARING,
      elapsedMs = controller.recordingElapsedMs,
      level = controller.recordingLevel,
      onStop = controller::stopRecording,
      onCancel = controller::cancelRecording,
    )
  }
  if (controller.transcriptListVisible && hasProject) {
    AudioTranscriptListDialog(
      capsules = controller.index.orderedForList(),
      playingCapsuleId = controller.playingCapsuleId,
      playbackPhase = controller.playbackPhase,
      playbackPositionMs = controller.playbackPositionMs,
      playbackDurationMs = controller.playbackDurationMs,
      onPlayCapsule = controller::togglePlay,
      onSeekSegment = controller::seekPlayback,
      onSeekPosition = { capsule, target -> controller.seekPlayback(capsule, target) },
      onJumpToPage = { capsule -> onJumpToPage(capsule.page - 1) },
      onRetranscribe = { capsule -> controller.transcribe(capsule.id) },
      onDelete = controller::deleteCapsule,
      onDismiss = { controller.transcriptListVisible = false },
    )
  }
}
