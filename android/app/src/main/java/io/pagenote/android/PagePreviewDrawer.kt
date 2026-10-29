package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.pagenote.feature.notes.NotePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PagePreviewDrawer(
  renderer: PdfDocumentSession?,
  markdown: String?,
  totalPages: Int,
  currentPage: Int,
  isRunning: Boolean,
  onDismiss: () -> Unit,
  onSelect: (Int) -> Unit,
) {
  val readerContext = LocalContext.current
  val readerConfiguration = LocalConfiguration.current
  var query by remember { mutableStateOf("") }
  val target = PageJumpPolicy.parseTarget(query, totalPages)
  val visiblePages = remember(query, totalPages) {
    if (query.isBlank()) (0 until totalPages).toList() else listOfNotNull(target)
  }
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)))
  LaunchedEffect(query) {
    listState.scrollToItem(if (query.isBlank()) currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)) else 0)
  }
  Dialog(
    onDismissRequest = { if (!isRunning) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !isRunning, dismissOnClickOutside = !isRunning),
  ) {
    CompositionLocalProvider(LocalContext provides readerContext, LocalConfiguration provides readerConfiguration) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("page_preview_host")) {
      Box(Modifier.fillMaxSize().clickable(enabled = !isRunning, onClick = onDismiss).testTag("page_preview_scrim"))
      Surface(
        modifier = Modifier.align(Alignment.CenterEnd).width(minOf(340.dp, maxWidth - 44.dp)).fillMaxHeight()
          .pointerInput(Unit) { detectTapGestures {} }.testTag("page_preview_drawer"),
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
      ) {
        Column(Modifier.fillMaxSize()) {
          Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(stringResource(R.string.page_preview_title), style = MaterialTheme.typography.titleMedium)
              Text(stringResource(R.string.page_preview_count, totalPages), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(enabled = !isRunning, onClick = onDismiss, modifier = Modifier.testTag("page_preview_close")) {
              Icon(Icons.Outlined.Close, stringResource(R.string.page_preview_close))
            }
          }
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).testTag("page_preview_search"),
            singleLine = true,
            enabled = !isRunning,
            label = { Text(stringResource(R.string.page_preview_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = query.isNotBlank() && target == null,
          )
          if (query.isNotBlank() && target == null) {
            Text(stringResource(R.string.page_jump_invalid, totalPages), Modifier.padding(horizontal = 20.dp).testTag("page_preview_invalid"), color = MaterialTheme.colorScheme.error)
          }
          if (isRunning) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("page_preview_saving"))
          LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("page_preview_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            items(visiblePages, key = { it }) { index ->
              PagePreviewItem(index, index == currentPage, renderer, markdown, !isRunning) { onSelect(index) }
            }
          }
        }
      }
    }
    }
  }
}

@Composable
private fun PagePreviewItem(index: Int, active: Boolean, renderer: PdfDocumentSession?, markdown: String?, enabled: Boolean, onClick: () -> Unit) {
  var previewFailed by remember(renderer, index) { mutableStateOf(false) }
  val thumbnail by produceState<Bitmap?>(null, renderer, index) {
    if (renderer != null) {
      try { value = withContext(Dispatchers.IO) { renderer.renderThumbnail(index) } }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { previewFailed = true }
    }
  }
  val label = stringResource(R.string.page_preview_page, index + 1)
  Surface(
    modifier = Modifier.fillMaxWidth().semantics { selected = active }.testTag("page_preview_item_$index"),
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(8.dp),
    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(8.dp)) {
      Box(Modifier.fillMaxWidth().aspectRatio(1.4f).background(Color.White), contentAlignment = Alignment.Center) {
        val bitmap = thumbnail
        when {
          bitmap != null -> Image(bitmap.asImageBitmap(), label, Modifier.fillMaxSize().testTag("page_preview_bitmap_$index"), contentScale = ContentScale.Fit)
          markdown != null -> Text(
            NotePage.pageAt(markdown, index + 1).take(800),
            Modifier.fillMaxSize().padding(14.dp), color = Color(0xFF263735),
            maxLines = 8, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
          )
          previewFailed -> Text(stringResource(R.string.page_preview_failed), color = Color(0xFF526566), style = MaterialTheme.typography.bodySmall)
          else -> CircularProgressIndicator(Modifier.size(24.dp))
        }
      }
      Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (active) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
      }
    }
  }
}
