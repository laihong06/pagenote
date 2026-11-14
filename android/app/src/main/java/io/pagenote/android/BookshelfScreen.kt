package io.pagenote.android

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BookshelfScreen(
  preferences: BookshelfPreferences?,
  books: List<BookshelfBook>,
  scanning: Boolean,
  error: String?,
  warnings: List<String>,
  busy: Boolean,
  onBind: () -> Unit,
  onRefresh: () -> Unit,
  onPresentation: (String, String) -> Unit,
  onNew: () -> Unit,
  onImport: () -> Unit,
  onExternal: () -> Unit,
  onRain: () -> Unit,
  onSettings: () -> Unit,
  onOpen: (BookshelfBook) -> Unit,
  repository: BookshelfRepository,
) {
  var query by rememberSaveable { mutableStateOf("") }
  var sortMenu by remember { mutableStateOf(false) }
  var moreMenu by remember { mutableStateOf(false) }
  var createMenu by remember { mutableStateOf(false) }
  var sidebarExpanded by rememberSaveable { mutableStateOf(true) }
  var customizing by remember { mutableStateOf<BookshelfBook?>(null) }
  val context = LocalContext.current
  val loader = remember(context) { BookCoverLoader(context) }
  val visible = remember(books, query, preferences?.sort) {
    val filtered = books.filter { bookshelfMatches(it.title, query) }
    if (preferences?.sort == "name") filtered.sortedBy { it.title.lowercase() }
    else filtered.sortedWith(compareByDescending<BookshelfBook> { it.updated }.thenBy { it.title })
  }
  val bound = preferences?.tree != null
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val drawerScope = rememberCoroutineScope()
  ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = false, drawerContent = {
    ModalDrawerSheet {
      Text(stringResource(R.string.bookshelf_title), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
      NavigationDrawerItem(label = { Text(stringResource(R.string.bookshelf_all_notes)) }, selected = true,
        icon = { Icon(Icons.Default.AutoStories, null) }, modifier = Modifier.testTag("bookshelf_navigation_all"),
        onClick = { drawerScope.launch { drawerState.close() } })
      NavigationDrawerItem(label = { Text(stringResource(R.string.bookshelf_change)) }, selected = false,
        icon = { Icon(Icons.Default.FolderOpen, null) },
        modifier = Modifier.alpha(if (busy) 0.38f else 1f).semantics { if (busy) disabled() }.testTag("bookshelf_navigation_change"),
        onClick = { if (!busy) { drawerScope.launch { drawerState.close() }; onBind() } })
      NavigationDrawerItem(label = { Text(stringResource(R.string.rain_classroom_menu)) }, selected = false,
        icon = { Icon(Icons.Default.CloudDownload, null) }, onClick = { drawerScope.launch { drawerState.close() }; onRain() })
      NavigationDrawerItem(label = { Text(stringResource(R.string.settings_action)) }, selected = false,
        icon = { Icon(Icons.Default.Settings, null) }, onClick = { drawerScope.launch { drawerState.close() }; onSettings() })
    }
  }) {
  BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("bookshelf_home")) {
    val showSidebar = maxWidth >= 840.dp
    Row(Modifier.fillMaxSize()) {
      if (showSidebar) {
        Column(Modifier.width(if (sidebarExpanded) 192.dp else 56.dp).fillMaxHeight()
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(horizontal = if (sidebarExpanded) 12.dp else 4.dp, vertical = 12.dp)
          .testTag(if (sidebarExpanded) "bookshelf_sidebar" else "bookshelf_sidebar_collapsed"),
          verticalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(onClick = { sidebarExpanded = !sidebarExpanded }, modifier = Modifier.testTag("bookshelf_sidebar_toggle")) {
            Icon(if (sidebarExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
              stringResource(if (sidebarExpanded) R.string.reader_menu_collapse else R.string.reader_menu_expand))
          }
          if (sidebarExpanded) {
            NavigationDrawerItem(label = { Text(stringResource(R.string.bookshelf_all_notes), maxLines = 1) }, selected = true,
              icon = { Icon(Icons.Default.AutoStories, null) }, onClick = {})
            TextButton(onClick = onNew, enabled = bound && !busy, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.new_project), maxLines = 1)
            }
            TextButton(onClick = onImport, enabled = bound && !busy, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_document), maxLines = 1)
            }
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_action), maxLines = 1)
            }
          } else {
            ShelfIcon(Icons.Default.Add, stringResource(R.string.new_project), "bookshelf_sidebar_new", bound && !busy, onNew)
            ShelfIcon(Icons.Default.FileDownload, stringResource(R.string.open_document), "bookshelf_sidebar_import", bound && !busy, onImport)
            ShelfIcon(Icons.Default.Settings, stringResource(R.string.settings_action), "bookshelf_sidebar_settings", onClick = onSettings)
          }
        }
      }
      Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = if (bound && !busy) 88.dp else 0.dp)) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      if (!showSidebar) {
        ShelfIcon(Icons.Default.Menu, stringResource(R.string.bookshelf_navigation), "bookshelf_navigation_open") { drawerScope.launch { drawerState.open() } }
      }
      Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.width(12.dp))
      Text(stringResource(R.string.bookshelf_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
      ShelfIcon(Icons.Default.Settings, stringResource(R.string.settings_action), "bookshelf_settings") { onSettings() }
      Box {
        ShelfIcon(Icons.Default.MoreVert, stringResource(R.string.reader_menu_group_file), "bookshelf_more") { moreMenu = true }
        DropdownMenu(moreMenu, { moreMenu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.rain_classroom_menu)) }, onClick = { moreMenu = false; onRain() })
          DropdownMenuItem(text = { Text(stringResource(R.string.bookshelf_open_external)) }, enabled = !busy, onClick = { moreMenu = false; onExternal() })
          DropdownMenuItem(text = { Text(stringResource(R.string.bookshelf_change)) }, enabled = !busy, onClick = { moreMenu = false; onBind() })
        }
      }
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(query, { query = it }, Modifier.weight(1f).testTag("bookshelf_search"), singleLine = true,
        placeholder = { Text(stringResource(R.string.bookshelf_search), maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, null) })
      Box {
        ShelfIcon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.bookshelf_sort), "bookshelf_sort") { sortMenu = true }
        DropdownMenu(sortMenu, { sortMenu = false }) {
          listOf("updated" to R.string.bookshelf_updated, "name" to R.string.bookshelf_name).forEach { (sort, label) ->
            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { sortMenu = false; onPresentation(sort, preferences?.view ?: "grid") },
              trailingIcon = { if (preferences?.sort == sort) Icon(Icons.Default.Check, null) })
          }
        }
      }
      val grid = preferences?.view != "list"
      ShelfIcon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
        stringResource(if (grid) R.string.bookshelf_list else R.string.bookshelf_grid), "bookshelf_view") {
        onPresentation(preferences?.sort ?: "updated", if (grid) "list" else "grid")
      }
      ShelfIcon(Icons.Default.Refresh, stringResource(R.string.bookshelf_refresh), "bookshelf_refresh", !scanning) { onRefresh() }
    }
    if (bound) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(preferences!!.name, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.bookshelf_count, books.size), style = MaterialTheme.typography.labelLarge)
      }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onNew, enabled = bound && !busy, modifier = Modifier.testTag("bookshelf_new")) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.new_project))
      }
      TextButton(onClick = onImport, enabled = bound && !busy, modifier = Modifier.testTag("bookshelf_import")) {
        Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_document))
      }
    }
    if (scanning || busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("bookshelf_loading"))
    if (error != null) Text(error, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).testTag("bookshelf_error"), color = MaterialTheme.colorScheme.error)
    if (error != null && bound) TextButton(onClick = onBind, enabled = !busy) { Text(stringResource(R.string.bookshelf_change)) }
    if (warnings.isNotEmpty()) Text(stringResource(R.string.bookshelf_warnings, warnings.joinToString("; ")),
      Modifier.padding(horizontal = 24.dp, vertical = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall)
    when {
      preferences == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      !bound -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.bookshelf_unbound), Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBind, modifier = Modifier.testTag("bookshelf_bind")) { Text(stringResource(R.string.bookshelf_bind)) }
      }
      else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)
        .testTag(if (preferences.view == "list") "bookshelf_list" else "bookshelf_grid")) {
        val columns = (maxWidth.value / 160).toInt().coerceIn(1, 8)
        LazyColumn(Modifier.fillMaxSize().testTag("bookshelf_books"), contentPadding = PaddingValues(top = 30.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
          if (preferences.view == "list") {
            items(visible, key = { it.key }) { book ->
              Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onOpen(book) }.padding(8.dp).testTag("bookshelf_book_${book.project.name}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BookCover(book, loader, Modifier.size(66.dp, 88.dp))
                Column(Modifier.weight(1f)) {
                  Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                  BookDate(book)
                }
                ShelfIcon(Icons.Default.MoreVert, stringResource(R.string.bookshelf_customize), "bookshelf_customize_${book.project.name}", !busy) { customizing = book }
              }
              HorizontalDivider()
            }
          } else {
            items(visible.chunked(columns), key = { row -> row.first().key }) { row ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { book ->
                  Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clickable(enabled = !busy) { onOpen(book) }.testTag("bookshelf_book_${book.project.name}")) {
                      BookCover(book, loader, Modifier.size(136.dp, 181.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                      Column(Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(book.title, Modifier.height(40.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        BookDate(book)
                      }
                      ShelfIcon(Icons.Default.MoreVert, stringResource(R.string.bookshelf_customize), "bookshelf_customize_${book.project.name}", !busy) { customizing = book }
                    }
                  }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
              }
            }
          }
          if (visible.isEmpty() && !scanning && error == null) item {
            Text(stringResource(if (query.isBlank()) R.string.bookshelf_empty else R.string.bookshelf_no_match), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
      }
    }
    if (bound && !busy) {
      Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
        FloatingActionButton(onClick = { createMenu = true }, modifier = Modifier.testTag("bookshelf_create_menu")) {
          Icon(Icons.Default.Add, stringResource(R.string.new_project))
        }
        DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.new_project)) },
            leadingIcon = { Icon(Icons.Default.Add, null) }, modifier = Modifier.testTag("bookshelf_create_new"),
            onClick = { createMenu = false; onNew() })
          DropdownMenuItem(text = { Text(stringResource(R.string.open_document)) },
            leadingIcon = { Icon(Icons.Default.FileDownload, null) }, modifier = Modifier.testTag("bookshelf_create_import"),
            onClick = { createMenu = false; onImport() })
        }
      }
    }
  }
  customizing?.let { book -> BookAppearanceDialog(book, repository, onDismiss = { customizing = null }, onSaved = { customizing = null; onRefresh() }) }
  }
}

