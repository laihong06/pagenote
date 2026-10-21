package io.pagenote.android

import android.view.KeyEvent

internal enum class ReaderShortcutAction {
  PREVIOUS_PAGE,
  NEXT_PAGE,
  SAVE_NOTE,
  FORMAT_BOLD,
  FORMAT_ITALIC,
  INSERT_LINK,
}

internal object ReaderShortcutPolicy {
  fun action(
    keyCode: Int,
    alt: Boolean = false,
    control: Boolean = false,
    meta: Boolean = false,
    shift: Boolean = false,
  ): ReaderShortcutAction? = when {
    alt && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> ReaderShortcutAction.PREVIOUS_PAGE
    alt && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> ReaderShortcutAction.NEXT_PAGE
    (control || meta) && keyCode == KeyEvent.KEYCODE_S -> ReaderShortcutAction.SAVE_NOTE
    (control xor meta) && !alt && !shift -> when (keyCode) {
      KeyEvent.KEYCODE_B -> ReaderShortcutAction.FORMAT_BOLD
      KeyEvent.KEYCODE_I -> ReaderShortcutAction.FORMAT_ITALIC
      KeyEvent.KEYCODE_K -> ReaderShortcutAction.INSERT_LINK
      else -> null
    }
    else -> null
  }
}
