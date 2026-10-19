package io.pagenote.android

import android.view.KeyEvent

internal enum class ReaderShortcutAction {
  PREVIOUS_PAGE,
  NEXT_PAGE,
  SAVE_NOTE,
}

internal object ReaderShortcutPolicy {
  fun action(
    keyCode: Int,
    alt: Boolean = false,
    control: Boolean = false,
    meta: Boolean = false,
  ): ReaderShortcutAction? = when {
    alt && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> ReaderShortcutAction.PREVIOUS_PAGE
    alt && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> ReaderShortcutAction.NEXT_PAGE
    (control || meta) && keyCode == KeyEvent.KEYCODE_S -> ReaderShortcutAction.SAVE_NOTE
    else -> null
  }
}