@Composable private fun BookDate(book: BookshelfBook) {
  val date = remember(book.updated) { if (book.updated > 0) DateFormat.getDateInstance(DateFormat.SHORT).format(Date(book.updated)) else book.project.sourceExtension.uppercase() }
  Text(date, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun BookCover(book: BookshelfBook, loader: BookCoverLoader, modifier: Modifier = Modifier) {
  val type = book.appearance.resolvedCover(book.project.sourceExtension)
  var bitmap by remember(book.key, book.revision, book.appearance) { mutableStateOf<Bitmap?>(null) }
  var failed by remember(book.key, book.revision, book.appearance) { mutableStateOf(false) }
  LaunchedEffect(book.key, book.revision, book.appearance) {
    if (type != "paper") try { bitmap = loader.load(book) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { failed = true }
  }
  val shape = RoundedCornerShape(5.dp)
  Box(modifier.shadow(4.dp, shape).clip(shape).background(Color.White), contentAlignment = Alignment.Center) {
    when {
      bitmap != null -> Image(bitmap!!.asImageBitmap(), book.title, Modifier.fillMaxSize().testTag("bookshelf_cover_image_${book.project.name}"), contentScale = ContentScale.Fit)
      type == "paper" -> {
        val color = paperColor(book.appearance.paper)
        Canvas(Modifier.fillMaxSize()) {
          drawRect(color)
          for (y in 1..16) drawLine(Color.Black.copy(alpha = 0.055f), Offset(0f, size.height * y / 17), Offset(size.width, size.height * y / 17))
          drawLine(Color.Black.copy(alpha = 0.12f), Offset(size.width * .08f, 0f), Offset(size.width * .08f, size.height), size.width * .025f)
        }
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
          Text(stringResource(R.string.bookshelf_cover_label), color = Color(0xFF303C37), style = MaterialTheme.typography.titleMedium)
          HorizontalDivider(Modifier.padding(top = 12.dp), color = Color.Black.copy(alpha = .2f))
          Text("pagenote", Modifier.padding(top = 8.dp), color = Color(0xFF4B5854), style = MaterialTheme.typography.labelSmall)
        }
      }
      failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.bookshelf_cover_failed), Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
      }
      else -> CircularProgressIndicator(Modifier.size(24.dp))
    }
    if (book.warning != null) Icon(Icons.Default.WarningAmber, book.warning, Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp), tint = MaterialTheme.colorScheme.error)
  }
}

