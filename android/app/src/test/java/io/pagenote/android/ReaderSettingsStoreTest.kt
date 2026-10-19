package io.pagenote.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderSettingsStoreTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun newStoreEmitsDefaults() = runBlocking {
    val scope = newScope()
    try {
      val store = ReaderSettingsStore(newDataStore(File(temporaryFolder.root, "settings.preferences_pb"), scope))

      assertEquals(ReaderSettings(), store.settings.first())
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun saveSurvivesOpeningAnotherStoreOnSameFile() = runBlocking {
    val file = File(temporaryFolder.root, "settings.preferences_pb")
    val firstScope = newScope()
    try {
      val firstStore = ReaderSettingsStore(newDataStore(file, firstScope))
      firstStore.save(ReaderSettings("dark", "zh-CN", 61, false))
    } finally {
      firstScope.cancel()
    }

    val secondScope = newScope()
    try {
      val secondStore = ReaderSettingsStore(newDataStore(file, secondScope))

      assertEquals(ReaderSettings("dark", "zh-CN", 61, false), secondStore.settings.first())
    } finally {
      secondScope.cancel()
    }
  }

  @Test
  fun noteFontSizeSurvivesOpeningAnotherStoreOnSameFile() = runBlocking {
    val file = File(temporaryFolder.root, "font-size.preferences_pb")
    val firstScope = newScope()
    try {
      val firstStore = ReaderSettingsStore(newDataStore(file, firstScope))
      firstStore.save(ReaderSettings(noteFontSize = 24))
    } finally {
      firstScope.cancel()
    }

    val secondScope = newScope()
    try {
      val secondStore = ReaderSettingsStore(newDataStore(file, secondScope))

      assertEquals(24, secondStore.settings.first().noteFontSize)
    } finally {
      secondScope.cancel()
    }
  }

  @Test
  fun invalidStoredFieldsFallBackIndependently() = runBlocking {
    val scope = newScope()
    try {
      val dataStore = newDataStore(File(temporaryFolder.root, "settings.preferences_pb"), scope)
      dataStore.edit { preferences ->
        preferences[stringPreferencesKey("theme")] = "neon"
        preferences[booleanPreferencesKey("language")] = true
        preferences[intPreferencesKey("left_percent")] = 90
        preferences[intPreferencesKey("note_font_size")] = 17
        preferences[booleanPreferencesKey("note_animation")] = false
      }

      val store = ReaderSettingsStore(dataStore)

      assertEquals(ReaderSettings(noteAnimation = false), store.settings.first())
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun invalidSaveLeavesPreviouslyStoredSnapshotUnchanged() = runBlocking {
    val scope = newScope()
    try {
      val store = ReaderSettingsStore(newDataStore(File(temporaryFolder.root, "settings.preferences_pb"), scope))
      val original = ReaderSettings("paper", "en-US", 48, false)
      store.save(original)

      var rejected = false
      try {
        store.save(original.copy(leftPercent = 81))
      } catch (_: IllegalArgumentException) {
        rejected = true
      }
      assertTrue(rejected)
      assertEquals(original, store.settings.first())

      var badFontSizeRejected = false
      try {
        store.save(original.copy(noteFontSize = 17))
      } catch (_: IllegalArgumentException) {
        badFontSizeRejected = true
      }
      assertTrue(badFontSizeRejected)
      assertEquals(original, store.settings.first())
    } finally {
      scope.cancel()
    }
  }

  private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun newDataStore(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { file },
  )
}
