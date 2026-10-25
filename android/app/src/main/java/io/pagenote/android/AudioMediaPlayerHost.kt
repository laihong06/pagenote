package io.pagenote.android

import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import io.pagenote.feature.audio.AudioPlaybackPolicy

/**
 * 回放 Android 封装（移植批次 8）：MediaPlayer 播放 `<project>/audio/` 下的
 * m4a 录音。seek 夹取与进度换算走 [AudioPlaybackPolicy]。线程约束：调用方
 * 串行（控制器在主线程持有状态、IO 动作调度后执行）。
 */
internal class AudioMediaPlayerHost {
  enum class PlayResult { STARTED, FAILED }

  private var player: MediaPlayer? = null
  private var descriptor: ParcelFileDescriptor? = null

  val durationMs: Long
    get() = player?.duration?.takeIf { it > 0 }?.toLong() ?: 0L

  val positionMs: Long
    get() = player?.currentPosition?.coerceAtLeast(0)?.toLong() ?: 0L

  val isPlaying: Boolean
    get() = try {
      player?.isPlaying ?: false
    } catch (_: Exception) {
      false
    }

  /** 从指定位置开始播放（文件描述符所有权转移到本类）。 */
  fun start(sourceDescriptor: ParcelFileDescriptor, startAtMs: Long): PlayResult {
    stop()
    return try {
      val mediaPlayer = MediaPlayer()
      player = mediaPlayer
      descriptor = sourceDescriptor
      mediaPlayer.setDataSource(sourceDescriptor.fileDescriptor)
      mediaPlayer.setOnCompletionListener {
        // 自然结束：复位到 IDLE（下一次 start 重建）。
        release()
      }
      mediaPlayer.setOnErrorListener { _, _, _ ->
        release()
        true
      }
      mediaPlayer.prepare()
      val target = AudioPlaybackPolicy.seekTarget(startAtMs, durationMs)
      if (target > 0) mediaPlayer.seekTo(target.toInt())
      mediaPlayer.start()
      PlayResult.STARTED
    } catch (_: Exception) {
      release()
      PlayResult.FAILED
    }
  }

  /** 暂停（保持进度；下一次 resume 从当前位置继续）。 */
  fun pause(): Boolean = try {
    player?.takeIf { it.isPlaying }?.pause()
    true
  } catch (_: Exception) {
    false
  }

  /** 暂停后恢复。 */
  fun resume(): Boolean = try {
    player?.takeIf { !it.isPlaying }?.start()
    true
  } catch (_: Exception) {
    false
  }

  /** 跳转到指定位置（毫秒；夹取到实际时长）。 */
  fun seekTo(targetMs: Long): Long {
    val mediaPlayer = player ?: return 0
    val clamped = AudioPlaybackPolicy.seekTarget(targetMs, durationMs)
    return try {
      mediaPlayer.seekTo(clamped.toInt())
      clamped
    } catch (_: Exception) {
      positionMs
    }
  }

  /** 停止并释放（幂等）。 */
  fun stop() {
    try {
      player?.let { if (it.isPlaying) it.stop() }
    } catch (_: Exception) {
    }
    release()
  }

  private fun release() {
    try {
      player?.release()
    } catch (_: Exception) {
    }
    player = null
    try {
      descriptor?.close()
    } catch (_: Exception) {
    }
    descriptor = null
  }
}
