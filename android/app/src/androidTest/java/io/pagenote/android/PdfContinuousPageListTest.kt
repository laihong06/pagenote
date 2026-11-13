package io.pagenote.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class PdfContinuousPageListTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun continuousListFlingUpdatesCenteredPageWithoutPageSnapping() {
    val activePage = AtomicInteger(-1)
    val firstPageTop = AtomicInteger(Int.MIN_VALUE)

    composeRule.setContent {
      MaterialTheme {
        PdfContinuousPageList(
          pageCount = 12,
          request = PdfPageNavigationRequest(pageIndex = 0, revision = 0),
          onActivePageChanged = activePage::set,
          modifier = Modifier.size(width = 320.dp, height = 300.dp),
          pageContent = { pageIndex ->
            Box(
              Modifier.fillMaxWidth().height(500.dp)
                .then(
                  if (pageIndex == 0) {
                    Modifier.onGloballyPositioned { firstPageTop.set(it.positionInRoot().y.roundToInt()) }
                  } else {
                    Modifier
                  },
                )
                .background(Color.White),
            ) {
              Text("Page ${pageIndex + 1}")
            }
          },
        )
      }
    }
    composeRule.waitForIdle()
    val initialTop = firstPageTop.get()

    composeRule.onNodeWithTag("pdf_continuous_page_list").performTouchInput {
      down(center.copy(y = height * 0.75f))
      moveTo(0, center.copy(y = height * 0.55f), delayMillis = 300)
      advanceEventTime(200)
      up(0)
    }
    composeRule.waitForIdle()
    val movedTop = firstPageTop.get()
    val pageHeightPx = with(composeRule.density) { 500.dp.toPx().roundToInt() }
    assertTrue("short drag should scroll inside the current page", movedTop < initialTop - 10)
    assertTrue("short drag should not snap to the next page", movedTop > initialTop - pageHeightPx * 0.8f)

    repeat(4) {
      composeRule.onNodeWithTag("pdf_continuous_page_list").performTouchInput { swipeUp() }
      composeRule.waitForIdle()
    }
    assertTrue("active page should advance across several list items", activePage.get() >= 2)
  }

  @Test
  fun continuousListKeepsMixedPageHeightsAndAspectRatios() {
    val measuredHeights = ConcurrentHashMap<Int, Int>()
    composeRule.setContent {
      MaterialTheme {
        PdfContinuousPageList(
          pageCount = 4,
          request = PdfPageNavigationRequest(pageIndex = 0, revision = 0),
          onActivePageChanged = {},
          modifier = Modifier.size(width = 320.dp, height = 700.dp),
          pageContent = { pageIndex ->
            val pageHeight = if (pageIndex % 2 == 0) 180.dp else 260.dp
            Box(
              Modifier.fillMaxWidth().height(pageHeight)
                .onSizeChanged { measuredHeights[pageIndex] = it.height }
                .background(Color.White),
            )
          },
        )
      }
    }
    composeRule.waitForIdle()

    assertEquals(with(composeRule.density) { 180.dp.toPx().roundToInt() }, measuredHeights[0])
    assertEquals(with(composeRule.density) { 260.dp.toPx().roundToInt() }, measuredHeights[1])
    assertTrue(measuredHeights[0] != measuredHeights[1])
  }

  @Test
  fun changedNavigationRequestBringsItsTargetPageIntoView() {
    var request by mutableStateOf(PdfPageNavigationRequest(pageIndex = 0, revision = 0))
    val activePage = AtomicInteger(-1)
    composeRule.setContent {
      MaterialTheme {
        PdfContinuousPageList(
          pageCount = 10,
          request = request,
          onActivePageChanged = activePage::set,
          modifier = Modifier.size(width = 320.dp, height = 300.dp),
          pageContent = { pageIndex ->
            Box(Modifier.fillMaxWidth().height(500.dp).background(Color.White)) {
              Text("Page ${pageIndex + 1}")
            }
          },
        )
      }
    }
    composeRule.waitForIdle()

    composeRule.runOnIdle { request = PdfPageNavigationRequest(pageIndex = 8, revision = 1) }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("pdf_continuous_page_8").assertIsDisplayed()
    assertEquals(8, activePage.get())
  }
}
