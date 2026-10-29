package io.pagenote.android

import io.pagenote.feature.pen.NoteBackground
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNoteSettingsTest {
  private val hasher = Pbkdf2NotePassphraseHasher(iterations = 1_000)

  @Test
  fun defaultsEncodeAsMinimalVersionedJson() {
    val encoded = ProjectNoteSettingsCodec.encode(ProjectNoteSettings.DEFAULT)
    val root = Json.parseToJsonElement(encoded).jsonObject
    assertEquals(setOf("version"), root.keys)
    assertEquals(1, (root.getValue("version") as kotlinx.serialization.json.JsonPrimitive).content.toInt())
  }

  @Test
  fun decodeReturnsDefaultsForTheEmptyShape() {
    assertEquals(ProjectNoteSettings.DEFAULT, ProjectNoteSettingsCodec.decode("""{"version":1}"""))
  }

  @Test
  fun backgroundAndPassphraseRoundTrip() {
    val hash = hasher.hash("project pass".toCharArray())
    val settings = ProjectNoteSettings(
      background = NoteBackground.FOUR_LINE,
      passphraseHash = hash,
    )
    val decoded = ProjectNoteSettingsCodec.decode(ProjectNoteSettingsCodec.encode(settings))
    assertEquals(settings, decoded)
    assertTrue(decoded.hasPassphrase)
    assertTrue(hasher.verify("project pass".toCharArray(), decoded.passphraseHash!!))
  }

  @Test
  fun omittedFieldsDecodeAsUnset() {
    val decoded = ProjectNoteSettingsCodec.decode(
      """{"version":1,"background":"grid"}""",
    )
    assertEquals(NoteBackground.GRID, decoded.background)
    assertNull(decoded.passphraseHash)
    assertFalse(decoded.hasPassphrase)
  }

  @Test
  fun unknownBackgroundKeyDegradesToUnsetInsteadOfFailing() {
    val decoded = ProjectNoteSettingsCodec.decode(
      """{"version":1,"background":"cornell-2027"}""",
    )
    assertNull(decoded.background)
    assertFalse(decoded.hasPassphrase)
  }

  @Test
  fun unknownRootFieldsAreIgnored() {
    val decoded = ProjectNoteSettingsCodec.decode(
      """{"version":1,"background":"dot","futureField":{"nested":true}}""",
    )
    assertEquals(NoteBackground.DOT, decoded.background)
  }

  @Test
  fun unsupportedVersionRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      ProjectNoteSettingsCodec.decode("""{"version":2}""")
    }
    assertThrows(IllegalArgumentException::class.java) {
      ProjectNoteSettingsCodec.decode("""{}""")
    }
  }

  @Test
  fun malformedJsonRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      ProjectNoteSettingsCodec.decode("not json")
    }
    assertThrows(IllegalArgumentException::class.java) {
      ProjectNoteSettingsCodec.decode("""[1,2,3]""")
    }
  }

  @Test
  fun corruptPassphraseBlockRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      ProjectNoteSettingsCodec.decode(
        """{"version":1,"passphrase":{"algorithm":"ROT13","iterations":10,"salt":"AQ==","hash":"AQ=="}}""",
      )
    }
  }

  @Test
  fun clearPassphraseEncodesWithoutTheBlock() {
    val settings = ProjectNoteSettings(background = NoteBackground.PLAIN, passphraseHash = null)
    val root = Json.parseToJsonElement(ProjectNoteSettingsCodec.encode(settings)).jsonObject
    assertEquals(setOf("version", "background"), root.keys)
    assertFalse(ProjectNoteSettingsCodec.decode(ProjectNoteSettingsCodec.encode(settings)).hasPassphrase)
  }
}
