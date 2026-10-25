package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePassphrasePolicyTest {
  @Test
  fun acceptsPassphrasesWithinTheLengthBounds() {
    assertTrue(NotePassphrasePolicy.isValidNewPassphrase("abcd"))
    assertTrue(NotePassphrasePolicy.isValidNewPassphrase("a".repeat(128)))
    assertTrue(NotePassphrasePolicy.isValidNewPassphrase("中文口令示例"))
  }

  @Test
  fun rejectsTooShortOrTooLongPassphrases() {
    assertFalse(NotePassphrasePolicy.isValidNewPassphrase("abc"))
    assertFalse(NotePassphrasePolicy.isValidNewPassphrase(""))
    assertFalse(NotePassphrasePolicy.isValidNewPassphrase("a".repeat(129)))
  }

  @Test
  fun rejectsControlCharacters() {
    assertFalse(NotePassphrasePolicy.isValidNewPassphrase("ab\u0000d"))
    assertFalse(NotePassphrasePolicy.isValidNewPassphrase("ab\nd"))
  }

  @Test
  fun confirmationRequiresExactMatchOfAValidPassphrase() {
    assertTrue(NotePassphrasePolicy.isConfirmed("secret", "secret"))
    assertFalse(NotePassphrasePolicy.isConfirmed("secret", "secreT"))
    assertFalse(NotePassphrasePolicy.isConfirmed("abc", "abc"))
    assertFalse(NotePassphrasePolicy.isConfirmed("secret", ""))
  }
}
