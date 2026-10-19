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
}
