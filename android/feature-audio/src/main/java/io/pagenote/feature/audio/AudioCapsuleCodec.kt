package io.pagenote.feature.audio

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 音频胶囊清单编解码（移植批次 8）。文件 `<project>/audio.json`，格式
 * （version 1，对齐 materials.json 的读侧容错/写侧严格姿态）：
 * ```json
 * {"version":1,"capsules":[
 *   {"id":"<uuid36>","page":1,"anchorX":440.0,"anchorY":300.0,
 *    "asset":"<uuid36>.m4a","durationMs":52300,"createdAt":1730000000000,
 *    "transcriptStatus":"ok","transcript":"……",
 *    "segments":[{"startMs":0,"endMs":3200,"text":"……"}],
 *    "language":"zh","model":"streaming-zipformer-zh-en"}]}
 * ```
 * 单条胶囊字段非法（坏 id/资产名/页号/状态/片段）时跳过该条而非整体失败；
 * 未知 version 抛 [IllegalArgumentException]（调用方决定是否放弃整表）。
 * 重复 id 保留首条。
 */
object AudioCapsuleCodec {
  const val VERSION = 1
  const val FILE_NAME = "audio.json"
  const val MAX_CAPSULES = 500
  const val MAX_SEGMENTS_PER_CAPSULE = 2000
  const val MAX_SEGMENT_TEXT_CHARS = 2000
  const val MAX_TRANSCRIPT_CHARS = 20_000

  private val json = Json { ignoreUnknownKeys = true }

  fun encode(capsules: List<AudioCapsule>): String {
    validate(capsules)
    val root = buildJsonObject {
      put("version", VERSION)
      put(
        "capsules",
        buildJsonArray {
          capsules.forEach { capsule ->
            add(
              buildJsonObject {
                put("id", capsule.id)
                put("page", capsule.page)
                put("anchorX", capsule.anchorX)
                put("anchorY", capsule.anchorY)
                put("asset", capsule.asset)
                put("durationMs", capsule.durationMs)
                put("createdAt", capsule.createdAtMillis)
                put("transcriptStatus", capsule.transcriptStatus.key)
                capsule.transcript?.let { put("transcript", it) }
                if (capsule.segments.isNotEmpty()) {
                  put(
                    "segments",
                    buildJsonArray {
                      capsule.segments.forEach { segment ->
                        add(
                          buildJsonObject {
                            put("startMs", segment.startMs)
                            put("endMs", segment.endMs)
                            put("text", segment.text)
                          },
                        )
                      }
                    },
                  )
                }
                capsule.language?.let { put("language", it) }
                capsule.model?.let { put("model", it) }
              },
            )
          }
        },
      )
    }
    return Json.encodeToString(JsonObject.serializer(), root)
  }

