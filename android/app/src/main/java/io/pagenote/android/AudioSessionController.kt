package io.pagenote.android

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.pagenote.feature.audio.AsrRuntimeBinding
import io.pagenote.feature.audio.AsrTranscriber
import io.pagenote.feature.audio.AudioCapsule
import io.pagenote.feature.audio.AudioCapsuleCodec
import io.pagenote.feature.audio.AudioCapsuleIndex
import io.pagenote.feature.audio.AudioCapsulePolicy
import io.pagenote.feature.audio.AudioPlaybackPolicy
import io.pagenote.feature.audio.AudioQuotaPolicy
import io.pagenote.feature.audio.AudioRecordingPolicy
import io.pagenote.feature.audio.MissingAsrRuntimeBinding
import io.pagenote.feature.audio.PcmAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频会话控制器（移植批次 8）：录音（MediaRecorder 封装）/回放（MediaPlayer
 * 封装）/转写（sherpa-onnx 绑定编排）/配额的状态中枢。所有音频宿主调用串行
 * 在单线程调度器上；Compose 状态线程安全，跨线程更新合法。
 *
 * 转写运行时绑定经 [AsrRuntimeBinding] 注入：缺省 [MissingAsrRuntimeBinding]
 * 报告"运行时未捆绑"，转写折叠为 FAILED 状态（录音/回放不受影响）。
 */
