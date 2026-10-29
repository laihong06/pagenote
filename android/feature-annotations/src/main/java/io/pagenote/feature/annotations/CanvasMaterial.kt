package io.pagenote.feature.annotations

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 素材数据模型（移植批次 5）。对应 touchnotes 的 `StickerInfo{materials, rect,
 * scale, offsetX/offsetY}`——"保存为素材/贴纸"把画布上的一组对象连同几何存为可
 * 复用单元。pagenote 的投影：一个素材 = 一组合法 annotations.json item（含其
 * extension 字段，贴纸姿态/公式源随之保留），加名称与种类。
 *
 * 素材库是**Android 侧独立文件** `<project>/materials.json`（项目根，与
 * annotations.json 平级）：桌面端不读取也不改写该文件（未知项目文件原样保留），
 * 因此素材库不进 annotations.json root——避免触碰 D3 的 root 键名预留。
 *
 * 文件格式（version 1）：
 * ```json
 * {"version":1,"materials":[
 *   {"id":"<uuid36>","name":"积分公式","kind":"formula",
 *    "createdAt":1730000000000,"items":[ …annotation items… ]}
 * ]}
 * ```
 */
enum class CanvasMaterialKind(val key: String) {
  /** 画布选区保存的通用素材（任意批注项组合）。 */
  SELECTION("selection"),

  /** 单张贴纸（image 项 + `sticker` 扩展字段）。 */
  STICKER("sticker"),

  /** 公式（image 项 + `latex` 扩展字段）。 */
  FORMULA("formula");

  companion object {
    fun fromKey(key: String?): CanvasMaterialKind? =
      key?.let { value -> CanvasMaterialKind.values().firstOrNull { it.key == value } }
  }
}

data class CanvasMaterialEntry(
  val id: String,
  val name: String,
  val kind: CanvasMaterialKind,
  val createdAtMillis: Long,
  val items: JsonArray,
) {
  companion object {
    internal val ID_PATTERN =
      Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
  }
}

/**
 * 素材库编解码与文件存储。读写模式对齐 [AnnotationStore]：原子写（临时文件
 * rename 回退）+ 修改前全量备份到 `materials-history/`；校验对齐
 * [AnnotationValidation]（items 逐项走桌面规则镜像，上限收紧为素材粒度）。
 */
object CanvasMaterialCodec {
  const val VERSION = 1
  const val MAX_MATERIALS = 100
  const val MAX_ITEMS_PER_MATERIAL = 50
  const val MAX_NAME_CODE_POINTS = 80
  const val FILE_NAME = "materials.json"

  private val json = Json { ignoreUnknownKeys = true }

  fun encode(entries: List<CanvasMaterialEntry>): String {
    validate(entries)
    val root = buildJsonObject {
      put("version", VERSION)
      put(
        "materials",
        buildJsonArray {
          entries.forEach { entry ->
            add(
              buildJsonObject {
                put("id", entry.id)
                put("name", entry.name)
                put("kind", entry.kind.key)
                put("createdAt", entry.createdAtMillis)
                put("items", entry.items)
              },
            )
          }
        },
      )
    }
    return Json.encodeToString(JsonObject.serializer(), root)
  }