  fun decode(text: String): List<AudioCapsule> {
    val root = json.parseToJsonElement(text).jsonObject
    val version = root["version"]?.jsonPrimitive?.longOrNull ?: VERSION.toLong()
    if (version != VERSION.toLong()) {
      throw IllegalArgumentException("unsupported audio manifest version: $version")
    }
    val capsules = root["capsules"]?.jsonArray ?: return emptyList()
    val seen = mutableSetOf<String>()
    val entries = mutableListOf<AudioCapsule>()
    for (element in capsules) {
      val entry = element as? JsonObject ?: continue
      val id = entry.stringOrNull("id")?.takeIf { AudioCapsulePolicy.ID_PATTERN.matches(it) } ?: continue
      val page = entry["page"]?.jsonPrimitive?.longOrNull?.takeIf { it in 1..Int.MAX_VALUE }
        ?.toInt() ?: continue
      val anchorX = entry["anchorX"]?.jsonPrimitive?.doubleOrNull
        ?.takeIf { it.isFinite() } ?: continue
      val anchorY = entry["anchorY"]?.jsonPrimitive?.doubleOrNull
        ?.takeIf { it.isFinite() } ?: continue
      val asset = entry.stringOrNull("asset")?.takeIf(AudioCapsulePolicy::isValidAssetName) ?: continue
      val durationMs = entry["durationMs"]?.jsonPrimitive?.longOrNull
        ?.takeIf { it > 0 } ?: continue
      val createdAt = entry["createdAt"]?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 } ?: continue
      val status = AudioTranscriptStatus.fromKey(entry.stringOrNull("transcriptStatus"))
        ?: AudioTranscriptStatus.NONE
      val transcript = entry.stringOrNull("transcript")?.takeIf(String::isNotBlank)
      val segments = decodeSegments(entry["segments"] as? JsonArray)
        ?: continue
      if (status == AudioTranscriptStatus.OK && segments.isEmpty()) continue
      val language = entry.stringOrNull("language")?.takeIf(String::isNotBlank)
      val model = entry.stringOrNull("model")?.takeIf(String::isNotBlank)
      // 去重只针对成功解码的条目：非法条目不占用 id（同 id 的合法条目保留）。
      if (!seen.add(id)) continue
      entries.add(
        AudioCapsule(
          id = id,
          page = page,
          anchorX = anchorX,
          anchorY = anchorY,
          asset = asset,
          durationMs = durationMs,
          createdAtMillis = createdAt,
          transcript = transcript,
          transcriptStatus = status,
          segments = segments,
          language = language,
          model = model,
        ),
      )
    }
    return entries
  }

  /** 写侧严格校验（整表）。 */
  fun validate(capsules: List<AudioCapsule>) {
    require(capsules.size <= MAX_CAPSULES) { "too many audio capsules: ${capsules.size}" }
    val ids = mutableSetOf<String>()
    capsules.forEach { capsule ->
      require(AudioCapsulePolicy.ID_PATTERN.matches(capsule.id)) { "bad capsule id: ${capsule.id}" }
      require(ids.add(capsule.id)) { "duplicate capsule id: ${capsule.id}" }
      require(capsule.page >= 1) { "bad capsule page: ${capsule.page}" }
      require(capsule.anchorX.isFinite() && capsule.anchorY.isFinite()) { "bad capsule anchor" }
      require(kotlin.math.abs(capsule.anchorX) <= AudioCapsulePolicy.MAX_COORDINATE) {
        "capsule anchor out of range: ${capsule.anchorX}"
      }
      require(kotlin.math.abs(capsule.anchorY) <= AudioCapsulePolicy.MAX_COORDINATE) {
        "capsule anchor out of range: ${capsule.anchorY}"
      }
      require(isValidAssetName(capsule.asset)) { "bad capsule asset: ${capsule.asset}" }
      require(capsule.durationMs > 0) { "bad capsule duration: ${capsule.durationMs}" }
      require(capsule.durationMs <= AudioCapsulePolicy.MAX_DURATION_MS) {
        "capsule duration too long: ${capsule.durationMs}"
      }
      require(capsule.createdAtMillis >= 0L) { "bad capsule createdAt: ${capsule.createdAtMillis}" }
      capsule.transcript?.let { transcript ->
        require(transcript.length <= MAX_TRANSCRIPT_CHARS) { "transcript too long" }
      }
      validateSegments(capsule.segments)
      if (capsule.transcriptStatus == AudioTranscriptStatus.OK) {
        require(capsule.segments.isNotEmpty()) { "ok transcript needs segments" }
        require(capsule.transcript != null) { "ok transcript needs text" }
      }
      capsule.language?.let { require(it.isNotBlank()) { "blank language" } }
      capsule.model?.let { require(it.isNotBlank()) { "blank model" } }
    }
  }

  private fun validateSegments(segments: List<TranscriptSegment>) {
    require(segments.size <= MAX_SEGMENTS_PER_CAPSULE) { "too many segments: ${segments.size}" }
    segments.forEach { segment ->
      require(segment.startMs >= 0) { "bad segment start: ${segment.startMs}" }
      require(segment.endMs > segment.startMs) { "bad segment end: ${segment.endMs}" }
      require(segment.text.isNotEmpty()) { "empty segment text" }
      require(segment.text.length <= MAX_SEGMENT_TEXT_CHARS) { "segment text too long" }
    }
    require(segments.zipWithNext().all { (a, b) -> b.startMs >= a.startMs }) {
      "segments must be sorted by startMs"
    }
  }

  private fun decodeSegments(array: JsonArray?): List<TranscriptSegment>? {
    if (array == null) return emptyList()
    if (array.size > MAX_SEGMENTS_PER_CAPSULE) return null
    val segments = mutableListOf<TranscriptSegment>()
    for (element in array) {
      val entry = element as? JsonObject ?: return null
      val startMs = entry["startMs"]?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 } ?: return null
      val endMs = entry["endMs"]?.jsonPrimitive?.longOrNull?.takeIf { it > startMs } ?: return null
      val text = entry.stringOrNull("text")?.takeIf { it.isNotEmpty() } ?: return null
      if (text.length > MAX_SEGMENT_TEXT_CHARS) return null
      segments.add(TranscriptSegment(startMs, endMs, text))
    }
    if (segments.zipWithNext().any { (a, b) -> b.startMs < a.startMs }) return null
    return segments
  }

  /** 新胶囊（写侧入口）：生成 uuid36 id 并整体校验。 */
  fun newCapsule(
    page: Int,
    anchorX: Double,
    anchorY: Double,
    asset: String,
    durationMs: Long,
    createdAtMillis: Long,
  ): AudioCapsule {
    val capsule = AudioCapsule(
      id = UUID.randomUUID().toString(),
      page = page,
      anchorX = anchorX,
      anchorY = anchorY,
      asset = asset,
      durationMs = durationMs,
      createdAtMillis = createdAtMillis,
      transcript = null,
      transcriptStatus = AudioTranscriptStatus.NONE,
      segments = emptyList(),
    )
    validate(listOf(capsule))
    return capsule
  }

  private fun isValidAssetName(asset: String): Boolean = AudioCapsulePolicy.ASSET_PATTERN.matches(asset)

  private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}
