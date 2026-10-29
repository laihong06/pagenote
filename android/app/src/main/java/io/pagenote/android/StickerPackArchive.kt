package io.pagenote.android

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 贴纸资产包格式（移植批次 5，决策 D7）。对齐 touchnotes 的 zip 贴纸包
 * （`StickerManager.importZipSticker`：zip → 解包 → 目录入库）：包内为图片文件
 * （png/jpg/jpeg/gif/webp），可选**一层**子目录作为分组（对齐其
 * `resolveDirectoryStickerFiles` 的 cover/thumb 结构层级）。
 *
 * 校验规则（写侧严格，全部满足才入库）：
 * - 每条目为图片扩展名；目录条目跳过；
 * - 条目路径拒绝绝对路径、`..` 路径段、空段与反斜杠；层级 ≤2（`cat.png` 或
 *   `group/cat.png`），更深层级拒绝；
 * - 尺寸：单文件 ≤[MAX_ENTRY_BYTES]（20MB）、总计 ≤[MAX_TOTAL_BYTES]（200MB）、
 *   条目数 ≤[MAX_ENTRIES]（200，含 zip-bomb 防护）；
 * - 解包后的显示名（`group/cat`）不得重复。
 *
 * 数据结构：[StickerPackEntry] 是面板/落盘共用的条目描述（显示名、分组、文件名、
 * 字节数）；zip 读取本身由 SAF 导入器（ProjectStickerPackImporter）流式执行。
 */
data class StickerPackEntry(
  /** 面板显示名（不含扩展名；分组内文件名）。 */
  val displayName: String,
  /** 保留扩展名的文件名（分组内，如 `cat.png`）。 */
  val fileName: String,
  /** 一层分组名；根级文件为空串。 */
  val group: String,
  /** 相对贴纸库根的相对路径（`group/cat.png`）。 */
  val relativePath: String,
  val sizeBytes: Long,
)

object StickerPackArchive {
  const val MAX_ENTRIES = 200
  const val MAX_ENTRY_BYTES = 20L * 1024L * 1024L
  const val MAX_TOTAL_BYTES = 200L * 1024L * 1024L

  val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")

  fun mimeTypeFor(extension: String): String? = when (extension.lowercase(Locale.ROOT)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> null
  }

  /**
   * 流式校验并收集条目描述。[source] 不被关闭。任一规则不满足抛 [IOException]
   * （携带原因），返回值只在整体合法时产出。
   */
  fun validate(source: InputStream): List<StickerPackEntry> {
    val entries = mutableListOf<StickerPackEntry>()
    val displayNames = mutableSetOf<String>()
    var totalBytes = 0L
    ZipInputStream(source.buffered()).use { zip ->
      while (true) {
        val entry: ZipEntry = zip.nextEntry ?: break
        try {
          if (entry.isDirectory) continue
          if (entries.size >= MAX_ENTRIES) {
            throw IOException("Sticker pack has too many entries (max $MAX_ENTRIES)")
          }
          val descriptor = describeEntry(entry.name)
            ?: throw IOException("Sticker pack entry is not a supported image: ${entry.name}")
          val size = consumeEntryBytes(zip, entry)
          if (size > MAX_ENTRY_BYTES) {
            throw IOException("Sticker pack entry is too large: ${entry.name}")
          }
          totalBytes += size
          if (totalBytes > MAX_TOTAL_BYTES) {
            throw IOException("Sticker pack is too large (max ${MAX_TOTAL_BYTES / (1024L * 1024L)} MB)")
          }
          if (!displayNames.add(descriptor.displayName)) {
            throw IOException("Sticker pack has a duplicate name: ${descriptor.displayName}")
          }
          entries.add(descriptor.copy(sizeBytes = size))
        } finally {
          zip.closeEntry()
        }
      }
    }
    if (entries.isEmpty()) throw IOException("Sticker pack has no images")
    return entries
  }

  fun validate(bytes: ByteArray): List<StickerPackEntry> = validate(ByteArrayInputStream(bytes))

  /**
   * 单条目名解析：`cat.png` → 根级；`group/cat.png` → 分组；`a/b/c.png`、
   * `/cat.png`、`../cat.png`、`cat.txt` → null（拒绝）。
   */
  fun describeEntry(entryName: String): StickerPackEntry? {
    if (entryName.isBlank() || entryName.contains('\\')) return null
    val segments = entryName.split('/')
    if (segments.size > 2) return null
    val fileName = segments.last()
    // 根级文件 group 为空串合法；空段（"a//b"）已由下行 any 拒绝。
    val group = segments.firstOrNull()?.takeIf { segments.size == 2 } ?: ""
    if (fileName.isBlank()) return null
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension !in IMAGE_EXTENSIONS) return null
    val displayName = fileName.substringBeforeLast('.')
    if (displayName.isBlank()) return null
    val relativePath = if (group.isBlank()) fileName else "$group/$fileName"
    return StickerPackEntry(
      displayName = displayName,
      fileName = fileName,
      group = group,
      relativePath = relativePath,
      sizeBytes = 0L,
    )
  }

  private fun consumeEntryBytes(zip: ZipInputStream, entry: ZipEntry): Long {
    // 声明尺寸先行拦截（未声明尺寸的 provider 流式计数兜底）。
    val declared = entry.size
    if (declared > MAX_ENTRY_BYTES) return declared
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
      val count = zip.read(buffer)
      if (count < 0) break
      total += count
      if (total > MAX_ENTRY_BYTES) return total
    }
    return total
  }
}
