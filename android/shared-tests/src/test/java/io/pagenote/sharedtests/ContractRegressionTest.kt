package io.pagenote.sharedtests

import io.pagenote.core.project.PageNotes
import io.pagenote.core.project.ProjectManifest
import io.pagenote.core.project.ProjectValidation
import io.pagenote.core.project.ProjectAssets
import io.pagenote.core.project.ProjectStore
import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PreparedCache
import io.pagenote.core.reader.PreparedManifest
import io.pagenote.core.reader.PreparedManifestStore
import io.pagenote.feature.annotations.AnnotationStore
import io.pagenote.feature.annotations.ReviewStore
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContractRegressionTest {
  private val json = Json { ignoreUnknownKeys = true }

  private fun fixture(name: String): File {
    val userDir = File(System.getProperty("user.dir"))
    var dir: File? = userDir
    while (dir != null) {
      val candidate = File(dir, "android/shared-tests/fixtures/$name")
      if (candidate.exists()) return candidate
      val direct = File(dir, "shared-tests/fixtures/$name")
      if (direct.exists()) return direct
      dir = dir.parentFile
    }
    val fallback = File(userDir, "src/test/fixtures/$name")
    if (fallback.exists()) return fallback
    throw IllegalStateException("fixture missing: $name (cwd=${userDir.absolutePath})")
  }

  @Test
  fun project_manifest_matches_contract() {
    val root = json.parseToJsonElement(fixture("project-minimal.json").readText()).jsonObject
    val manifest = ProjectManifest(
      format = root["format"]!!.jsonPrimitive.content,
      version = root["version"]!!.jsonPrimitive.content.toInt(),
      id = root["id"]!!.jsonPrimitive.content,
      name = root["name"]!!.jsonPrimitive.content,
      left = root["left"]!!.jsonPrimitive.content,
      right = root["right"]!!.jsonPrimitive.content,
    )
    ProjectValidation.validate(manifest)
  }

  @Test
  fun multipage_notes_keep_empty_pages() {
    val pages = PageNotes.split(fixture("right-multipage.md").readText())
    assertEquals(3, pages.size)
    assertEquals("# p1 note", pages[0])
    assertEquals("# p2 note", pages[1])
    // 尾部分隔符后的换行留在页内（桌面 split 不做 trim），只断言空页语义。
    assertTrue(pages[2].isBlank())
    assertEquals(pages, PageNotes.split(PageNotes.join(pages)))
  }

  @Test
  fun annotations_fixture_round_trips() {
    val dir = createTempDir("pagenote-contract")
    val root = json.parseToJsonElement(fixture("annotations-sample.json").readText()).jsonObject
    val documentItems = root["document"]!!.jsonObject["1"]!!.jsonArray
    val noteItems = root["note"]!!.jsonObject["2"]!!.jsonArray
    assertTrue(AnnotationStore.writePage(dir, "document", 1, documentItems))
    assertTrue(AnnotationStore.writePage(dir, "note", 2, noteItems))
    val file = File(dir, "annotations.json")
    assertEquals(documentItems, AnnotationStore.readPage(file, "document", 1))
    assertEquals(noteItems, AnnotationStore.readPage(file, "note", 2))
  }

  @Test
  fun reviews_fixture_creates_open_record() {
    val dir = createTempDir("pagenote-reviews")
    val sample = json.parseToJsonElement(fixture("ai-reviews-sample.json").readText()).jsonArray[0].jsonObject
    val record = ReviewStore.create(
      dir,
      sample["side"]!!.jsonPrimitive.content,
      sample["kind"]!!.jsonPrimitive.content,
      sample["page"]!!.jsonPrimitive.content.toInt(),
      sample["instruction"]!!.jsonPrimitive.content,
    )
    assertEquals("open", record["status"]!!.jsonPrimitive.content)
    assertEquals(1, record["number"]!!.jsonPrimitive.content.toInt())
  }

  @Test
  fun prepared_manifest_fixture_validates() {
    val root = json.parseToJsonElement(fixture("prepared-manifest-sample.json").readText()).jsonObject
    val region = root["region"]!!.jsonObject
    val manifest = PreparedManifest(
      version = root["version"]!!.jsonPrimitive.content.toInt(),
      documentId = root["documentId"]!!.jsonPrimitive.content,
      page = root["page"]!!.jsonPrimitive.content.toInt(),
      sourceRevision = root["sourceRevision"]!!.jsonPrimitive.content,
      kind = root["kind"]!!.jsonPrimitive.content,
      region = PageRegion(
        region["x"]!!.jsonPrimitive.content.toDouble(),
        region["y"]!!.jsonPrimitive.content.toDouble(),
        region["width"]!!.jsonPrimitive.content.toDouble(),
        region["height"]!!.jsonPrimitive.content.toDouble(),
      ),
      cacheKey = root["cacheKey"]!!.jsonPrimitive.content,
      width = root["width"]!!.jsonPrimitive.content.toInt(),
      height = root["height"]!!.jsonPrimitive.content.toInt(),
      scale = root["scale"]!!.jsonPrimitive.content.toDouble(),
    )
    PreparedManifestStore.validate(manifest)
    assertTrue(PreparedManifestStore.isHit(manifest, manifest.sourceRevision, manifest.cacheKey))
    PreparedCache.validateRegion(manifest.region)
  }

  @Test
  fun assets_export_skips_missing_but_rewrites() {
    val uploads = createTempDir("pagenote-assets-uploads")
    val projectRoot = createTempDir("pagenote-assets-project")
    val present = "123e4567-e89b-12d3-a456-426614174000.png"
    File(uploads, present).writeBytes(byteArrayOf(1, 2, 3))
    val text = fixture("asset-note.md").readText()

    val exported = ProjectAssets.exportAttachments(text, uploads, projectRoot)

    assertFalse(exported.contains("/attachments/"))
    assertTrue(exported.contains("assets/$present"))
    assertTrue(exported.contains("assets/123e4567-e89b-12d3-a456-426614174001.png"))
    assertTrue(File(projectRoot, "src/assets/$present").exists())
    assertFalse(File(projectRoot, "src/assets/123e4567-e89b-12d3-a456-426614174001.png").exists())
  }

  @Test
  fun assets_import_skips_missing_but_rewrites() {
    val uploads = createTempDir("pagenote-assets-uploads-in")
    val projectRoot = createTempDir("pagenote-assets-project-in")
    val present = "123e4567-e89b-12d3-a456-426614174000.png"
    File(projectRoot, "src/assets").mkdirs()
    File(projectRoot, "src/assets/$present").writeBytes(byteArrayOf(4, 5))
    val text = "![a](assets/$present)\n\n![b](assets/123e4567-e89b-12d3-a456-426614174001.png)\n"

    val imported = ProjectAssets.importAssets(text, projectRoot, uploads)

    assertFalse(imported.contains("assets/$present"))
    assertTrue(imported.contains("/attachments/$present"))
    assertTrue(imported.contains("/attachments/123e4567-e89b-12d3-a456-426614174001.png"))
    assertTrue(File(uploads, present).exists())
    assertFalse(File(uploads, "123e4567-e89b-12d3-a456-426614174001.png").exists())
  }

  @Test
  fun save_note_rejects_stale_expected_revision() {
    val dir = createTempDir("pagenote-revision-conflict")
    val right = File(dir, "right.md")
    right.writeText(fixture("revision-conflict.md").readText(), Charsets.UTF_8)
    val stale = PageNotes.revision(PageNotes.split(right.readText())[0])

    ProjectStore.saveNote(right, 1, "# conflict p1 edited", stale)
    assertEquals("# conflict p1 edited", ProjectStore.readNotes(right)[1])
    try {
      ProjectStore.saveNote(right, 1, "# stale overwrite", stale)
      fail("expected conflict")
    } catch (_: IllegalStateException) {
    }
    assertEquals("# conflict p1 edited", ProjectStore.readNotes(right)[1])
  }
}
