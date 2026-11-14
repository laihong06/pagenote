package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle
import io.pagenote.feature.pen.PenStroke
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPenRailInteractionTest {
  @get:Rule val compose = createComposeRule()

  @Test fun penBoxAppliesExistingToolAndColor() {
    var selectedTool = PenCanvasTool.PEN
    var selectedColor = ""
    compose.setContent {
      MaterialTheme {
        ReaderPenRail(tool = PenCanvasTool.PEN, drawTool = null, style = PenStyle.DEFAULT,
          enabled = true, selectionEnabled = true, onTool = { selectedTool = it }, onShape = {},
          onColor = { selectedColor = it }, onWidth = {}, onCustomColor = {})
      }
    }
    compose.onNodeWithTag("reader_pen_box").performClick()
    compose.onNodeWithTag("reader_pen_box_ballpoint").performClick()
    compose.runOnIdle { assertEquals(PenCanvasTool.BALLPOINT, selectedTool) }
    compose.onNodeWithTag("reader_pen_box").performClick()
    compose.onNodeWithTag("reader_pen_box_color_185abd").performClick()
    compose.runOnIdle { assertEquals("#185abd", selectedColor) }
  }

  @Test fun penBoxCustomWidthAppliesBetweenPresets() {
    var style by mutableStateOf(PenStyle.DEFAULT)
    var stroke: PenStroke? = null
    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    compose.setContent {
      MaterialTheme {
        Row {
          ReaderPenRail(tool = PenCanvasTool.PEN, drawTool = null, style = style,
            enabled = true, selectionEnabled = true, onTool = {}, onShape = {}, onColor = {},
            onWidth = { style = requireNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {})
          PdfPageViewport(bitmap = bitmap, sourceName = "test.pdf", page = 0, loading = false,
            strokes = emptyList(), tool = PenCanvasTool.PEN, inputEnabled = true,
            onStrokeAdded = { stroke = it }, onStrokeErased = {},
            transform = PdfViewportTransform.DEFAULT, onTransformChanged = {}, onBoundsChanged = {},
            penStyle = style, modifier = Modifier.size(240.dp).testTag("width_test_page"))
        }
      }
    }
    compose.onNodeWithTag("reader_pen_box").performClick()
    compose.onNodeWithTag("reader_pen_box_width_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(4.7f) }
    compose.runOnIdle { assertEquals(4.7, style.width, 0.11) }
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("width_test_page").performTouchInput {
      down(0, center - Offset(20f, 20f)); moveTo(0, center + Offset(20f, 20f)); up(0)
    }
    compose.runOnIdle {
      assertEquals(4.7, requireNotNull(stroke).width, 0.11)
      bitmap.recycle()
    }
  }

  @Test fun penArtworkDoesNotDrawOutsideTheRail() {
    compose.setContent {
      MaterialTheme {
        Row(Modifier.size(100.dp, 300.dp).background(Color.Magenta).testTag("rail_test_surface")) {
          Spacer(Modifier.width(30.dp))
          ReaderPenRail(tool = PenCanvasTool.PEN, drawTool = null, style = PenStyle.DEFAULT,
            enabled = true, selectionEnabled = true, onTool = {}, onShape = {}, onColor = {},
            onWidth = {}, onCustomColor = {})
        }
      }
    }
    val density = compose.density.density
    val pixels = compose.onNodeWithTag("rail_test_surface").captureToImage().toPixelMap()
    assertEquals(Color.Magenta, pixels[(27 * density).toInt(), (92 * density).toInt()])
  }

  @Test fun collapsedToolboxCanBeExpandedAgain() {
    compose.setContent {
      MaterialTheme {
        ReaderPenRail(tool = PenCanvasTool.PEN, drawTool = null, style = PenStyle.DEFAULT,
          enabled = true, selectionEnabled = true, onTool = {}, onShape = {}, onColor = {},
          onWidth = {}, onCustomColor = {})
      }
    }
    compose.onNodeWithTag("reader_pen_collapse").performClick()
    compose.onNodeWithTag("reader_pen_pen").assertDoesNotExist()
    compose.onNodeWithTag("reader_pen_expand").performClick()
    compose.onNodeWithTag("reader_pen_pen").assertIsDisplayed()
  }

  @Test fun longPressPenOpensToolStyle() {
    compose.setContent {
      MaterialTheme {
        ReaderPenRail(tool = PenCanvasTool.PEN, drawTool = null, style = PenStyle.DEFAULT,
          enabled = true, selectionEnabled = true, onTool = {}, onShape = {}, onColor = {},
          onWidth = {}, onCustomColor = {})
      }
    }
    compose.onNodeWithTag("reader_pen_pen").performTouchInput { longClick() }
    compose.onNodeWithTag("reader_pen_style_menu").assertIsDisplayed()
  }
}
