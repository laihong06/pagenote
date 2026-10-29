package io.pagenote.feature.audio

import java.io.File

/**
 * 离线 ASR 引擎契约（移植批次 8）。接口形状镜像 sherpa-onnx 的 Kotlin API
 * 子集（package `com.k2fsa.sherpa.onnx`：OnlineRecognizer / OfflineRecognizer /
 * Vad），使编排逻辑（[AsrTranscriber]/[VadSegmenter]）与真实运行时解耦：
 *
 * - 单测用注入式 fake 覆盖流式/VAD/短音频回退路由；
 * - 真实 sherpa-onnx 绑定按 [AsrRuntimeBinding] 实现约 60 行粘合代码
 *   （createOnlineRecognizer/createOfflineRecognizer/createVad + 模型文件
 *   配置映射），随运行时落地批次接入；本批次缺省绑定是
 *   [MissingAsrRuntimeBinding]，报告"运行时未捆绑"的可恢复状态。
 *
 * 所有组件一次性装载、close 幂等；调用方串行使用（转写已在 IO 调度器上）。
 */

/** 一段语音（VAD 输出）：样本从录音开头的绝对采样点记起。 */
class SpeechSegment(
  val startSample: Long,
  val samples: FloatArray,
)

/** 毫秒时间轴上的语音段。 */
data class SpeechSpan(val startMs: Long, val endMs: Long)

/** 流式识别会话（sherpa OnlineRecognizer.Stream 的镜像）。 */
interface StreamingAsrSession {
  /** 特征窗已够一帧（不足时 decode 为空转）。 */
  val isReady: Boolean

  /** 送入一段 16kHz 单声道 float 样本。 */
  fun acceptWaveform(samples: FloatArray)

  /** 端点检测命中（调用方据此 reset 并收割结果）。 */
  val isEndpoint: Boolean

  /** 对当前缓冲解码一次（增量）。 */
  fun decode()

  /** 端点收割后复位会话（保留跨段上下文的按实现决定）。 */
  fun reset()

  /** 输入结束（尾段冲刷）。 */
  fun inputFinished()

  /** 当前增量识别文本。 */
  val currentText: String

  fun close()
}

/** 流式识别引擎工厂（sherpa OnlineRecognizer 的镜像）。 */
interface StreamingAsrEngine {
  fun createSession(): StreamingAsrSession
  fun close()
}

/** 短音频一次性识别（sherpa OfflineRecognizer 的镜像）：整段送入、带时间戳输出。 */
interface ShortClipRecognizer {
  fun recognize(samples: FloatArray): ShortClipResult
  fun close()
}

data class ShortClipResult(
  val text: String,
  /** 词级时间戳（毫秒，相对本段起点）；运行时不提供时为空表。 */
  val tokenTimestampsMs: List<Long>,
)

/** 语音活动检测（sherpa Vad 的镜像）：内部累积语音段，队列语义收割。 */
interface VoiceActivityDetector {
  fun acceptWaveform(samples: FloatArray)

  /** 当前窗口是否仍在语音中（未闭合的累积段存在）。 */
  val isSpeechDetected: Boolean

  /** 收割队列是否为空。 */
  val empty: Boolean

  /** 队头语音段（空队列返回 null）。 */
  fun front(): SpeechSegment?

  fun pop()

  /** 闭合并收割当前累积中的语音段到队列（流末尾调用）。 */
  fun flush()

  fun clear()
  fun close()
}

/**
 * sherpa-onnx 运行时绑定：引擎创建入口。真实实现持有模型文件路径映射
 * （[AsrModelEntry] 组件契约 → sherpa 配置）与 JNI 装载；`create*` 在模型
 * 文件缺失或 native 库不可用时抛 [AsrRuntimeException]。
 */
interface AsrRuntimeBinding {
  /** 运行时与所选模型的可用性（文件存在性 + native 库）。 */
  fun checkAvailable(model: AsrModelEntry, modelRoot: File): BindingAvailability

  fun createStreamingEngine(model: AsrModelEntry, modelRoot: File): StreamingAsrEngine

  fun createShortClipRecognizer(model: AsrModelEntry, modelRoot: File): ShortClipRecognizer

  fun createVoiceActivityDetector(model: AsrModelEntry, modelRoot: File): VoiceActivityDetector
}

data class BindingAvailability(
  val available: Boolean,
  /** 不可用原因（用户可读、可恢复提示；available 时为 null）。 */
  val reason: String? = null,
)

class AsrRuntimeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 缺省绑定：运行时未落地。所有创建入口都报告不可用。 */
object MissingAsrRuntimeBinding : AsrRuntimeBinding {
  const val REASON = "sherpa-onnx runtime is not bundled in this build"

  override fun checkAvailable(model: AsrModelEntry, modelRoot: File): BindingAvailability =
    BindingAvailability(available = false, reason = REASON)

  override fun createStreamingEngine(model: AsrModelEntry, modelRoot: File): StreamingAsrEngine =
    throw AsrRuntimeException(REASON)

  override fun createShortClipRecognizer(model: AsrModelEntry, modelRoot: File): ShortClipRecognizer =
    throw AsrRuntimeException(REASON)

  override fun createVoiceActivityDetector(model: AsrModelEntry, modelRoot: File): VoiceActivityDetector =
    throw AsrRuntimeException(REASON)
}
