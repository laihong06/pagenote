package io.pagenote.android

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val READER_PANE_DIVIDER_WIDTH: Dp = 24.dp

@Composable
internal fun ReaderPaneDivider(
  leftPercent: Float,
  availableWidthPx: Float,
  onPercentChange: (Float) -> Unit,
  onDragFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestPercent by rememberUpdatedState(leftPercent)
  val latestAvailableWidth by rememberUpdatedState(availableWidthPx)
  val latestOnPercentChange by rememberUpdatedState(onPercentChange)
  val latestOnDragFinished by rememberUpdatedState(onDragFinished)
  val description = stringResource(R.string.reader_splitter_adjust)

  Box(
    modifier = modifier
      .width(READER_PANE_DIVIDER_WIDTH)
      .fillMaxHeight()
      .testTag("reader_pane_divider")
      .semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(leftPercent, 20f..80f)
      }
      .pointerInput(Unit) {
        detectDragGestures(
          onDragEnd = { latestOnDragFinished() },
          onDragCancel = { latestOnDragFinished() },
        ) { change, dragAmount ->
          change.consume()
          latestOnPercentChange(
            ReaderPaneSplitPolicy.percentAfterDrag(
              currentPercent = latestPercent,
              deltaXPx = dragAmount.x,
              availableWidthPx = latestAvailableWidth,
            ),
          )
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .width(2.dp)
        .fillMaxHeight()
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.outlineVariant),
    )
  }
}