internal class AudioSessionController(
  context: Context,
  private val scope: CoroutineScope,
  private val settingsProvider: () -> ReaderSettings,
  private val statusReporter: (String) -> Unit,
  private val binding: AsrRuntimeBinding = MissingAsrRuntimeBinding,
) {
  private val appContext = context.applicationContext

  /** 音频宿主专用串行调度器（MediaRecorder/MediaPlayer/解码调用排队执行）。 */
  private val audioDispatcher = Dispatchers.IO.limitedParallelism(1)

  var index by mutableStateOf(AudioCapsuleIndex())
    private set

  /** 转写列表对话框可见性（菜单命令与画布点按共用开关）。 */
  var transcriptListVisible by mutableStateOf(false)

  var quotaUsageBytes by mutableLongStateOf(0L)
    private set

  // ---- 录音状态 ----
  var recordingPhase by mutableStateOf(AudioRecordingPolicy.Phase.IDLE)
    private set
  var recordingElapsedMs by mutableLongStateOf(0L)
    private set
  var recordingLevel by mutableFloatStateOf(0f)
    private set

  // ---- 回放状态 ----
  var playingCapsuleId by mutableStateOf<String?>(null)
    private set
  var playbackPhase by mutableStateOf(AudioPlaybackPolicy.Phase.IDLE)
    private set
  var playbackPositionMs by mutableLongStateOf(0L)
    private set
  var playbackDurationMs by mutableLongStateOf(0L)
    private set

  private var store: ProjectAudioStore? = null
  private var recorderHost: AudioMediaRecorderHost? = null
  private val playerHost = AudioMediaPlayerHost()
  private var recordingTicker: Job? = null
  private var playbackTicker: Job? = null
  private var recordingStartRealtime = 0L
  private var pendingRecording: PendingRecording? = null

  /**
   * 录音会话代号：start/stop/cancel 各自递增，PREPARING 期间的用户取消
   * （stop/cancel 先于宿主 start 完成到达）使在途 start 的产出失效。
   */
  private var recordingGeneration = 0L

  private data class PendingRecording(
    val asset: String,
    val recordingUri: android.net.Uri,
    val page: Int,
    val anchorX: Double,
    val anchorY: Double,
  )

  val isRecording: Boolean
    get() = recordingPhase == AudioRecordingPolicy.Phase.RECORDING

  /** 项目打开：装载 audio.json 与配额用量；切换项目时先终止录音/回放。 */
  fun attachProject(project: OpenedProject) {
    stopEverything()
    val audioStore = ProjectAudioStore(appContext.contentResolver, project.sourceDirectoryUri)
    store = audioStore
    scope.launch {
      try {
        val loaded = withContext(audioDispatcher) { audioStore.readCapsules() }
        val usage = withContext(audioDispatcher) { audioStore.usageBytes() }
        if (store === audioStore) {
          index = loaded
          quotaUsageBytes = usage
        }
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (store === audioStore) {
          index = AudioCapsuleIndex()
          quotaUsageBytes = 0L
          statusReporter(
            appContext.getString(R.string.audio_manifest_load_failed, error.message ?: ""),
          )
        }
      }
    }
  }

  /** 项目关闭/切换：取消录音（丢文件）、停回放、清状态。 */
  fun stopEverything() {
    stopRecordingInternal(keep = false, silent = true)
    stopPlayback()
    transcriptListVisible = false
    store = null
    index = AudioCapsuleIndex()
    quotaUsageBytes = 0L
  }

  /**
   * 开始录音（权限已被调用方确认）。落点信息（页号 + width-1000 锚点）
   * 由调用方从当前视口换算；停止时用于建胶囊。
   */
  fun startRecording(page: Int, anchorX: Double, anchorY: Double) {
    val audioStore = store ?: return
    if (!AudioRecordingPolicy.canStart(recordingPhase, permissionGranted = true, quotaAllowed = true)) return
    recordingGeneration += 1L
    val generation = recordingGeneration
    recordingPhase = AudioRecordingPolicy.Phase.PREPARING
    scope.launch {
      val result = withContext(audioDispatcher) {
        runCatching {
          val settings = settingsProvider()
          val usage = audioStore.usageBytes()
          quotaUsageBytes = usage
          if (!AudioQuotaPolicy.canStartRecording(settings.audioQuotaMb, usage)) {
            return@runCatching StartOutcome.QUOTA_EXCEEDED
          }
          val maxBytes = AudioQuotaPolicy.maxRecordingBytes(settings.audioQuotaMb, usage)
          val maxDuration = minOf(
            AudioCapsulePolicy.recorderMaxDurationMs(),
            AudioQuotaPolicy.maxRecordingDurationMs(settings.audioQuotaMb, usage),
          )
          val clampedAnchor = AudioCapsulePolicy.clampAnchor(anchorX, anchorY)
            ?: return@runCatching StartOutcome.ANCHOR_INVALID
          val recordingUri = audioStore.createRecordingFile()
          val asset = try {
            audioStore.assetNameOf(recordingUri)
          } catch (error: Exception) {
            runCatching { audioStore.deleteRecording(queryName(recordingUri) ?: "") }
            throw error
          }
          val descriptor = appContext.contentResolver.openFileDescriptor(recordingUri, "rw")
            ?: return@runCatching StartOutcome.PROVIDER_FAILED
          // 每次录音独立的宿主实例：会话代号失效时本地停止，不影响后续会话。
          val host = AudioMediaRecorderHost(appContext)
          when (host.start(descriptor, maxDuration, maxBytes)) {
            AudioMediaRecorderHost.StartResult.STARTED ->
              StartOutcome.Started(asset, recordingUri, clampedAnchor, host)
            AudioMediaRecorderHost.StartResult.FAILED -> StartOutcome.PROVIDER_FAILED
          }
        }.getOrElse { StartOutcome.PROVIDER_FAILED }
      }
      if (generation != recordingGeneration) {
        // 会话已被 stop/cancel/新 start 取代：立即停止并丢弃半成品文件。
        if (result is StartOutcome.Started) {
          scope.launch(audioDispatcher) {
            result.host.stop()
            runCatching { appContext.contentResolver.delete(result.uri, null, null) }
          }
        }
        return@launch
      }
      when (result) {
        is StartOutcome.Started -> {
          recorderHost = result.host
          pendingRecording = PendingRecording(result.asset, result.uri, page, result.anchor.first, result.anchor.second)
          recordingStartRealtime = SystemClock.elapsedRealtime()
          recordingElapsedMs = 0L
          recordingLevel = 0f
          recordingPhase = AudioRecordingPolicy.Phase.RECORDING
          recordingTicker?.cancel()
          recordingTicker = scope.launch(audioDispatcher) {
            while (recordingPhase == AudioRecordingPolicy.Phase.RECORDING) {
              recordingElapsedMs = SystemClock.elapsedRealtime() - recordingStartRealtime
              recordingLevel = AudioMediaRecorderHost.levelOf(recorderHost?.maxAmplitude() ?: 0)
              delay(AudioRecordingPolicy.TICK_MS)
            }
          }
        }
        StartOutcome.QUOTA_EXCEEDED -> {
          recordingPhase = AudioRecordingPolicy.Phase.IDLE
          statusReporter(appContext.getString(R.string.audio_record_quota_exceeded))
        }
        StartOutcome.ANCHOR_INVALID, StartOutcome.PROVIDER_FAILED -> {
          recordingPhase = AudioRecordingPolicy.Phase.IDLE
          statusReporter(appContext.getString(R.string.audio_record_start_failed))
        }
      }
    }
  }

  private sealed interface StartOutcome {
    data class Started(
      val asset: String,
      val uri: android.net.Uri,
      val anchor: Pair<Double, Double>,
      val host: AudioMediaRecorderHost,
    ) : StartOutcome
    data object QUOTA_EXCEEDED : StartOutcome
    data object ANCHOR_INVALID : StartOutcome
    data object PROVIDER_FAILED : StartOutcome
  }

  /**
   * 停止录音并落胶囊。低于最短时长的录音丢弃文件（不落胶囊）；
   * 自动转写开启时排队转写任务。
   */
  fun stopRecording() {
    stopRecordingInternal(keep = true, silent = false)
  }

  /** 取消录音：文件删除、不落胶囊。 */
  fun cancelRecording() {
    stopRecordingInternal(keep = false, silent = true)
  }

  private fun stopRecordingInternal(keep: Boolean, silent: Boolean) {
    val pending = pendingRecording
    recordingGeneration += 1L
    if (recordingPhase == AudioRecordingPolicy.Phase.IDLE) {
      if (pending != null) {
        // PREPARING 阶段的取消：文件尚未产出，清理登记即可。
        pendingRecording = null
        scope.launch(audioDispatcher) {
          runCatching { appContext.contentResolver.delete(pending.recordingUri, null, null) }
        }
      }
      return
    }
    recordingPhase = AudioRecordingPolicy.Phase.IDLE
    recordingTicker?.cancel()
    recordingTicker = null
    val elapsed = recordingElapsedMs
    recordingLevel = 0f
    recorderHost?.let { host ->
      scope.launch(audioDispatcher) {
        val stopped = host.stop()
        recorderHost = null
        val audioStore = store
        if (audioStore != null && pending != null) {
          val durationMs = elapsed
          val fileDeleted = if (!stopped || !keep || !AudioCapsulePolicy.shouldKeepRecording(durationMs)) {
            runCatching { audioStore.deleteRecording(pending.asset) }.getOrDefault(false)
            true
          } else {
            false
          }
          if (silent) return@launch
          when {
            !stopped -> statusReporter(appContext.getString(R.string.audio_record_too_short))
            fileDeleted && !AudioCapsulePolicy.shouldKeepRecording(durationMs) ->
              statusReporter(appContext.getString(R.string.audio_record_discarded))
            else -> try {
              commitCapsule(audioStore, pending, durationMs)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
              throw cancelled
            } catch (error: Exception) {
              statusReporter(
                appContext.getString(R.string.audio_capsule_save_failed, error.message ?: ""),
              )
            }
          }
        }
      }
    }
    pendingRecording = null
  }

  private suspend fun commitCapsule(audioStore: ProjectAudioStore, pending: PendingRecording, durationMs: Long) {
    if (!AudioCapsulePolicy.canPlaceOnPage(index.forPage(pending.page).size) ||
      !AudioCapsulePolicy.canPlaceTotal(index.size)
    ) {
      runCatching { audioStore.deleteRecording(pending.asset) }
      statusReporter(appContext.getString(R.string.audio_capsule_limit_reached))
      return
    }
    val capsule = AudioCapsuleCodec.newCapsule(
      page = pending.page,
      anchorX = pending.anchorX,
      anchorY = pending.anchorY,
      asset = pending.asset,
      durationMs = durationMs,
      createdAtMillis = System.currentTimeMillis(),
    )
    val withStatus = if (settingsProvider().asrAutoTranscribe) {
      capsule.copy(transcriptStatus = io.pagenote.feature.audio.AudioTranscriptStatus.PENDING)
    } else {
      capsule
    }
    index = index.upsert(withStatus)
    persistManifest(audioStore)
    quotaUsageBytes = withContext(audioDispatcher) { audioStore.usageBytes() }
    statusReporter(appContext.getString(R.string.audio_record_saved))
    if (settingsProvider().asrAutoTranscribe) {
      transcribe(withStatus.id)
    }
  }

  /** 转写（自动排队或列表手动重试共用）；运行中（PENDING）跳过重复排队。 */
  fun transcribe(capsuleId: String) {
    val audioStore = store ?: return
    val capsule = index.byId(capsuleId) ?: return
    if (capsule.transcriptStatus == io.pagenote.feature.audio.AudioTranscriptStatus.PENDING) return
    val queued = capsule.copy(transcriptStatus = io.pagenote.feature.audio.AudioTranscriptStatus.PENDING)
    index = index.upsert(queued)
    scope.launch {
      val result = withContext(audioDispatcher) {
        runCatching {
          val settings = settingsProvider()
          val descriptor = audioStore.openRecordingDescriptor(capsule.asset)
            ?: throw IllegalStateException("recording asset missing")
          descriptor.use { pfd ->
            val pcm: PcmAudio = AudioPcmDecoder.decodeTo16kMono(pfd)
            val transcriber = AsrTranscriber(
              binding = binding,
              modelRoot = java.io.File(appContext.filesDir, "models/asr"),
              settingModelId = settings.asrModel,
              settingLanguage = settings.asrLanguage,
              systemLanguageTag = java.util.Locale.getDefault().toLanguageTag(),
            )
            transcriber.transcribe(pcm, durationMs = capsule.durationMs)
          }
        }.getOrElse { error ->
          AsrTranscriber.AsrTranscriptResult(
            status = io.pagenote.feature.audio.AudioTranscriptStatus.FAILED,
            text = null,
            segments = emptyList(),
            language = "",
            modelId = settingsProvider().asrModel,
            warnings = listOf(error.message ?: error.javaClass.simpleName),
          )
        }
      }
      val audioStore2 = store ?: return@launch
      val current = index.byId(capsuleId) ?: return@launch
      val updated = when (result.status) {
        io.pagenote.feature.audio.AudioTranscriptStatus.OK -> AudioCapsulePolicy.withTranscript(
          current,
          io.pagenote.feature.audio.AudioTranscriptStatus.OK,
          result.text.orEmpty(),
          result.segments,
          result.language,
          result.modelId,
        )
        else -> AudioCapsulePolicy.withTranscript(
          current,
          io.pagenote.feature.audio.AudioTranscriptStatus.FAILED,
          null,
          emptyList(),
          null,
          null,
        )
      }
      if (updated != null) {
        index = index.upsert(updated)
        try {
          persistManifest(audioStore2)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          statusReporter(
            appContext.getString(R.string.audio_capsule_save_failed, error.message ?: ""),
          )
        }
        if (result.status == io.pagenote.feature.audio.AudioTranscriptStatus.OK) {
          statusReporter(appContext.getString(R.string.audio_transcribe_done))
        } else {
          statusReporter(appContext.getString(R.string.audio_transcribe_failed))
        }
      } else {
        // 产物不合法（脏数据防御）：标记失败，不写清单。
        index = index.upsert(
          current.copy(transcriptStatus = io.pagenote.feature.audio.AudioTranscriptStatus.FAILED),
        )
        statusReporter(appContext.getString(R.string.audio_transcribe_failed))
      }
    }
  }

  /** 播放/暂停切换；同一胶囊暂停后恢复继续，换胶囊从头播。 */
  fun togglePlay(capsule: AudioCapsule) {
    when {
      playingCapsuleId == capsule.id && playbackPhase == AudioPlaybackPolicy.Phase.PLAYING -> {
        scope.launch(audioDispatcher) { playerHost.pause() }
        playbackPhase = AudioPlaybackPolicy.Phase.PAUSED
        playbackTicker?.cancel()
      }
      playingCapsuleId == capsule.id && playbackPhase == AudioPlaybackPolicy.Phase.PAUSED -> {
        scope.launch(audioDispatcher) { playerHost.resume() }
        playbackPhase = AudioPlaybackPolicy.Phase.PLAYING
        startPlaybackTicker()
      }
      else -> startPlayback(capsule, startAtMs = 0L)
    }
  }

  /** 回听跳转：切/播到指定位置（片段点击、进度条 seek 共用）。 */
  fun seekPlayback(capsule: AudioCapsule, targetMs: Long) {
    if (playingCapsuleId != capsule.id) {
      startPlayback(capsule, startAtMs = targetMs)
      return
    }
    val clamped = AudioPlaybackPolicy.seekTarget(targetMs, playbackDurationMs.takeIf { it > 0 })
    scope.launch(audioDispatcher) {
      playerHost.seekTo(clamped)
      playbackPositionMs = clamped
    }
    if (playbackPhase == AudioPlaybackPolicy.Phase.IDLE) {
      playbackPhase = AudioPlaybackPolicy.Phase.PLAYING
      startPlaybackTicker()
    }
  }

  fun pauseOrResumePlayback() {
    val id = playingCapsuleId ?: return
    when (playbackPhase) {
      AudioPlaybackPolicy.Phase.PLAYING -> togglePlay(index.byId(id) ?: return)
      AudioPlaybackPolicy.Phase.PAUSED -> togglePlay(index.byId(id) ?: return)
      AudioPlaybackPolicy.Phase.IDLE -> Unit
    }
  }

  fun stopPlayback() {
    scope.launch(audioDispatcher) { playerHost.stop() }
    playingCapsuleId = null
    playbackPhase = AudioPlaybackPolicy.Phase.IDLE
    playbackPositionMs = 0L
    playbackDurationMs = 0L
    playbackTicker?.cancel()
    playbackTicker = null
  }

  private fun startPlayback(capsule: AudioCapsule, startAtMs: Long) {
    val audioStore = store ?: return
    playingCapsuleId = capsule.id
    playbackPhase = AudioPlaybackPolicy.Phase.PAUSED
    playbackPositionMs = 0L
    playbackDurationMs = capsule.durationMs
    scope.launch(audioDispatcher) {
      val descriptor = audioStore.openRecordingDescriptor(capsule.asset)
      if (descriptor == null) {
        playingCapsuleId = null
        playbackPhase = AudioPlaybackPolicy.Phase.IDLE
        statusReporter(appContext.getString(R.string.audio_playback_asset_missing))
        return@launch
      }
      descriptor.let { pfd ->
        when (playerHost.start(pfd, startAtMs)) {
          AudioMediaPlayerHost.PlayResult.STARTED -> {
            playbackPhase = AudioPlaybackPolicy.Phase.PLAYING
            playbackDurationMs = playerHost.durationMs.takeIf { it > 0 } ?: capsule.durationMs
            startPlaybackTicker()
          }
          AudioMediaPlayerHost.PlayResult.FAILED -> {
            playingCapsuleId = null
            playbackPhase = AudioPlaybackPolicy.Phase.IDLE
            statusReporter(appContext.getString(R.string.audio_playback_failed))
          }
        }
      }
    }
  }

  private fun startPlaybackTicker() {
    playbackTicker?.cancel()
    playbackTicker = scope.launch(audioDispatcher) {
      while (playbackPhase == AudioPlaybackPolicy.Phase.PLAYING) {
        playbackPositionMs = playerHost.positionMs
        if (!playerHost.isPlaying && playbackPhase == AudioPlaybackPolicy.Phase.PLAYING) {
          // 自然结束（completion 回调已释放）：复位状态。
          playingCapsuleId = null
          playbackPhase = AudioPlaybackPolicy.Phase.IDLE
          playbackPositionMs = 0L
          playbackTicker = null
          return@launch
        }
        delay(AudioPlaybackPolicy.TICK_MS)
      }
    }
  }

  /** 删除胶囊：清单移除 + 录音文件删除（配额回收）。 */
  fun deleteCapsule(capsule: AudioCapsule) {
    val audioStore = store ?: return
    if (playingCapsuleId == capsule.id) stopPlayback()
    scope.launch {
      val removed = withContext(audioDispatcher) {
        runCatching { audioStore.deleteRecording(capsule.asset) }.getOrDefault(false)
      }
      index = index.remove(capsule.id)
      try {
        persistManifest(audioStore)
        quotaUsageBytes = withContext(audioDispatcher) { audioStore.usageBytes() }
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        statusReporter(
          appContext.getString(R.string.audio_capsule_save_failed, error.message ?: ""),
        )
      }
      if (!removed) {
        statusReporter(appContext.getString(R.string.audio_delete_asset_missing))
      }
    }
  }

  /** 手动刷新配额用量（设置对话框打开时）。 */
  fun refreshQuotaUsage() {
    val audioStore = store ?: return
    scope.launch {
      quotaUsageBytes = withContext(audioDispatcher) {
        runCatching { audioStore.usageBytes() }.getOrDefault(quotaUsageBytes)
      }
    }
  }

  private suspend fun persistManifest(audioStore: ProjectAudioStore) {
    withContext(audioDispatcher) {
      audioStore.writeCapsules(index)
    }
  }

  private fun queryName(uri: android.net.Uri): String? =
    uri.lastPathSegment?.substringAfterLast('/')
}
