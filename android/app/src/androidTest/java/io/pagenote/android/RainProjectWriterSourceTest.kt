package io.pagenote.android

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.yuketang.RainCourse
import io.pagenote.feature.yuketang.RainLesson
import io.pagenote.feature.yuketang.RainSlide
import io.pagenote.feature.yuketang.RainSlideImage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class RainProjectWriterSourceTest {
  @Test
  fun reportImportStoresActualDeckIdsInSourceManifest() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    val bytes = try {
      ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
      }
    } finally { bitmap.recycle() }
    var root: Uri? = null
    try {
      val target = RainProjectWriter(context).create(
        ProjectCreateSafTestProvider.treeUri(), "rain-source-test-${UUID.randomUUID()}",
        RainCourse("course", "room", "Course"), RainLesson("room", "lesson", "report-lesson", "Lecture"),
      ) { emit ->
        for (deck in listOf("deck-a", "deck-b")) {
          emit(RainSlideImage(RainSlide("page-$deck", 0, deck, "https://qn-scd1.yuketang.cn/page"), "image/png", bytes))
        }
      }
      root = DocumentsContract.buildDocumentUriUsingTree(target.accessTreeUri, target.rootDocumentId)
      val access = SafPreparedDocumentAccess(resolver)
      val src = checkNotNull(access.findChild(root.toString(), "src"))
      val source = Uri.parse(checkNotNull(access.findChild(src, "yuketang-source.json")))
      val manifest = resolver.openInputStream(source)!!.bufferedReader().use { JSONObject(it.readText()) }
      assertEquals("deck-a", manifest.getString("presentationId"))
      val pages = manifest.getJSONArray("pages")
      assertEquals(listOf("deck-a", "deck-b"), (0 until pages.length()).map { pages.getJSONObject(it).getString("presentationId") })
    } finally { root?.let { DocumentsContract.deleteDocument(resolver, it) } }
  }
}
