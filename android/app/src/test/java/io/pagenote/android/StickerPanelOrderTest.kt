package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 贴纸面板拖拽排序数据测试：order.json 编解码容错、顺序合成、拖拽移动语义。 */
class StickerPanelOrderTest {
  @Test
  fun encodeDecodeRoundTripsAllFolders() {
    val orders = mapOf(
      StickerPanelOrder.ROOT_FOLDER_KEY to listOf("cat.png", "pack1", "b.png"),
      "pack1" to listOf("c.gif"),
    )
    val decoded = StickerPanelOrder.decode(StickerPanelOrder.encode(orders))
    assertEquals(orders, decoded)
  }

  @Test
  fun decodeIsTolerant() {
    // 坏 JSON / 非对象 → 空表。
    assertTrue(StickerPanelOrder.decode("not json {").isEmpty())
    assertTrue(StickerPanelOrder.decode("[1,2]").isEmpty())
    // 未知 version → 空表。
    assertTrue(StickerPanelOrder.decode("""{"version":2,"folders":{}}""").isEmpty())
    // version 缺失按 1；非字符串项跳过；空名字跳过。
    val decoded = StickerPanelOrder.decode(
      """{"folders":{"":["a.png",3,"","b.gif"],"g":["x.png"]}}""",
    )
    assertEquals(mapOf("" to listOf("a.png", "b.gif"), "g" to listOf("x.png")), decoded)
    // folders 缺失 → 空表。
    assertTrue(StickerPanelOrder.decode("""{"version":1}""").isEmpty())
  }

  @Test
  fun orderedNamesAppliesOrderThenAppendsNewNamesSorted() {
    val names = listOf("b.png", "cat.png", "pack1", "a.gif")
    val ordered = StickerPanelOrder.orderedNames(names, listOf("pack1", "cat.png"))
    assertEquals(listOf("pack1", "cat.png", "a.gif", "b.png"), ordered)
  }

  @Test
  fun orderedNamesDefaultsToCaseInsensitiveSort() {
    val ordered = StickerPanelOrder.orderedNames(listOf("B.png", "a.png", "C.gif"), null)
    assertEquals(listOf("a.png", "B.png", "C.gif"), ordered)
  }

  @Test
  fun orderedNamesIgnoresStaleEntries() {
    // 顺序表里已删除的名字不占位。
    val ordered = StickerPanelOrder.orderedNames(listOf("x.png", "y.png"), listOf("gone.png", "y.png", "x.png"))
    assertEquals(listOf("y.png", "x.png"), ordered)
  }

  @Test
  fun applyMoveMatchesItemTouchHelperSemantics() {
    val names = listOf("a", "b", "c", "d")
    assertEquals(listOf("b", "c", "a", "d"), StickerPanelOrder.applyMove(names, 0, 2))
    assertEquals(listOf("d", "a", "b", "c"), StickerPanelOrder.applyMove(names, 3, 0))
    assertEquals(null, StickerPanelOrder.applyMove(names, -1, 0))
    assertEquals(null, StickerPanelOrder.applyMove(names, 0, 4))
    assertEquals(null, StickerPanelOrder.applyMove(names, 1, 1))
    assertEquals(null, StickerPanelOrder.applyMove(emptyList(), 0, 0))
  }

  @Test
  fun dragAndPersistFlow() {
    // 面板拖拽 → 新顺序 → 落 order.json → 重读合成。
    var names = StickerPanelOrder.orderedNames(listOf("a.png", "b.png", "c.png"), null)
    names = StickerPanelOrder.applyMove(names, 2, 0)!!
    val orders = mapOf(StickerPanelOrder.ROOT_FOLDER_KEY to names)
    val reloaded = StickerPanelOrder.decode(StickerPanelOrder.encode(orders))
    val merged = StickerPanelOrder.orderedNames(listOf("b.png", "a.png", "c.png", "new.png"), reloaded[""])
    assertEquals(listOf("c.png", "a.png", "b.png", "new.png"), merged)
  }
}
