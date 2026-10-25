package io.pagenote.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.pagenote.core.search.SearchHit
import io.pagenote.core.search.SearchQueryPolicy

/** 搜索焦点高亮（结果跳转后的 rect 定位）：与 OCR 选区覆盖层同一归一化语义。 */
@Composable
internal fun SearchFocusOverlay(
  rect: NormalizedOcrRegion?,
  modifier: Modifier = Modifier,
) {
  if (rect == null) return
  val color = MaterialTheme.colorScheme.tertiary
  Canvas(modifier = modifier.fillMaxSize()) {
    val left = rect.left / 1000f * size.width
    val top = rect.top / 1000f * size.height
    val right = rect.right / 1000f * size.width
    val bottom = rect.bottom / 1000f * size.height
    drawRect(
      color = color.copy(alpha = 0.20f),
      topLeft = Offset(left, top),
      size = Size(right - left, bottom - top),
    )
    drawRect(
      color = color,
      topLeft = Offset(left, top),
      size = Size(right - left, bottom - top),
      style = Stroke(width = 2.dp.toPx()),
    )
  }
}

@Composable
internal fun SearchDialog(
  query: String,
  results: List<SearchHit>,
  isSearching: Boolean,
  error: String?,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  onJump: (SearchHit) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    modifier = Modifier.testTag("search_dialog"),
    onDismissRequest = onDismiss,
    properties = DialogProperties(dismissOnClickOutside = false),
    title = { Text(stringResource(R.string.search_dialog_title)) },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).testTag("search_input"),
            label = { Text(stringResource(R.string.search_input_label)) },
            singleLine = true,
          )
          TextButton(
            modifier = Modifier.testTag("search_run"),
            enabled = !isSearching && query.trim().isNotEmpty() &&
              query.trim().length <= SearchQueryPolicy.MAX_QUERY_LENGTH,
            onClick = onSearch,
          ) {
            Text(stringResource(R.string.search_run))
          }
          if (isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.testTag("search_progress").padding(4.dp),
              strokeWidth = 2.dp,
            )
          }
        }
        error?.let {
          Text(
            text = it,
            modifier = Modifier.testTag("search_error").padding(top = 8.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (!isSearching && error == null && results.isEmpty()) {
          Text(
            text = stringResource(R.string.search_no_results),
            modifier = Modifier.testTag("search_no_results").padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("search_results"),
        ) {
          items(results, key = { result -> result.page to result.kind }) { result ->
            SearchResultItem(
              hit = result,
              onClick = { onJump(result) },
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(
        modifier = Modifier.testTag("search_close"),
        onClick = onDismiss,
      ) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun SearchResultItem(
  hit: SearchHit,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("search_result_item")
      .padding(vertical = 6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.weight(1f)) {
        TextButton(onClick = onClick) {
          Text(
            text = stringResource(R.string.search_result_page, hit.page),
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }
      Text(
        text = stringResource(searchKindLabelRes(hit.kind)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      text = hit.snippet,
      modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun searchKindLabelRes(kind: io.pagenote.core.search.SearchDocumentKind): Int = when (kind) {
  io.pagenote.core.search.SearchDocumentKind.SOURCE -> R.string.search_kind_source
  io.pagenote.core.search.SearchDocumentKind.NOTE -> R.string.search_kind_note
  io.pagenote.core.search.SearchDocumentKind.OCR -> R.string.search_kind_ocr
  io.pagenote.core.search.SearchDocumentKind.TRANSCRIPT -> R.string.search_kind_transcript
}
