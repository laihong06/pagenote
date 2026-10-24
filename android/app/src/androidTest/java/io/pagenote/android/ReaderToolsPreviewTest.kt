package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import java.util.Locale
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderToolsPreviewTest {
  @get:Rule val compose = createComposeRule()

  @Test fun railIs44DpAndSwitchesNativeToolsAndShapes() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var shape by mutableStateOf<PdfAnnotationDrawTool?>(null)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        ReaderPenRail(tool, shape, style, true, true,
          onTool = { tool = it; shape = null }, onShape = { shape = it },
          onColor = { style = checkNotNull(PenStyle.create(it, style.width)) },
          onWidth = { style = checkNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {})
      }
    }
    compose.onNodeWithTag("reader_pen_rail").assertWidthIsEqualTo(44.dp)
    compose.onNodeWithTag("reader_pen_ballpoint").performClick().assertIsSelected()
    compose.runOnIdle { assertEquals(PenCanvasTool.BALLPOINT, tool) }
    compose.onNodeWithTag("reader_pen_laser").performClick().assertIsSelected()
    compose.onNodeWithTag("reader_pen_preset_6.0").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(6.0, style.width, 0.0) }
    compose.onNodeWithTag("reader_pen_color_185abd").performScrollTo().performClick()
    compose.runOnIdle { assertEquals("#185abd", style.color) }
    compose.onNodeWithTag("reader_pen_shapes").performScrollTo().performClick()
    compose.onNodeWithTag("reader_pen_shape_ellipse").performClick()
    compose.runOnIdle { assertEquals(PdfAnnotationDrawTool.ELLIPSE, shape) }
  }

  @Test fun previewDrawerSelectsPagesAndDismissesOnOutsideTap() {
    var show by mutableStateOf(true)
    var selected = -1
    compose.setContent {
      MaterialTheme {
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
        val context = LocalContext.current.createConfigurationContext(configuration)
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
        if (show) PagePreviewDrawer(null, PageNotes.join(listOf("First", "Second", "Third")),
          3, 1, false, onDismiss = { show = false }, onSelect = { selected = it; show = false })
        }
      }
    }
    compose.onNodeWithText("页面预览").assertIsDisplayed()
    compose.onNodeWithTag("page_preview_item_1").assertIsSelected()
    compose.onNodeWithTag("page_preview_item_2").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(2, selected); show = true }
    compose.onNodeWithTag("page_preview_scrim").performTouchInput { click(Offset(8f, height / 2f)) }
    compose.onNodeWithTag("page_preview_drawer").assertDoesNotExist()
  }
}