  /**
   * 读侧容错解码：单条素材字段非法（未知 kind、坏 id、items 非数组、item 非对象）
   * 时跳过该条而非整体失败；version 缺失按 1 接受，未知 version 抛
   * [IllegalArgumentException]（调用方决定是否放弃整库）。重复 id 保留首条。
   */
  fun decode(text: String): List<CanvasMaterialEntry> {
    val root = json.parseToJsonElement(text).jsonObject
    val version = root["version"]?.jsonPrimitive?.longOrNull ?: VERSION.toLong()
    if (version != VERSION.toLong()) {
      throw IllegalArgumentException("unsupported materials version: $version")
    }
    val materials = root["materials"]?.jsonArray ?: return emptyList()
    val seen = mutableSetOf<String>()
    val entries = mutableListOf<CanvasMaterialEntry>()
    for (element in materials) {
      val entry = element as? JsonObject ?: continue
      val id = entry.stringOrNull("id")?.takeIf { CanvasMaterialEntry.ID_PATTERN.matches(it) } ?: continue
      if (!seen.add(id)) continue
      val name = entry.stringOrNull("name")
        ?.takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= MAX_NAME_CODE_POINTS }
        ?: continue
      val kind = CanvasMaterialKind.fromKey(entry.stringOrNull("kind")) ?: continue
      val createdAt = entry["createdAt"]?.jsonPrimitive?.longOrNull ?: continue
      val items = entry["items"] as? JsonArray ?: continue
      if (items.isEmpty() || items.size > MAX_ITEMS_PER_MATERIAL) continue
      if (items.any { it !is JsonObject }) continue
      entries.add(CanvasMaterialEntry(id, name, kind, createdAt, items))
    }
    return entries
  }

  /** 写侧严格校验（素材库整体）。 */
  fun validate(entries: List<CanvasMaterialEntry>) {
    require(entries.size <= MAX_MATERIALS) { "too many materials: ${entries.size}" }
    val ids = mutableSetOf<String>()
    entries.forEach { entry ->
      require(CanvasMaterialEntry.ID_PATTERN.matches(entry.id)) { "bad material id: ${entry.id}" }
      require(ids.add(entry.id)) { "duplicate material id: ${entry.id}" }
      require(entry.name.isNotBlank()) { "blank material name" }
      require(entry.name.codePointCount(0, entry.name.length) <= MAX_NAME_CODE_POINTS) {
        "material name too long"
      }
      require(entry.createdAtMillis >= 0L) { "bad material createdAt: ${entry.createdAtMillis}" }
      require(entry.items.size in 1..MAX_ITEMS_PER_MATERIAL) {
        "bad material item count: ${entry.items.size}"
      }
      // items 走桌面校验镜像（逐项类型/几何/上限，复用 AnnotationValidation）。
      AnnotationValidation.validateItems(entry.items)
    }
  }

  /** 新素材（写侧入口）：生成 uuid id 并整体校验。 */
  fun newEntry(
    name: String,
    kind: CanvasMaterialKind,
    createdAtMillis: Long,
    items: JsonArray,
  ): CanvasMaterialEntry {
    val entry = CanvasMaterialEntry(
      id = UUID.randomUUID().toString(),
      name = name,
      kind = kind,
      createdAtMillis = createdAtMillis,
      items = items,
    )
    validate(listOf(entry))
    return entry
  }

  /** 插入/替换（同 id 覆盖；超出容量时淘汰最旧的 SELECTION）。 */
  fun upsert(entries: List<CanvasMaterialEntry>, entry: CanvasMaterialEntry): List<CanvasMaterialEntry> {
    validate(listOf(entry))
    val replaced = entries.any { it.id == entry.id }
    val next = if (replaced) {
      entries.map { if (it.id == entry.id) entry else it }
    } else {
      entries + entry
    }
    val capped = if (next.size > MAX_MATERIALS) {
      // 淘汰最旧的条目（ createdAt 升序首个），保证 FIFO 上限。
      val oldest = next.minByOrNull(CanvasMaterialEntry::createdAtMillis) ?: return next.take(MAX_MATERIALS)
      next.filterNot { it === oldest && it.id == oldest.id }
    } else {
      next
    }
    validate(capped)
    return capped
  }

  fun delete(entries: List<CanvasMaterialEntry>, id: String): List<CanvasMaterialEntry> =
    entries.filterNot { it.id == id }

  private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}

/** 素材库文件存储（`<project>/materials.json`，feature 层 File IO，SAF 适配在 app 层）。 */
object CanvasMaterialStore {
  fun read(projectDirectory: File): List<CanvasMaterialEntry> {
    val file = File(projectDirectory, CanvasMaterialCodec.FILE_NAME)
    if (!file.exists()) return emptyList()
    return CanvasMaterialCodec.decode(file.readText(Charsets.UTF_8))
  }

  /** 全量保存：先备份旧文件到 `materials-history/`，再原子写。返回是否落盘。 */
  fun write(projectDirectory: File, entries: List<CanvasMaterialEntry>): Boolean {
    CanvasMaterialCodec.validate(entries)
    val file = File(projectDirectory, CanvasMaterialCodec.FILE_NAME)
    val encoded = CanvasMaterialCodec.encode(entries)
    if (file.exists() && file.readText(Charsets.UTF_8) == encoded) return false
    backup(file)
    atomicWrite(file, encoded)
    return true
  }

  private fun backup(file: File) {
    if (!file.exists()) return
    val history = File(file.parentFile, "materials-history")
    history.mkdirs()
    val backup = File(history, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
    val tmp = File(history, backup.name + ".tmp")
    tmp.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
    if (!tmp.renameTo(backup)) {
      backup.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
      tmp.delete()
    }
  }

  private fun atomicWrite(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + "." + UUID.randomUUID() + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (!tmp.renameTo(file)) {
      file.writeText(text, Charsets.UTF_8)
      tmp.delete()
    }
  }
}
