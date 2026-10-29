package io.pagenote.feature.annotations

import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 素材数据模型 + 素材库（materials.json）编解码与文件存储测试。 */
class CanvasMaterialTest {
  @get:Rule
  val folder = TemporaryFolder()

  private fun imageItem(label: String): kotlinx.serialization.json.JsonObject = buildJsonObject {
    put("type", "image")
    put("color", "#000000")
    put("width", 1.0)
    put("url", "/attachments/0123abcd-0000-4000-8000-000000000000.png")
    put("points", buildJsonArray {
      add(buildJsonArray { add(0.0); add(0.0) })
      add(buildJsonArray { add(50.0); add(50.0) })
    })
    put("sticker", label)
  }

  @Test
  fun encodeDecodeRoundTrips() {
    val entry = CanvasMaterialCodec.newEntry(
      name = "常用贴纸",
      kind = CanvasMaterialKind.STICKER,
      createdAtMillis = 1_700_000_000_000,
      items = JsonArray(listOf(imageItem("cat.png"))),
    )
    val text = CanvasMaterialCodec.encode(listOf(entry))
    val decoded = CanvasMaterialCodec.decode(text)

    assertEquals(listOf(entry), decoded)
  }

  @Test
  fun newEntryGeneratesUuidIdAndValidatesItems() {
    val entry = CanvasMaterialCodec.newEntry(
      name = "公式",
      kind = CanvasMaterialKind.FORMULA,
      createdAtMillis = 1L,
      items = JsonArray(listOf(imageItem("f"))),
    )
    assertTrue(CanvasMaterialEntry.ID_PATTERN.matches(entry.id))

    // 非法 item（坏颜色）在 newEntry 即被拒。
    val badItem = buildJsonObject {
      put("type", "image")
      put("color", "red")
      put("width", 1.0)
      put("url", "/attachments/0123abcd-0000-4000-8000-000000000000.png")
      put("points", buildJsonArray {
        add(buildJsonArray { add(0.0); add(0.0) })
        add(buildJsonArray { add(50.0); add(50.0) })
      })
    }
    try {
      CanvasMaterialCodec.newEntry("bad", CanvasMaterialKind.SELECTION, 1L, JsonArray(listOf(badItem)))
      fail("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun decodeSkipsInvalidEntriesAndKeepsValidOnes() {
    val goodItemJson = imageItem("good").toString()
    val text = """
      {"version":1,"materials":[
        {"id":"not-a-uuid","name":"bad-id","kind":"sticker","createdAt":1,"items":[$goodItemJson]},
        {"id":"0123abcd-0000-4000-8000-000000000001","name":"","kind":"sticker","createdAt":1,"items":[$goodItemJson]},
        {"id":"0123abcd-0000-4000-8000-000000000002","name":"bad-kind","kind":"unknown","createdAt":1,"items":[$goodItemJson]},
        {"id":"0123abcd-0000-4000-8000-000000000003","name":"good","kind":"sticker","createdAt":5,"items":[$goodItemJson]},
        {"id":"0123abcd-0000-4000-8000-000000000004","name":"empty-items","kind":"sticker","createdAt":6,"items":[]}
      ]}
    """.trimIndent()
    val decoded = CanvasMaterialCodec.decode(text)
    assertEquals(1, decoded.size)
    val kept = decoded.single()
    assertEquals("0123abcd-0000-4000-8000-000000000003", kept.id)
    assertEquals("good", kept.name)
    assertEquals(CanvasMaterialKind.STICKER, kept.kind)
    assertEquals(5L, kept.createdAtMillis)
  }

  @Test
  fun decodeRejectsUnknownVersion() {
    try {
      CanvasMaterialCodec.decode("""{"version":2,"materials":[]}""")
      fail("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun upsertReplacesSameIdAndEvictsOldestOverCapacity() {
    var entries = (1..CanvasMaterialCodec.MAX_MATERIALS).map { index ->
      CanvasMaterialCodec.newEntry("m$index", CanvasMaterialKind.SELECTION, index.toLong(), JsonArray(listOf(imageItem("i"))))
    }
    assertEquals(CanvasMaterialCodec.MAX_MATERIALS, entries.size)

    val replacement = entries.last().copy(name = "renamed")
    entries = CanvasMaterialCodec.upsert(entries, replacement)
    assertEquals(CanvasMaterialCodec.MAX_MATERIALS, entries.size)
    assertTrue(entries.any { it.name == "renamed" })

    // 超容量：淘汰最旧（createdAt=1）。
    val extra = CanvasMaterialCodec.newEntry("extra", CanvasMaterialKind.SELECTION, 10_000L, JsonArray(listOf(imageItem("e"))))
    entries = CanvasMaterialCodec.upsert(entries, extra)
    assertEquals(CanvasMaterialCodec.MAX_MATERIALS, entries.size)
    assertFalse(entries.any { it.name == "m1" })
    assertTrue(entries.any { it.name == "extra" })
  }

  @Test
  fun deleteRemovesById() {
    val a = CanvasMaterialCodec.newEntry("a", CanvasMaterialKind.SELECTION, 1L, JsonArray(listOf(imageItem("a"))))
    val b = CanvasMaterialCodec.newEntry("b", CanvasMaterialKind.SELECTION, 2L, JsonArray(listOf(imageItem("b"))))
    assertEquals(listOf(b), CanvasMaterialCodec.delete(listOf(a, b), a.id))
  }

  @Test
  fun storePersistsWithBackupAndDedup() {
    val project = folder.newFolder("project")
    val entry = CanvasMaterialCodec.newEntry("贴纸组", CanvasMaterialKind.SELECTION, 42L, JsonArray(listOf(imageItem("x"))))

    assertTrue(CanvasMaterialStore.read(project).isEmpty())
    assertTrue(CanvasMaterialStore.write(project, listOf(entry)))
    assertEquals(listOf(entry), CanvasMaterialStore.read(project))
    // 相同内容再写为 no-op。
    assertFalse(CanvasMaterialStore.write(project, listOf(entry)))
    // 修改前备份存在。
    val updated = entry.copy(name = "改名")
    assertTrue(CanvasMaterialStore.write(project, listOf(updated)))
    val history = File(project, "materials-history")
    assertTrue(history.isDirectory && history.listFiles()!!.isNotEmpty())
    assertEquals("改名", CanvasMaterialStore.read(project).single().name)
  }

  @Test
  fun storeReadsMissingFileAsEmpty() {
    val project = folder.newFolder("empty")
    assertTrue(CanvasMaterialStore.read(project).isEmpty())
  }
}
