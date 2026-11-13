package io.pagenote.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

internal data class PdfPageNavigationRequest(
  val pageIndex: Int,
  val revision: Int,
)

@Composable
internal fun PdfContinuousPageList(
  pageCount: Int,
  request: PdfPageNavigationRequest?,
  onActivePageChanged: (Int) -> Unit,
  pageContent: @Composable LazyItemScope.(Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  require(pageCount >= 0) { "page count must not be negative" }
  val initialPageIndex = request?.pageIndex
    ?.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    ?: 0
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
  val latestOnActivePageChanged by rememberUpdatedState(onActivePageChanged)
  var appliedRequestRevision by remember { mutableIntStateOf(Int.MIN_VALUE) }

  LaunchedEffect(listState, pageCount) {
    snapshotFlow {
      val layout = listState.layoutInfo
      val viewportCenterPx = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
      PdfContinuousPagePolicy.activePage(
        visiblePages = layout.visibleItemsInfo.map { item ->
          PdfVisiblePage(pageIndex = item.index, topPx = item.offset, heightPx = item.size)
        },
        viewportCenterPx = viewportCenterPx,
      )
    }
      .filterNotNull()
      .distinctUntilChanged()
      .collect(latestOnActivePageChanged)
  }

  LaunchedEffect(request?.revision, pageCount) {
    val navigation = request ?: return@LaunchedEffect
    if (pageCount == 0 || navigation.revision == appliedRequestRevision) return@LaunchedEffect
    appliedRequestRevision = navigation.revision
    listState.animateScrollToItem(navigation.pageIndex.coerceIn(0, pageCount - 1))
  }

  LazyColumn(
    state = listState,
    modifier = modifier.testTag(TEST_TAG),
    contentPadding = PaddingValues(vertical = 0.dp),
    verticalArrangement = Arrangement.spacedBy(PAGE_SPACING_DP.dp),
  ) {
    items(
      count = pageCount,
      key = { pageIndex -> "pdf-page-$pageIndex" },
      contentType = { "pdf-page" },
    ) { pageIndex ->
      Box(Modifier.fillMaxWidth().testTag(pageTag(pageIndex))) {
        pageContent(pageIndex)
      }
    }
  }
}

internal const val PDF_CONTINUOUS_PAGE_LIST_TAG = "pdf_continuous_page_list"
private const val TEST_TAG = PDF_CONTINUOUS_PAGE_LIST_TAG
private const val PAGE_SPACING_DP = 2.2f
internal fun pdfContinuousPageTag(pageIndex: Int): String = "pdf_continuous_page_$pageIndex"
private fun pageTag(pageIndex: Int): String = pdfContinuousPageTag(pageIndex)
