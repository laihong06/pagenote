package io.pagenote.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BookshelfController(private val context: Context, private val scope: CoroutineScope) {
  val store = BookshelfStore(context.applicationContext)
  val repository = BookshelfRepository(context)
  var preferences by mutableStateOf<BookshelfPreferences?>(null)
  var books by mutableStateOf(emptyList<BookshelfBook>())
  var warnings by mutableStateOf(emptyList<String>())
  var error by mutableStateOf<String?>(null)
  var scanning by mutableStateOf(false)
  var revision by mutableIntStateOf(0)
  var choosingDirectory by mutableStateOf(false)
  val tree: Uri? get() = preferences?.tree?.let(Uri::parse)

  fun refresh() { revision++ }
  fun chooseDirectory() { choosingDirectory = true }
  fun presentation(sort: String, view: String) {
    scope.launch {
      try { store.presentation(sort, view) }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { error = failure.message }
    }
  }
  fun bind(uri: Uri?) {
    if (uri == null) return
    scope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
          store.bind(uri.toString(), repository.directoryName(uri))
        }
        error = null
        refresh()
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { error = context.getString(R.string.bookshelf_unavailable, failure.message.orEmpty()) }
    }
  }
}

@Composable
internal fun rememberBookshelfController(context: Context, visible: Boolean, owner: LifecycleOwner?): BookshelfController {
  val scope = rememberCoroutineScope()
  val controller = remember(context) { BookshelfController(context, scope) }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), controller::bind)
  LaunchedEffect(controller.choosingDirectory) {
    if (controller.choosingDirectory) {
      controller.choosingDirectory = false
      picker.launch(controller.tree)
    }
  }
  LaunchedEffect(controller) {
    try { controller.store.preferences.collect { controller.preferences = it } }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { controller.error = failure.message; controller.preferences = BookshelfPreferences() }
  }
  val tree = controller.tree
  LaunchedEffect(controller, tree, controller.revision, visible) {
    if (!visible) return@LaunchedEffect
    controller.books = emptyList()
    controller.warnings = emptyList()
    if (tree == null) return@LaunchedEffect
    controller.scanning = true
    controller.error = null
    try {
      val scan = controller.repository.scan(tree)
      controller.books = scan.books
      controller.warnings = scan.warnings
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { controller.error = context.getString(R.string.bookshelf_unavailable, failure.message.orEmpty()) }
    finally { controller.scanning = false }
  }
  DisposableEffect(controller, owner) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) controller.refresh() }
    owner?.lifecycle?.addObserver(observer)
    onDispose { owner?.lifecycle?.removeObserver(observer) }
  }
  return controller
}
