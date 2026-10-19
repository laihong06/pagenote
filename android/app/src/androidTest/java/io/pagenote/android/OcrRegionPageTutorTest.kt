package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrRegionPageTutorTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun usableRegionResultOffersPageTutorAction() {
    var explainCalls = 0
    composeRule.setContent {
      OcrResultDialog(
        page = 4,
        result = OcrResult(
          engine = "test-ocr",
          status = "ok",
          blocks = listOf(OcrBlock("selected region text", 0.94, 100.0, 120.0, 300.0, 80.0)),
        ),
        onDismiss = {},
        onExplainRegion = { explainCalls += 1 },
      )
    }

    composeRule.onNodeWithTag("ocr_result_explain_region")
      .assertIsDisplayed()
      .assertIsEnabled()
    composeRule.onNodeWithTag("ocr_result_explain_region").performClick()
    assertEquals(1, explainCalls)
  }

  @Test
  fun failedResultDoesNotOfferPageTutorAction() {
    composeRule.setContent {
      OcrResultDialog(
        page = 4,
        result = OcrResult(engine = "test-ocr", status = "failed"),
        onDismiss = {},
        onExplainRegion = {},
      )
    }
    assertTrue(composeRule.onAllNodesWithTag("ocr_result_explain_region").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun emptyPartialResultDoesNotOfferPageTutorAction() {
    composeRule.setContent {
      OcrResultDialog(
        page = 4,
        result = OcrResult(
          engine = "test-ocr",
          status = "partial",
          blocks = listOf(OcrBlock("  ", 0.2, 100.0, 120.0, 300.0, 80.0)),
        ),
        onDismiss = {},
        onExplainRegion = {},
      )
    }
    assertTrue(composeRule.onAllNodesWithTag("ocr_result_explain_region").fetchSemanticsNodes().isEmpty())
  }
}
