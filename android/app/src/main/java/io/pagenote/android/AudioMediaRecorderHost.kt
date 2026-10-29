package io.pagenote.android

import android.content.Context
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import io.pagenote.feature.audio.AudioRecordingPolicy
import java.io.IOException

/**
 * 录音 Android 封装（移植批次 8）：MediaRecorder → AAC/MPEG-4（`<uuid36>.m4a`）。
 * 输出直接写 SAF 文档描述符（由控制器经 [ProjectAudioStore.openRecordingDescriptor]
 * 类似路径以 "wt" 打开后传入）；maxFileSize/maxDuration 由配额策略折算后在
 * prepare 前注入。线程约束：全部方法在调用方串行执行（控制器已在 IO 调度器）。
 */
internal class AudioMediaRecorderHost(private val context: Context) {
  enum class StartResult { STARTED, FAILED }

  private var recorder: MediaRecorder? = null
  private var descriptor: ParcelFileDescriptor? = null

  /**
   * 准备并开始录音。失败时释放全部资源并返回 [StartResult.FAILED]
   * （调用方负责删除半成品文件）。[writeDescriptor] 以写模式打开的
   * 录音文档描述符，所有权转移到本类（停止/失败时关闭）。
   */
  fun start(writeDescriptor: ParcelFileDescriptor, maxDurationMs: Long, maxFileSizeBytes: Long): StartResult {
    if (recorder != null) {
      runCatching { writeDescriptor.close() }
      return StartResult.FAILED
    }
    return try {
      val mediaRecorder = MediaRecorder(context)
      recorder = mediaRecorder
      descriptor = writeDescriptor
      mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
      mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      mediaRecorder.setAudioSamplingRate(SAMPLE_RATE)
      mediaRecorder.setAudioChannels(CHANNELS)
      mediaRecorder.setAudioEncodingBitRate(BIT_RATE)
      if (maxDurationMs > 0) mediaRecorder.setMaxDuration(maxDurationMs.toInt())
      if (maxFileSizeBytes > 0) mediaRecorder.setMaxFileSize(maxFileSizeBytes)
      mediaRecorder.setOutputFile(writeDescriptor.fileDescriptor)
      mediaRecorder.prepare()
      mediaRecorder.start()
      StartResult.STARTED
    } catch (_: IOException) {
      release()
      StartResult.FAILED
    } catch (_: RuntimeException) {
      release()
      StartResult.FAILED
    } catch (_: SecurityException) {
      release()
      StartResult.FAILED
    }
  }

  /** 当前最大振幅（0..32767；未在录音返回 0）。 */
  fun maxAmplitude(): Int = try {
    recorder?.maxAmplitude ?: 0
  } catch (_: Exception) {
    0
  }

  /** 停止并释放。返回是否成功产出完整文件（开始后过短停止会抛异常）。 */
  fun stop(): Boolean {
    val mediaRecorder = recorder ?: return false
    return try {
      mediaRecorder.stop()
      true
    } catch (_: RuntimeException) {
      false
    } finally {
      release()
    }
  }

  /** 取消：丢弃文件由调用方处理；这里只保证录音器释放。 */
  fun cancel() {
    try {
      recorder?.stop()
    } catch (_: Exception) {
      // 未达最短停止窗口的取消属预期失败。
    } finally {
      release()
    }
  }

  private fun release() {
    try {
      recorder?.release()
    } catch (_: Exception) {
    }
    recorder = null
    try {
      descriptor?.close()
    } catch (_: Exception) {
    }
    descriptor = null
  }

  companion object {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BIT_RATE = 32_000

    /** UI 电平换算（策略归一化 0..1）。 */
    fun levelOf(rawAmplitude: Int): Float = AudioRecordingPolicy.amplitudeLevel(rawAmplitude)
  }
}
