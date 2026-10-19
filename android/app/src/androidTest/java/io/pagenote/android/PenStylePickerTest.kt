package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle
import io.pagenote.feature.pen.PenStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenStylePickerTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun swatchCustomRgbAndWidthChoicesApplyToActiveStyle() {
    var color by mutableStateOf("#dc3545")
    var width by mutableStateOf(3.0)
    var showColorPicker by mutableStateOf(false)
    var showWidthPicker by mutableStateOf(false)
    var addedStroke: PenStroke? = null
    val pageBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    composeRule.setContent {
      MaterialTheme {
        Column {
          Button(onClick = { showColorPicker = true }) { Text("Open color") }
          Button(onClick = { showWidthPicker = true }) { Text("Open width") }
          Text(color, Modifier.testTag("pen_style_color_value"))
          Text(width.toString(), Modifier.testTag("pen_style_width_value"))
          PdfPageViewport(
            bitmap = pageBitmap,
            sourceName = "test.pdf",
            page = 0,
            loading = false,
            strokes = emptyList(),
            tool = PenCanvasTool.PEN,
            inputEnabled = true,
            onStrokeAdded = { addedStroke = it },
            onStrokeErased = {},
            transform = PdfViewportTransform.DEFAULT,
            onTransformChanged = {},
            onBoundsChanged = {},
            gesturesEnabled = true,
            penStyle = PenStyle.create(color, width) ?: PenStyle.DEFAULT,
            modifier = Modifier.size(120.dp).testTag("style_pdf_page"),
          )
        }
        if (showColorPicker) {
          InkColorPickerDialog(
            currentColor = color,
            onApply = { color = it; showColorPicker = false },
            onDismiss = { showColorPicker = false },
          )
        }
        if (showWidthPicker) {
          InkWidthPickerDialog(
            currentWidth = width,
            onApply = { width = it; showWidthPicker = false },
            onDismiss = { showWidthPicker = false },
          )
        }
      }
    }

    composeRule.onNodeWithTag("pen_style_color_value").assertTextEquals("#dc3545")
    composeRule.onNodeWithTag("pen_style_width_value").assertTextEquals("3.0")

    composeRule.onNodeWithText("Open color").performClick()
    composeRule.onNodeWithTag("ink_color_swatch_blue").performClick()
    composeRule.onNodeWithTag("ink_color_apply").performClick()
    composeRule.onNodeWithTag("pen_style_color_value").assertTextEquals("#185abd")

    composeRule.onNodeWithText("Open color").performClick()
    composeRule.onNodeWithTag("ink_color_hex").performTextReplacement("#12AB34")
    composeRule.onNodeWithTag("ink_color_apply").performClick()
    composeRule.onNodeWithTag("pen_style_color_value").assertTextEquals("#12ab34")

    composeRule.onNodeWithText("Open width").performClick()
    composeRule.onNodeWithTag("ink_width_10").performClick()
    composeRule.onNodeWithTag("ink_width_apply").performClick()
    composeRule.onNodeWithTag("pen_style_width_value").assertTextEquals("10.0")

    composeRule.onNodeWithTag("style_pdf_page").performTouchInput {
      down(0, center - Offset(20f, 20f))
      moveTo(0, center + Offset(20f, 20f))
      up(0)
    }

    composeRule.runOnIdle {
      assertNotNull(addedStroke)
      assertEquals("#12ab34", addedStroke?.color)
      assertEquals(10.0, addedStroke?.width ?: 0.0, 0.0)
      pageBitmap.recycle()
    }
  }
}
