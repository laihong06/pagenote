package io.pagenote.android

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** GIF 帧解码数据结构测试：手工构造 GIF 字节流覆盖头/帧/时延/循环解析。 */
class GifDocumentMetadataTest {
  /** 构造最小合法 GIF：LSD + 可选 GCT + N 个图像块 + 可选 NETSCAPE 扩展。 */
  private fun gif(
    width: Int,
    height: Int,
    frameCount: Int,
    delayCsPerFrame: Int? = null,
    loopCount: Int? = null,
    withTrailer: Boolean = true,
    globalColorTable: Boolean = false,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    out.write("GIF89a".toByteArray(Charsets.US_ASCII))
    fun u16(value: Int) {
      out.write(value and 0xFF)
      out.write((value shr 8) and 0xFF)
    }

    // Logical Screen Descriptor：packed 的 GCT 位 + size 位（2 色 → N=0 → 2*3=6 字节）。
    val packed = if (globalColorTable) 0x80 or 0x00 else 0x00
    u16(width)
    u16(height)
    out.write(packed)
    out.write(0) // background
    out.write(0) // aspect

    if (globalColorTable) {
      out.write(byteArrayOf(0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
    }

    if (loopCount != null) {
      out.write(0x21) // extension
      out.write(0xFF) // application
      // 子块 1：11 字节标识；子块 2：id=1 + 16 位小端循环计数。
      val identifier = "NETSCAPE2.0".toByteArray(Charsets.US_ASCII)
      out.write(identifier.size)
      out.write(identifier)
      out.write(3)
      out.write(1)
      out.write(loopCount and 0xFF)
      out.write((loopCount shr 8) and 0xFF)
      out.write(0) // block terminator
    }

    repeat(frameCount) {
      if (delayCsPerFrame != null) {
        out.write(0x21) // extension
        out.write(0xF9) // graphic control
        out.write(4) // block size
        out.write(0x00) // packed
        out.write(delayCsPerFrame and 0xFF)
        out.write((delayCsPerFrame shr 8) and 0xFF)
        out.write(0) // transparent index
        out.write(0) // terminator
      }
      out.write(0x2C) // image separator
      u16(0) // left
      u16(0) // top
      u16(1) // width
      u16(1) // height
      out.write(0x00) // packed: no LCT
      out.write(2) // LZW min code size
      out.write(2) // data sub-block size
      out.write(0x00)
      out.write(0x00)
      out.write(0) // terminator
    }

    if (withTrailer) out.write(0x3B)
    return out.toByteArray()
  }

  @Test
  fun parsesHeaderDimensionsAndFrameCount() {
    val metadata = GifMetadataParser.parse(gif(320, 240, 3))!!
    assertEquals(320, metadata.width)
    assertEquals(240, metadata.height)
    assertEquals(3, metadata.frameCount)
    assertEquals(3, metadata.frames.size)
    assertEquals(listOf(0, 1, 2), metadata.frames.map(GifFrameTiming::index))
  }

  @Test
  fun parsesGlobalColorTableWithoutBreakingOffsets() {
    val metadata = GifMetadataParser.parse(gif(10, 10, 2, globalColorTable = true))!!
    assertEquals(10, metadata.width)
    assertEquals(2, metadata.frameCount)
  }

  @Test
  fun parsesFrameDelays() {
    val metadata = GifMetadataParser.parse(gif(8, 8, 2, delayCsPerFrame = 20))!!
    assertEquals(listOf(200, 200), metadata.frames.map(GifFrameTiming::durationMs))
    assertEquals(listOf(200, 200), metadata.frames.map(GifFrameTiming::effectiveDurationMs))
  }

  @Test
  fun zeroDelayFallsBackToBrowserConvention() {
    val metadata = GifMetadataParser.parse(gif(8, 8, 2, delayCsPerFrame = 0))!!
    assertEquals(listOf(0, 0), metadata.frames.map(GifFrameTiming::durationMs))
    assertEquals(listOf(100, 100), metadata.frames.map(GifFrameTiming::effectiveDurationMs))
  }

  @Test
  fun parsesNetscapeLoopCount() {
    val infinite = GifMetadataParser.parse(gif(8, 8, 2, loopCount = 0))!!
    assertEquals(0, infinite.loopCount)
    val three = GifMetadataParser.parse(gif(8, 8, 2, loopCount = 3))!!
    assertEquals(3, three.loopCount)
    // 无扩展 → null（播放一次）。
    val once = GifMetadataParser.parse(gif(8, 8, 2))!!
    assertNull(once.loopCount)
  }

  @Test
  fun animatedFlagRequiresMultipleFrames() {
    val single = GifMetadataParser.parse(gif(8, 8, 1))!!
    assertEquals(false, single.isAnimated)
    val multi = GifMetadataParser.parse(gif(8, 8, 2))!!
    assertEquals(true, multi.isAnimated)
  }

  @Test
  fun rejectsNonGifAndTruncatedStreams() {
    assertNull(GifMetadataParser.parse("PNG is not a gif".toByteArray()))
    assertNull(GifMetadataParser.parse(ByteArray(0)))
    // 无 trailer 截断。
    assertNull(GifMetadataParser.parse(gif(8, 8, 1, withTrailer = false)))
    // 头后立即出现未知块标签。
    assertNull(GifMetadataParser.parse(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) + byteArrayOf(
      8, 0, 8, 0, 0x70, 0, 0, 0x55,
    )))
  }
}
