package io.pagenote.android

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderShortcutPolicyTest {
  @Test
  fun mapsDesktopReaderKeyboardShortcuts() {
    assertEquals(
      ReaderShortcutAction.PREVIOUS_PAGE,
      ReaderShortcutPolicy.action(KeyEvent.KEYCODE_DPAD_LEFT, alt = true),
    )
    assertEquals(
      ReaderShortcutAction.NEXT_PAGE,
      ReaderShortcutPolicy.action(KeyEvent.KEYCODE_DPAD_RIGHT, alt = true),
    )
    assertEquals(
      ReaderShortcutAction.SAVE_NOTE,
      ReaderShortcutPolicy.action(KeyEvent.KEYCODE_S, control = true),
    )
    assertEquals(
      ReaderShortcutAction.SAVE_NOTE,
      ReaderShortcutPolicy.action(KeyEvent.KEYCODE_S, meta = true),
    )
  }

  @Test
  fun ignoresUnmodifiedAndUnrelatedKeys() {
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_DPAD_LEFT))
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_A, alt = true))
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_S, alt = true))
  }

  @Test
  fun recognizesFormattingShortcutsForBothDesktopModifierKeys() {
    val shortcuts = listOf(
      KeyEvent.KEYCODE_B to ReaderShortcutAction.FORMAT_BOLD,
      KeyEvent.KEYCODE_I to ReaderShortcutAction.FORMAT_ITALIC,
      KeyEvent.KEYCODE_K to ReaderShortcutAction.INSERT_LINK,
    )
    shortcuts.forEach { (keyCode, action) ->
      assertEquals(action, ReaderShortcutPolicy.action(keyCode, control = true))
      assertEquals(action, ReaderShortcutPolicy.action(keyCode, meta = true))
    }
  }

  @Test
  fun ignoresFormattingShortcutsWithUnrelatedModifierCombinations() {
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_B, control = true, alt = true))
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_I, control = true, meta = true))
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_B, control = true, shift = true))
    assertNull(ReaderShortcutPolicy.action(KeyEvent.KEYCODE_K))
  }
}
