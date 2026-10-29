package io.pagenote.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * 贴纸面板拖拽排序数据（移植批次 5）。对齐 touchnotes 贴纸面板
 * （`StickerDialog` + `StickerItemTouchHelperCallback`：RecyclerView 拖拽重排），
 * 持久化为贴纸库根下的 `stickers/order.json`（version 1）：
 *
 * ```json
 * {"version":1,"folders":{"":["cat.png","pack1","b.png"],"pack1":["c.gif"]}}
 * ```
 *
 * - 键为相对 `stickers/` 的文件夹路径（根 = `""`）；值为该文件夹直接子项显示名
 *   的**优先顺序表**；
 * - 排序合成规则（[orderedNames]）：顺序表中已存在的名字按表排列，未列入的名字
 *   按名称不区分大小写排序追加在表后（顺序表只表达覆盖，新增贴纸零配置可见）；
 * - 拖拽落点语义（[applyMove]）：把 `fromIndex` 项移动到 `toIndex`（对齐
 *   ItemTouchHelper `onMove` 的 from/to 索引），产出新顺序即新的顺序表。
 *
 * 纯 JSON 逻辑（kotlinx.serialization），无 Android 依赖；SAF 读写由
 * ProjectStickerLibrary 执行。
 */
object StickerPanelOrder {
  const val ORDER_FILE_NAME = "order.json"
  const val VERSION = 1
  const val ROOT_FOLDER_KEY = ""

  private val json = Json { ignoreUnknownKeys = true }

  /** 编码整棵顺序表；键排序保证字节级稳定输出。 */
  fun encode(orders: Map<String, List<String>>): String {
    val root = buildJsonObject {
      put("version", VERSION)
      put(
        "folders",
        buildJsonObject {
          orders.toSortedMap().forEach { (folder, names) ->
            put(
              folder,
              buildJsonArray {
                names.forEach { name -> add(JsonPrimitive(name)) }
              },
            )
          }
        },
      )
    }
    return Json.encodeToString(JsonObject.serializer(), root)
  }

  /**
   * 读侧容错解码：version 缺失按 1 接受；非对象/坏 JSON 返回空表（顺序数据损坏
   * 时面板退回默认排序，不阻塞贴纸库）；未知 version 返回空表（未来格式交给
   * 未来版本，当前端只读不写）；非字符串项跳过。
   */
  fun decode(text: String): Map<String, List<String>> {
    val root = try {
      json.parseToJsonElement(text).jsonObject
    } catch (_: IllegalArgumentException) {
      return emptyMap()
    }
    val version = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: VERSION
    if (version != VERSION) return emptyMap()
    val folders = root["folders"] as? JsonObject ?: return emptyMap()
    val orders = mutableMapOf<String, List<String>>()
    folders.forEach { (folder, value) ->
      val array = value as? JsonArray ?: return@forEach
      val names = array.mapNotNull { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content
          ?.takeIf(String::isNotEmpty)
      }
      if (names.isNotEmpty()) orders[folder] = names
    }
    return orders
  }

  /**
   * 排序合成：顺序表中的名字（且仍存在于 [names]）按表排列，其余按名称
   * 不区分大小写、大小写敏感逐级比较追加。
   */
  fun orderedNames(names: List<String>, order: List<String>?): List<String> {
    if (names.isEmpty()) return names
    if (order.isNullOrEmpty()) {
      return names.sortedWith { left, right -> compareNames(left, right) }
    }
    val orderRank = HashMap<String, Int>()
    order.forEachIndexed { index, name -> orderRank.putIfAbsent(name, index) }
    return names.sortedWith { left, right ->
      val leftRank = orderRank[left] ?: Int.MAX_VALUE
      val rightRank = orderRank[right] ?: Int.MAX_VALUE
      if (leftRank != rightRank) leftRank.compareTo(rightRank) else compareNames(left, right)
    }
  }

  /** 拖拽重排（ItemTouchHelper onMove 语义）：from → to，越界返回 null。 */
  fun applyMove(names: List<String>, fromIndex: Int, toIndex: Int): List<String>? {
    if (names.isEmpty()) return null
    if (fromIndex !in names.indices || toIndex !in names.indices || fromIndex == toIndex) return null
    val mutable = names.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable
  }

  private fun compareNames(left: String, right: String): Int {
    val lower = left.lowercase(Locale.ROOT).compareTo(right.lowercase(Locale.ROOT))
    if (lower != 0) return lower
    return left.compareTo(right)
  }
}
