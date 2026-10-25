package io.pagenote.android

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * GIF 帧解码数据结构（移植批次 5）。对齐 touchnotes 的动效贴纸
 * （`GifImage extends Material` + `PanelManager.addGif`）：帧级元数据先行解析，
 * 像素解码归渲染层（画布重做批次接 ImageDecoder/AnimatedImageDrawable）。
 *
 * 纯字节级头解析（GIF87a/89a，W3C gif89a 规范）：
 * - Logical Screen Descriptor → 画布宽高；
 * - Image Separator `0x2C` 计帧 + 每帧 LZW 数据块跳过；
 * - Graphic Control Extension `0xF9` → 每帧时延（delay*10ms）；
 * - Application Extension `NETSCAPE2.0` → 循环次数（子块偏移 1 的 16 位小端；
 *   规范值 0 表示无限循环，[GifDocumentMetadata.loopCount] 存原始值）。
 *
 * 时域兼容规则（[GifFrameTiming.effectiveDurationMs]）：delay < 20ms（很多
 * 制作工具写 0）按浏览器惯例回退 100ms——与 touchnotes 播放器行为一致的可视
 * 结果；原始值保留在 [GifFrameTiming.durationMs]。
 */
data class GifFrameTiming(
  /** 帧序号（0 起，按出现顺序）。 */
  val index: Int,
  /** GCE delay × 10（毫秒），原始值；无 GCE 的帧为 0。 */
  val durationMs: Int,
) {
  /** 播放时长：原始 <20ms 回退 100ms（浏览器惯例）。 */
  val effectiveDurationMs: Int
    get() = if (durationMs < BROWSER_MIN_EFFECTIVE_MS) BROWSER_FALLBACK_MS else durationMs

  companion object {
    const val BROWSER_MIN_EFFECTIVE_MS = 20
    const val BROWSER_FALLBACK_MS = 100
  }
}

data class GifDocumentMetadata(
  val width: Int,
  val height: Int,
  val frameCount: Int,
  /** NETSCAPE 循环次数；null = 无循环扩展（播放一次）；0 = 无限循环。 */
  val loopCount: Int?,
  val frames: List<GifFrameTiming>,
) {
  /** 单帧或 1×1 画布视为静态图。 */
  val isAnimated: Boolean get() = frameCount > 1
}

object GifMetadataParser {
  private val HEADER_87A = byteArrayOf(0x47, 0x49, 0x46, 0x37, 0x38, 0x61) // "GIF87a"
  private val HEADER_89A = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) // "GIF89a"
  private const val EXTENSION_INTRODUCER = 0x21
  private const val IMAGE_SEPARATOR = 0x2C
  private const val TRAILER = 0x3B
  private const val GRAPHIC_CONTROL_LABEL = 0xF9
  private const val APPLICATION_LABEL = 0xFF
  private const val MAX_FRAMES = 10_000

  /** 解析失败（非 GIF/截断/畸形）返回 null；不抛出。 */
  fun parse(bytes: ByteArray): GifDocumentMetadata? = try {
    parse(ByteArrayInputStream(bytes))
  } catch (_: IOException) {
    null
  } catch (_: IllegalStateException) {
    null
  }

  fun parse(source: InputStream): GifDocumentMetadata? {
    val input = LittleEndianReader(source)
    val header = input.readBytes(6)
    if (!header.contentEquals(HEADER_87A) && !header.contentEquals(HEADER_89A)) return null

    // Logical Screen Descriptor
    val width = input.readUInt16()
    val height = input.readUInt16()
    val packed = input.readUInt8()
    if (packed and 0x80 != 0) {
      // 全局颜色表：3 × 2^(N+1) 字节。
      input.readBytes(3 shl ((packed and 0x07) + 1))
    }
    input.readUInt8() // background color index
    input.readUInt8() // pixel aspect ratio

    var loopCount: Int? = null
    val frames = mutableListOf<GifFrameTiming>()
    var pendingDelayMs = 0
    var sawTrailer = false
    while (!sawTrailer) {
      when (val block = input.readUInt8()) {
        TRAILER -> sawTrailer = true
        EXTENSION_INTRODUCER -> {
          val label = input.readUInt8()
          when (label) {
            GRAPHIC_CONTROL_LABEL -> {
              val gce = readSubBlocks(input)
              if (gce.size >= 3) {
                val delayCs = (gce[1].toInt() and 0xFF) or ((gce[2].toInt() and 0xFF) shl 8)
                pendingDelayMs = delayCs * 10
              }
            }
            APPLICATION_LABEL -> {
              val app = readSubBlocks(input)
              loopCount = loopCount ?: parseNetscapeLoop(app)
            }
            else -> readSubBlocks(input)
          }
        }
        IMAGE_SEPARATOR -> {
          // Image Descriptor：left/top/width/height(4×uint16) + packed。
          input.readBytes(8)
          val imagePacked = input.readUInt8()
          if (imagePacked and 0x80 != 0) {
            input.readBytes(3 shl ((imagePacked and 0x07) + 1))
          }
          input.readUInt8() // LZW minimum code size
          readSubBlocks(input) // 图像数据子块
          if (frames.size >= MAX_FRAMES) return null
          frames.add(GifFrameTiming(index = frames.size, durationMs = pendingDelayMs))
          pendingDelayMs = 0
        }
        else -> return null // 未知块标签：畸形流。
      }
    }
    if (frames.isEmpty()) return null
    return GifDocumentMetadata(
      width = width,
      height = height,
      frameCount = frames.size,
      loopCount = loopCount,
      frames = frames,
    )
  }

  /** 读净载荷（去掉每块 1 字节长度头）拼接；0 长度块终止。 */
  private fun readSubBlocks(input: LittleEndianReader): ByteArray {
    val payload = mutableListOf<Byte>()
    while (true) {
      val size = input.readUInt8()
      if (size == 0) break
      payload.addAll(input.readBytes(size).toList())
    }
    return payload.toByteArray()
  }

  /**
   * NETSCAPE2.0 应用块：[readSubBlocks] 已剥离各子块长度头，载荷为
   * 11 字节标识 + [0x01, loopLo, loopHi]（子块 id 1 + 16 位小端循环计数）。
   */
  private fun parseNetscapeLoop(payload: ByteArray): Int? {
    if (payload.size < 14) return null
    val identifier = String(payload, 0, 11, Charsets.US_ASCII)
    if (identifier != "NETSCAPE2.0" && identifier != "ANIMEXTS1.0") return null
    if (payload[11].toInt() != 1) return null // 循环子块 id 固定 1
    return (payload[12].toInt() and 0xFF) or ((payload[13].toInt() and 0xFF) shl 8)
  }

  private class LittleEndianReader(private val source: InputStream) {
    fun readUInt8(): Int {
      val value = source.read()
      if (value < 0) throw IOException("unexpected end of GIF")
      return value
    }

    fun readUInt16(): Int = readUInt8() or (readUInt8() shl 8)

    fun readBytes(count: Int): ByteArray {
      val buffer = ByteArray(count)
      var offset = 0
      while (offset < count) {
        val read = source.read(buffer, offset, count - offset)
        if (read < 0) throw IOException("unexpected end of GIF")
        offset += read
      }
      return buffer
    }
  }
}
