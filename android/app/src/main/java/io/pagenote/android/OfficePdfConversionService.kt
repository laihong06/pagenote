package io.pagenote.android

import io.pagenote.core.project.OfficeDocumentFormat
import io.pagenote.core.project.OfficePdfConversion
import io.pagenote.core.project.OfficePdfConversionResult
import io.pagenote.core.project.OfficePdfEngine
import io.pagenote.core.project.OfficePreviewCacheKey
import io.pagenote.core.project.OfficePreviewCachePolicy
import io.pagenote.core.project.OfficeSourceRevision
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * 本地 Office→PDF 转换服务（task 36 本地引擎切片的 app 侧编排）。
 *
 * 源字节只读；可丢弃预览写入 app 自有缓存目录，缓存键由
 * [OfficePreviewCacheKey]（源 SHA-256 + 源格式 + 引擎修订 + 导出设置）决定，
 * 任一分量变化即失效重建。引擎未接入时如实报告失败，不伪造预览、不触碰源。
 * SAF/Uri 的读写留在 MainActivity；本类以流为边界，可纯 JVM 测试。
 */
internal class OfficePdfConversionService(val workingDirectory: File) {

  init {
    workingDirectory.mkdirs()
  }

  sealed interface Outcome {
    /** 预览就绪；file 为缓存目录内的 PDF 文件。 */
    data class PreviewReady(val file: File, val cacheKey: OfficePreviewCacheKey) : Outcome

    /** 转换未产生预览；result 给出准确原因（引擎未接入/格式未放开/失败）。 */
    data class Unavailable(val result: OfficePdfConversionResult) : Outcome
  }

  /**
   * 若缓存键对 (currentRevision, format, engine) 仍有效且缓存文件存在，直接复用；
   * 否则转换并原子写入新预览，随后清理其它过期 `office-preview-*.pdf`。
   * 转换失败时不写入任何文件，并丢弃已失效的旧预览。
   */
  fun convertAndCache(
    currentRevision: OfficeSourceRevision,
    format: OfficeDocumentFormat,
    engine: OfficePdfEngine?,
    openSource: () -> InputStream,
  ): Outcome {
    val cachedKey = readCachedKey(format)
    if (OfficePreviewCachePolicy.isUsable(cachedKey, currentRevision, format, engine)) {
      val cached = File(workingDirectory, cachedKey!!.cacheFileName)
      if (cached.isFile && cached.length() > 0) {
        return Outcome.PreviewReady(cached, cachedKey)
      }
    }
    return when (val result = OfficePdfConversion.convert(currentRevision, format, engine, openSource)) {
      is OfficePdfConversionResult.Ready -> {
        val file = writePreviewAtomically(result.cacheKey, result.pdf)
        clearStalePreviews(keep = result.cacheKey)
        Outcome.PreviewReady(file, result.cacheKey)
      }
      else -> {
        // 失效预览直接丢弃，不得把旧预览当作新源。
        cachedKey?.let { File(workingDirectory, it.cacheFileName).delete() }
        Outcome.Unavailable(result)
      }
    }
  }

  fun cachedPreview(
    currentRevision: OfficeSourceRevision,
    format: OfficeDocumentFormat,
    engine: OfficePdfEngine?,
  ): File? {
    val key = readCachedKey(format) ?: return null
    if (!OfficePreviewCachePolicy.isUsable(key, currentRevision, format, engine)) return null
    val file = File(workingDirectory, key.cacheFileName)
    return file.takeIf { it.isFile && it.length() > 0 }
  }

  private fun readCachedKey(format: OfficeDocumentFormat): OfficePreviewCacheKey? =
    runCatching {
      OfficePreviewKeyStore.read(workingDirectory, format)
    }.getOrNull()

  private fun writePreviewAtomically(key: OfficePreviewCacheKey, pdf: ByteArray): File {
    val target = File(workingDirectory, key.cacheFileName)
    val temporary = File(workingDirectory, "${target.name}.${UUID.randomUUID()}.tmp")
    try {
      temporary.writeBytes(pdf)
      if (!temporary.renameTo(target)) {
        temporary.copyTo(target, overwrite = true)
        temporary.delete()
      }
    } finally {
      temporary.delete()
    }
    OfficePreviewKeyStore.write(workingDirectory, key)
    return target
  }

  fun clearStalePreviews(keep: OfficePreviewCacheKey?) {
    val keepName = keep?.cacheFileName
    workingDirectory.listFiles { file ->
      file.name.startsWith(PREVIEW_PREFIX) &&
        file.name != keepName &&
        file.name != KEY_STORE_NAME // 键侧车与预览同名前缀，不得当作过期预览清理
    }
      ?.forEach { it.delete() }
  }

  companion object {
    const val PREVIEW_PREFIX = "office-preview-"
    const val KEY_STORE_NAME = "office-preview-keys.json"
  }
}

/**
 * 预览缓存键的落盘记录：文件名只含键哈希，键的原始分量存到同目录的 JSON 侧车，
 * 供下次会话判断缓存是否仍对当前源/引擎有效。
 */
internal object OfficePreviewKeyStore {
  fun write(directory: File, key: OfficePreviewCacheKey) {
    val payload = buildString {
      append("{\n")
      append("  \"sourceSha256\": \"${key.sourceSha256}\",\n")
      append("  \"format\": \"${key.format.extension}\",\n")
      append("  \"engineId\": \"${jsonEscape(key.engineId)}\",\n")
      append("  \"engineRevision\": \"${jsonEscape(key.engineRevision)}\",\n")
      append("  \"exportSettingsVersion\": ${key.exportSettingsVersion}\n")
      append("}\n")
    }
    File(directory, OfficePdfConversionService.KEY_STORE_NAME).writeText(payload, Charsets.UTF_8)
  }

  /** 只读回与 [format] 同格式的键；缺失/损坏/格式不符返回 null（当作无缓存）。 */
  fun read(directory: File, format: OfficeDocumentFormat): OfficePreviewCacheKey? {
    val file = File(directory, OfficePdfConversionService.KEY_STORE_NAME)
    if (!file.isFile) return null
    val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
    fun field(name: String): String? {
      val match = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text) ?: return null
      return match.groupValues[1]
    }
    val sha = field("sourceSha256") ?: return null
    if (!sha.matches(Regex("^[0-9a-f]{64}$"))) return null
    val storedFormat = field("format") ?: return null
    if (!storedFormat.equals(format.extension, ignoreCase = true)) return null
    val engineId = field("engineId") ?: return null
    val engineRevision = field("engineRevision") ?: return null
    val settingsVersion = Regex("\"exportSettingsVersion\"\\s*:\\s*(-?[0-9]+)").find(text)
      ?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return runCatching {
      OfficePreviewCacheKey(
        sourceSha256 = sha,
        format = format,
        engineId = engineId,
        engineRevision = engineRevision,
        exportSettingsVersion = settingsVersion,
      )
    }.getOrNull()
  }

  private fun jsonEscape(value: String): String = buildString {
    value.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else ->
          if (char < ' ') {
            append("\\u%04x".format(char.code))
          } else {
            append(char)
          }
      }
    }
  }
}

/**
 * 本地 Office 引擎注册点。Android 端在 LibreOfficeKit 集成完成前保持 `null`：
 * 此时 [OfficePdfConversionService] 一律报告引擎不可用，扩展名门控保持关闭，
 * 项目 opener 对 `.doc/.docx/.ppt/.pptx` 的拒绝行为不变。
 */
internal object LocalOfficePdfEngineRegistry {
  @Volatile
  var engine: OfficePdfEngine? = null
}
