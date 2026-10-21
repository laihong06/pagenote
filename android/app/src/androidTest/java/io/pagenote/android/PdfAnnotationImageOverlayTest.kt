package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfAnnotationImageOverlayTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val resolver get() = context.contentResolver

  @Test
  fun loadsImageThroughProjectSafAndReturnsNullForMissingOrUnreadableAssets() {
    resetProvider(NoteAssetSafTestProvider.FAILURE_NONE)
    val sourceUri = projectSourceUri()
    val loader = PdfAnnotationImageLoader(resolver, sourceUri)

    val bitmap = loader.load(NoteAssetSafTestProvider.IMAGE_NAME)
    assertNotNull(bitmap)
    assertEquals(AndroidColor.MAGENTA, requireNotNull(bitmap).getPixel(0, 0))
    assertNull(loader.load("01234567-89ab-cdef-0123-456789abcdee.png"))

    resetProvider(NoteAssetSafTestProvider.FAILURE_OPEN)
    assertNull(PdfAnnotationImageLoader(resolver, sourceUri).load(NoteAssetSafTestProvider.IMAGE_NAME))
  }

  @Test
  fun drawsBoundedImageAtPageWidthCoordinatesAlongsideVectorAndMissingFallback() {
    val assetName = "01234567-89ab-cdef-0123-456789abcdef.png"
    val image = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
      eraseColor(AndroidColor.MAGENTA)
    }
    composeRule.setContent {
      Box(Modifier.size(100.dp)) {
        PdfAnnotationOverlay(
          items = listOf(
            PdfAnnotationOverlayItem(
              type = "image",
              points = listOf(80.0 to 100.0, 430.0 to 275.0),
              color = "#185abd",
              width = 3.0,
              text = null,
              imageAssetName = assetName,
            ),
            PdfAnnotationOverlayItem(
              type = "line",
              points = listOf(100.0 to 400.0, 400.0 to 400.0),
              color = "#00ff00",
              width = 20.0,
              text = null,
            ),
            PdfAnnotationOverlayItem(
              type = "image",
              points = listOf(550.0 to 100.0, 750.0 to 300.0),
              color = "#185abd",
              width = 3.0,
              text = null,
              imageAssetName = "01234567-89ab-cdef-0123-456789abcdee.png",
            ),
          ),
          images = mapOf(assetName to image),
        )
      }
    }

    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    val scale = pixels.width / 1000f
    assertColorNear(pixels, 200f * scale, 180f * scale, Color(0xFFFF00FF))
    assertTrue(
      "the image keeps its 2:1 pixel aspect ratio in page-width coordinates",
      !nearColor(pixels, 200f * scale, 320f * scale, Color(0xFFFF00FF)),
    )
    assertTrue(
      "expected the existing vector line to remain visible",
      nearGreen(pixels, 250f * scale, 400f * scale),
    )
    assertColorNear(pixels, 570f * scale, 120f * scale, Color(0xFFFFF4D6))
  }

  private fun assertColorNear(
    pixels: androidx.compose.ui.graphics.PixelMap,
    x: Float,
    y: Float,
    expected: Color,
  ) {
    assertTrue("expected $expected near (${x.toInt()}, ${y.toInt()})", nearColor(pixels, x, y, expected))
  }

  private fun nearColor(
    pixels: androidx.compose.ui.graphics.PixelMap,
    x: Float,
    y: Float,
    expected: Color,
  ): Boolean {
    val centerX = x.toInt()
    val centerY = y.toInt()
    return (-3..3).any { dy ->
      (-3..3).any { dx ->
        centerX + dx in 0 until pixels.width && centerY + dy in 0 until pixels.height &&
          pixels[centerX + dx, centerY + dy] == expected
      }
    }
  }

  private fun nearGreen(
    pixels: androidx.compose.ui.graphics.PixelMap,
    x: Float,
    y: Float,
  ): Boolean {
    val centerX = x.toInt()
    val centerY = y.toInt()
    return (-4..4).any { dy ->
      (-4..4).any { dx ->
        val px = centerX + dx
        val py = centerY + dy
        if (px !in 0 until pixels.width || py !in 0 until pixels.height) {
          false
        } else {
          val color = pixels[px, py]
          color.green > 0.8f && color.red < 0.2f && color.blue < 0.2f
        }
      }
    }
  }

  private fun projectSourceUri(): android.net.Uri {
    val tree = DocumentsContract.buildTreeDocumentUri(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.TREE_DOCUMENT_ID,
    )
    return DocumentsContract.buildDocumentUriUsingTree(
      tree,
      NoteAssetSafTestProvider.SOURCE_DOCUMENT_ID,
    )
  }

  private fun resetProvider(failureMode: String) {
    resolver.call(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.METHOD_RESET,
      null,
      Bundle().apply { putString(NoteAssetSafTestProvider.EXTRA_FAILURE_MODE, failureMode) },
    )
  }
}
