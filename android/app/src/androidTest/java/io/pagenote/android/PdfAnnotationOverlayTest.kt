package io.pagenote.android

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
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PdfAnnotationOverlayTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun rectangleUsesPageWidthScaleForVerticalCoordinates() {
    composeRule.setContent {
      Box(Modifier.size(width = 100.dp, height = 200.dp)) {
        PdfAnnotationOverlay(
          items = listOf(
            PdfAnnotationOverlayItem(
              type = "rect",
              points = listOf(100.0 to 100.0, 200.0 to 300.0),
              color = "#00ff00",
              width = 20.0,
              text = null,
            ),
          ),
        )
      }
    }

    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    val x = (pixels.width * 0.15f).toInt()
    val expectedY = (pixels.height * 0.10f).toInt()
    val actualY = (pixels.height * 0.05f).toInt()
    val green = Color(0xFF00FF00)
    assertTrue(
      "expected the rectangle's top edge at y scaled from page width",
      (actualY - 3..actualY + 3).any { y -> pixels[x, y] == green },
    )
    assertTrue(
      "the height-scaled alternative must not contain that edge",
      (expectedY - 3..expectedY + 3).none { y -> pixels[x, y] == green },
    )
  }

  @Test
  fun drawsLineArrowAndEllipseAtDesktopWidthScaledCoordinates() {
    composeRule.setContent {
      Box(Modifier.size(width = 300.dp, height = 400.dp)) {
        PdfAnnotationOverlay(
          items = listOf(
            PdfAnnotationOverlayItem(
              type = "line",
              points = listOf(50.0 to 80.0, 250.0 to 280.0),
              color = "#ff0000",
              width = 10.0,
              text = null,
            ),
            PdfAnnotationOverlayItem(
              type = "arrow",
              points = listOf(350.0 to 100.0, 550.0 to 100.0),
              color = "#0000ff",
              width = 5.0,
              text = null,
            ),
            PdfAnnotationOverlayItem(
              type = "ellipse",
              points = listOf(700.0 to 100.0, 900.0 to 300.0),
              color = "#00ff00",
              width = 8.0,
              text = null,
            ),
          ),
        )
      }
    }

    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    val scale = pixels.width / 1000f
    assertColorNear(pixels, 150f * scale, 180f * scale, Color(0xFFFF0000))
    assertColorNear(pixels, 550f * scale, 100f * scale, Color(0xFF0000FF))
    assertColorNear(pixels, 528.4f * scale, 110.4f * scale, Color(0xFF0000FF))
    assertColorNear(pixels, 528.4f * scale, 89.6f * scale, Color(0xFF0000FF))
    assertColorNear(pixels, 800f * scale, 100f * scale, Color(0xFF00FF00))
  }

  @Test
  fun reloadFindsAnnotationsFileCreatedAfterProjectWasOpened() {
    val context = InstrumentationRegistry.getInstrumentation().context
    val resolver = context.contentResolver
    val treeUri = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, "{}".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, byteArrayOf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, byteArrayOf())
        putBoolean(ExistingProjectSafTestProvider.EXTRA_OMIT_REVIEWS, true)
      },
    )
    val sourceDirectory = DocumentsContract.buildDocumentUriUsingTree(
      treeUri,
      ExistingProjectSafTestProvider.SRC_ID,
    )
    val expected = JsonArray(listOf(Json.parseToJsonElement(
      """{"type":"rect","color":"#112233","width":3,"points":[[0,0],[100,100]]}""",
    )))
    ProjectAnnotationStore(resolver, sourceDirectory, null)
      .writePage("document", 1, expected)

    val reloaded = ProjectAnnotationStore(resolver, sourceDirectory, null)
      .readPage("document", 1)

    assertEquals(expected, reloaded)
  }

  private fun assertColorNear(
    pixels: androidx.compose.ui.graphics.PixelMap,
    x: Float,
    y: Float,
    expected: Color,
  ) {
    val centerX = x.toInt()
    val centerY = y.toInt()
    val matches = (-3..3).any { dy ->
      (-3..3).any { dx -> pixels[centerX + dx, centerY + dy] == expected }
    }
    assertTrue("expected $expected near ($centerX, $centerY)", matches)
  }
}
