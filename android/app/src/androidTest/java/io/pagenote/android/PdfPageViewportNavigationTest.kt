package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.pen.PenCanvasTool
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPageViewportNavigationTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun verticalTouchSwipesNavigateOnePageAndHorizontalDragDoesNot() {
    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    var direction: PdfPageSwipeDirection? = null

    composeRule.setContent {
      MaterialTheme {
        PdfPageViewport(
          bitmap = bitmap,
          sourceName = "test.pdf",
          page = 0,
          loading = false,
          strokes = emptyList(),
          tool = PenCanvasTool.PEN,
          inputEnabled = false,
          onStrokeAdded = {},
          onStrokeErased = {},
          transform = PdfViewportTransform.DEFAULT,
          onTransformChanged = {},
          onBoundsChanged = {},
          gesturesEnabled = true,
          pageSwipesEnabled = true,
          onPageSwipe = { direction = it },
          modifier = Modifier.size(240.dp).testTag("pdf_page_viewport"),
        )
      }
    }

    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center.copy(y = center.y + height * 0.35f))
      moveTo(0, center.copy(y = center.y - height * 0.35f), delayMillis = 60)
      up(0)
    }
    composeRule.waitForIdle()
    assertEquals(PdfPageSwipeDirection.NEXT, direction)

    direction = null
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center.copy(x = center.x - 60f))
      moveTo(0, center.copy(x = center.x + 60f), delayMillis = 60)
      up(0)
    }
    composeRule.waitForIdle()
    assertEquals(null, direction)
    bitmap.recycle()
  }
}