private fun paperColor(name: String): Color = when (name) {
  "rose" -> Color(0xFFF2D7DC)
  "sky" -> Color(0xFFD8E7F1)
  "graphite" -> Color(0xFFCFD3D8)
  else -> Color(0xFFDCE8DB)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ShelfIcon(icon: ImageVector, label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
  TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)) { Icon(icon, label) }
  }
}

@Composable
private fun BookAppearanceDialog(book: BookshelfBook, repository: BookshelfRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
  var title by remember { mutableStateOf(book.appearance.titleOr(book.project.name)) }
  var type by remember { mutableStateOf(book.appearance.cover) }
  var page by remember { mutableStateOf((book.appearance.page + 1).toString()) }
  var paper by remember { mutableStateOf(book.appearance.paper) }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  fun draft() = BookAppearance(title.trim().takeIf { it != book.project.name && it.isNotEmpty() }, type,
    book.appearance.asset, if (type == "pdf") (page.toIntOrNull() ?: 0) - 1 else 0, paper)
  fun save(reset: Boolean = false) {
    saving = true
    scope.launch {
      try { repository.saveAppearance(book, if (reset) BookAppearance() else draft()); onSaved() }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { error = failure.message }
      finally { saving = false }
    }
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      saving = true
      scope.launch {
        try { repository.importCover(book, uri, draft().copy(page = 0)); onSaved() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { saving = false }
      }
    }
  }
  AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.bookshelf_customize)) },
    text = {
      Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { if (it.length <= 200) title = it }, Modifier.testTag("bookshelf_display_title"), label = { Text(stringResource(R.string.bookshelf_display_title)) }, enabled = !saving)
        val choices = listOf("default" to R.string.bookshelf_default, "paper" to R.string.bookshelf_paper) +
          (if (book.project.sourceExtension == "pdf") listOf("pdf" to R.string.bookshelf_pdf) else emptyList()) +
          (if (book.appearance.asset != null) listOf("image" to R.string.bookshelf_image) else emptyList())
        choices.forEach { (value, label) -> Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { type = value }, verticalAlignment = Alignment.CenterVertically) {
          RadioButton(type == value, { type = value }, enabled = !saving); Text(stringResource(label))
        } }
        if (type == "pdf") OutlinedTextField(page, { page = it.filter(Char::isDigit).take(6) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.bookshelf_page)) }, enabled = !saving)
        if (type == "paper" || (type == "default" && book.project.sourceExtension != "pdf")) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          BookAppearance.PAPER_COLORS.forEachIndexed { index, color ->
            val names = listOf(R.string.bookshelf_sage, R.string.bookshelf_rose, R.string.bookshelf_sky, R.string.bookshelf_graphite)
            IconButton(onClick = { paper = color }, enabled = !saving, modifier = Modifier.size(44.dp).clip(CircleShape).background(paperColor(color))
              .border(if (paper == color) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape).testTag("bookshelf_paper_$color")) {
              if (paper == color) Icon(Icons.Default.Check, stringResource(names[index]), tint = Color.DarkGray)
              else Icon(Icons.Default.Circle, stringResource(names[index]), tint = Color.Transparent)
            }
          }
        }
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !saving) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.bookshelf_choose_image)) }
        TextButton(onClick = { save(reset = true) }, enabled = !saving, modifier = Modifier.testTag("bookshelf_reset")) { Text(stringResource(R.string.bookshelf_reset)) }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
      }
    }, confirmButton = { TextButton(onClick = { save() }, enabled = !saving, modifier = Modifier.testTag("bookshelf_appearance_save")) { Text(stringResource(R.string.save)) } },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}
