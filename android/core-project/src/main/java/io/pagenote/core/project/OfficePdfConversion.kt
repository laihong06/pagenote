package io.pagenote.core.project

import java.io.InputStream

/**
 * 本地 Office→PDF 引擎边界（task 36 本地引擎切片）。
 *
 * Android 端尚无实现（LibreOfficeKit 集成未完成）；宿主通过注册一个
 * [OfficePdfEngine] 实例接入。边界之外不假设引擎存在：
 * `null` 引擎一律得到 [OfficePdfConversionResult.EngineUnavailable]，
 * 不产生伪造的 PDF 预览。
 */
interface OfficePdfEngine {
  val engineId: String

  /** 引擎修订标识；参与预览缓存键，引擎升级即失效旧预览。 */
  val engineRevision: String

  /** 导出设置/版本；参与预览缓存键。 */
  val exportSettingsVersion: Int

  /** 引擎已证明可离线打开并渲染的格式集合；未证明的扩展名不得放开。 */
  val supportedFormats: Set<OfficeDocumentFormat>

  /** 把完整 Office 源字节转换为 PDF 字节；失败抛 [OfficePdfEngineException]。 */
  @Throws(OfficePdfEngineException::class)
  fun convertToPdf(source: InputStream): ByteArray
}

class OfficePdfEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 可丢弃的 PDF 预览缓存键：源 SHA-256 + 源格式 + 引擎修订 + 导出设置/版本。 */
data class OfficePreviewCacheKey(
  val sourceSha256: String,
  val format: OfficeDocumentFormat,
  val engineId: String,
  val engineRevision: String,
  val exportSettingsVersion: Int,
) {
  init {
    require(sourceSha256.matches(Regex("^[0-9a-f]{64}$"))) { "invalid source digest" }
    require(engineId.isNotBlank()) { "engine id must not be blank" }
    require(engineRevision.isNotBlank()) { "engine revision must not be blank" }
    require(exportSettingsVersion >= 0) { "invalid export settings version" }
  }

  /** 由全部键分量派生的确定性缓存文件名；分量变化必产生不同文件名。 */
  val cacheFileName: String
    get() {
      val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(
          "$sourceSha256\n${format.extension}\n$engineId\n$engineRevision\n$exportSettingsVersion"
            .toByteArray(Charsets.UTF_8),
        )
      return "office-preview-" + digest.joinToString("") { "%02x".format(it) } + ".pdf"
    }
}

sealed interface OfficePdfConversionResult {
  /** 转换成功；pdf 为完整 PDF 字节，cacheKey 标识本次预览缓存项。 */
  data class Ready(val pdf: ByteArray, val cacheKey: OfficePreviewCacheKey) : OfficePdfConversionResult {
    override fun equals(other: Any?): Boolean = other is Ready &&
      other.pdf.contentEquals(pdf) &&
      other.cacheKey == cacheKey

    override fun hashCode(): Int = pdf.contentHashCode() * 31 + cacheKey.hashCode()
  }

  /** 引擎未声明支持该格式：扩展名门控保持关闭，源不动。 */
  data class UnsupportedFormat(val format: OfficeDocumentFormat) : OfficePdfConversionResult

  /** 未接入本地引擎：不得伪造预览，也不得放开扩展名。 */
  data class EngineUnavailable(val format: OfficeDocumentFormat) : OfficePdfConversionResult

  /** 引擎转换失败；reason 为引擎侧短消息，源字节保留。 */
  data class Failed(val format: OfficeDocumentFormat, val reason: String) : OfficePdfConversionResult
}

object OfficePdfConversion {
  const val MAX_REASON_LENGTH = 300

  /**
   * 把 Office 源转换为本地 PDF 预览字节。
   *
   * @param sourceRevision 打开时计算好的源 SHA-256 revision（完整源字节流式哈希）。
   * @param openSource 重新打开源字节流的回调；转换独立消费，不复用已哈希过的流。
   */
  fun convert(
    sourceRevision: OfficeSourceRevision,
    format: OfficeDocumentFormat,
    engine: OfficePdfEngine?,
    openSource: () -> InputStream,
  ): OfficePdfConversionResult {
    if (engine == null) return OfficePdfConversionResult.EngineUnavailable(format)
    if (format !in engine.supportedFormats) {
      return OfficePdfConversionResult.UnsupportedFormat(format)
    }
    return try {
      val pdf = openSource().use { stream -> engine.convertToPdf(stream) }
      OfficePdfConversionResult.Ready(
        pdf = pdf,
        cacheKey = OfficePreviewCacheKey(
          sourceSha256 = sourceRevision.sha256,
          format = format,
          engineId = engine.engineId,
          engineRevision = engine.engineRevision,
          exportSettingsVersion = engine.exportSettingsVersion,
        ),
      )
    } catch (cancelled: Throwable) {
      // 与协作家保持一致：JVM 层取消（java.util.concurrent.CancellationException）
      // 原样上抛，不折叠成转换失败。
      if (cancelled is java.util.concurrent.CancellationException) throw cancelled
      OfficePdfConversionResult.Failed(
        format = format,
        reason = (cancelled.message ?: cancelled.javaClass.simpleName)
          .take(MAX_REASON_LENGTH),
      )
    }
  }
}

object OfficePreviewCachePolicy {
  /**
   * 缓存键任一分量（源 SHA-256、源格式、引擎 id/修订、导出设置版本）变化即失效；
   * 不得复用旧预览冒充新源。
   */
  fun isUsable(
    key: OfficePreviewCacheKey?,
    currentRevision: OfficeSourceRevision,
    format: OfficeDocumentFormat,
    engine: OfficePdfEngine?,
  ): Boolean = key != null && engine != null &&
    key.sourceSha256 == currentRevision.sha256 &&
    key.format == format &&
    key.engineId == engine.engineId &&
    key.engineRevision == engine.engineRevision &&
    key.exportSettingsVersion == engine.exportSettingsVersion
}
