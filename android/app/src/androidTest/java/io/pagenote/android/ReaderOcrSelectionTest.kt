package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ReaderOcrSelectionTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test fun regionActionEntersSelectionAndCancelRestoresInput() {
    composeRule.setContent {
      MaterialTheme {
        var state by remember { mutableStateOf(ReaderOcrState()) }
        Column {
          Text(state.mode.name, Modifier.testTag("reader_ocr_mode"))
          ReaderOcrActions(
            state = state,
            currentPage = 1,
            onStateChange = { state = it },
            onRecognize = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("region_ocr_action").performClick()
    composeRule.onNodeWithTag("recognize_region_action").assertIsDisplayed()
    composeRule.onNodeWithTag("cancel_region_selection").performClick()

    composeRule.onNodeWithTag("reader_ocr_mode").assertTextEquals("IDLE")
    composeRule.onNodeWithTag("region_ocr_action").assertIsDisplayed()
  }

  @Test fun invalidSelectionShowsRecoverableMessage() {
    composeRule.setContent {
      MaterialTheme {
        var state by remember {
          mutableStateOf(ReaderOcrState().enterSelection(1).withSelection(null))
        }
        Column {
          ReaderOcrActions(
            state = state,
            currentPage = 1,
            onStateChange = { state = it },
            onRecognize = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("ocr_selection_invalid").assertIsDisplayed()
    // Recognize stays disabled until a valid drag replaces the invalid one.
    composeRule.onNodeWithTag("recognize_region_action").assertIsDisplayed()
    composeRule.onNodeWithTag("cancel_region_selection").assertIsDisplayed()
  }

  @Test fun dragSelectsRegionWithoutAddingPenStrokeOrZoom() {
    val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
    var selectedRegion: NormalizedOcrRegion? = null
    var resultingTransform = PdfViewportTransform.DEFAULT
    var addedStrokes = 0
    composeRule.setContent {
      MaterialTheme {
        Column {
          PdfPageViewport(
            bitmap = bitmap,
            sourceName = "test.pdf",
            page = 0,
            loading = false,
            strokes = emptyList(),
            tool = io.pagenote.feature.pen.PenCanvasTool.PEN,
            inputEnabled = false,
            onStrokeAdded = { addedStrokes += 1 },
            onStrokeErased = {},
            transform = PdfViewportTransform.DEFAULT,
            onTransformChanged = { resultingTransform = it },
            onBoundsChanged = {},
            selectionEnabled = true,
            gesturesEnabled = false,
            selection = selectedRegion,
            onSelectionChanged = { selectedRegion = it },
            modifier = Modifier.size(240.dp).testTag("pdf_page_viewport"),
          )
          Text(selectedRegion?.toString() ?: "none", Modifier.testTag("selected_region"))
        }
      }
    }

    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center - Offset(40f, 40f))
      moveTo(0, center + Offset(40f, 40f))
      up(0)
    }

    composeRule.runOnIdle {
      assertNotNull(selectedRegion)
      assertEquals(PdfViewportTransform.DEFAULT, resultingTransform)
      assertEquals(0, addedStrokes)
      bitmap.recycle()
    }
  }

  @Test fun recognizeUsesCurrentSelectionAndResultDismissalRestoresInput() {
    composeRule.setContent {
      MaterialTheme {
        var state by remember {
          mutableStateOf(ReaderOcrState().enterSelection(2).withSelection(NormalizedOcrRegion(100, 120, 600, 700)))
        }
        var recognizedRegion by remember { mutableStateOf<NormalizedOcrRegion?>(null) }
        Column {
          Text(state.mode.name, Modifier.testTag("reader_ocr_mode"))
          ReaderOcrActions(
            state = state,
            currentPage = 2,
            onStateChange = { state = it },
            onRecognize = { request ->
              recognizedRegion = request.selection
              state = request.showResult()
            },
          )
          Text(recognizedRegion?.toString() ?: "no selection", Modifier.testTag("recognized_region"))
        }
        if (state.mode == ReaderOcrMode.RESULT) {
          OcrResultDialog(
            page = state.pageIndex!! + 1,
            result = OcrResult(
              engine = "test-ocr",
              status = "ok",
              blocks = listOf(OcrBlock("result", 0.95, 100.0, 100.0, 200.0, 40.0)),
            ),
            onDismiss = { state = state.dismissResult() },
          )
        }
      }
    }

    composeRule.onNodeWithTag("recognize_region_action").performClick()
    composeRule.onNodeWithTag("reader_ocr_mode").assertTextEquals("RESULT")
    composeRule.onNodeWithTag("recognized_region").assertTextEquals("NormalizedOcrRegion(left=100, top=120, right=600, bottom=700)")
    composeRule.onNodeWithTag("ocr_result_close").performClick()

    composeRule.onNodeWithTag("reader_ocr_mode").assertTextEquals("IDLE")
    composeRule.onNodeWithTag("region_ocr_action").assertIsDisplayed()
  }
}
