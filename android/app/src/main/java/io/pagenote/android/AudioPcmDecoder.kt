package io.pagenote.android

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import io.pagenote.feature.audio.PcmAudio
import java.nio.ByteOrder

/**
 * 录音文件 → 16kHz 单声道 float PCM（移植批次 8）：MediaExtractor 选择音轨 +
 * MediaCodec 解码 → 重采样（[PcmAudio.resampleToTarget] 线性插值）。产出直接
 * 供 [io.pagenote.feature.audio.AsrTranscriber] 消费。
 */
internal object AudioPcmDecoder {
  /** 解码到 PCM；格式不支持/解码失败抛 [IllegalStateException]。 */
  fun decodeTo16kMono(descriptor: ParcelFileDescriptor): PcmAudio {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(descriptor.fileDescriptor)
      val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        mime.startsWith("audio/")
      } ?: throw IllegalStateException("no audio track in recording")
      extractor.selectTrack(trackIndex)
      val format = extractor.getTrackFormat(trackIndex)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("missing audio mime")
      val codec = MediaCodec.createDecoderByType(mime)
      try {
        codec.configure(format, null, null, 0)
        codec.start()
        return drain(extractor, codec, format)
      } finally {
        try {
          codec.stop()
        } catch (_: Exception) {
        }
        codec.release()
      }
    } finally {
      try {
        extractor.release()
      } catch (_: Exception) {
      }
    }
  }

  private fun drain(extractor: MediaExtractor, codec: MediaCodec, format: MediaFormat): PcmAudio {
    val pcmChunks = mutableListOf<ShortArray>()
    var inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val bufferInfo = MediaCodec.BufferInfo()
    var sawInputEos = false
    var sawOutputEos = false
    var resampleFormat = false
    while (!sawOutputEos) {
      if (!sawInputEos) {
        val inputIndex = codec.dequeueInputBuffer(10_000L)
        if (inputIndex >= 0) {
          val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
          val size = extractor.readSampleData(inputBuffer, 0)
          if (size < 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            sawInputEos = true
          } else {
            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }
      val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
      when {
        outputIndex >= 0 -> {
          val outputBuffer = codec.getOutputBuffer(outputIndex)
          if (outputBuffer != null && bufferInfo.size > 0) {
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            if (!resampleFormat) {
              val outputFormat = codec.outputFormat
              inputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
              inputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
              resampleFormat = true
            }
            val shorts = ShortArray(bufferInfo.size / 2)
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            pcmChunks.add(shorts)
          }
          codec.releaseOutputBuffer(outputIndex, false)
          if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
        }
        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          val outputFormat = codec.outputFormat
          inputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          inputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        }
      }
    }
    val mono = toMono(pcmChunks, inputChannels)
    return PcmAudio.resampleToTarget(mono, inputSampleRate)
  }

  /** 交错多声道 → 单声道（均值下混）。 */
  internal fun toMono(chunks: List<ShortArray>, channels: Int): FloatArray {
    val validChannels = channels.coerceAtLeast(1)
    if (validChannels == 1) {
      val total = chunks.sumOf { it.size }
      val output = FloatArray(total)
      var offset = 0
      chunks.forEach { chunk ->
        chunk.forEachIndexed { index, sample -> output[offset + index] = sample / 32768f }
        offset += chunk.size
      }
      return output
    }
    val frames = chunks.sumOf { it.size } / validChannels
    val output = FloatArray(frames)
    var frame = 0
    chunks.forEach { chunk ->
      var index = 0
      while (index + validChannels <= chunk.size && frame < frames) {
        var sum = 0f
        repeat(validChannels) { channel -> sum += chunk[index + channel] / 32768f }
        output[frame] = sum / validChannels
        frame++
        index += validChannels
      }
    }
    return output
  }
}
