package io.pagenote.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsDataStore by preferencesDataStore(name = "reader_settings")

fun readerSettingsStore(context: Context): ReaderSettingsStore =
  ReaderSettingsStore(context.readerSettingsDataStore)

class ReaderSettingsStore(private val dataStore: DataStore<Preferences>) {
  val settings: Flow<ReaderSettings> = dataStore.data.map { preferences ->
    val defaults = ReaderSettings()
    ReaderSettingsPolicy.sanitize(
      ReaderSettings(
        theme = preferences.stringOrDefault(THEME_KEY, defaults.theme),
        language = preferences.stringOrDefault(LANGUAGE_KEY, defaults.language),
        leftPercent = preferences.intOrDefault(LEFT_PERCENT_KEY, defaults.leftPercent),
        noteAnimation = preferences.booleanOrDefault(NOTE_ANIMATION_KEY, defaults.noteAnimation),
        noteFontSize = preferences.intOrDefault(NOTE_FONT_SIZE_KEY, defaults.noteFontSize),
        allowInkOutsidePage = preferences.booleanOrDefault(
          ALLOW_INK_OUTSIDE_PAGE_KEY,
          defaults.allowInkOutsidePage,
        ),
        prepareAheadPages = preferences.intOrDefault(PREPARE_AHEAD_PAGES_KEY, defaults.prepareAheadPages),
        markdownTheme = preferences.stringOrDefault(MARKDOWN_THEME_KEY, defaults.markdownTheme),
        noteBackground = preferences.stringOrDefault(NOTE_BACKGROUND_KEY, defaults.noteBackground),
        ocrLanguage = preferences.stringOrDefault(OCR_LANGUAGE_KEY, defaults.ocrLanguage),
        latexOcrModel = preferences.stringOrDefault(LATEX_OCR_MODEL_KEY, defaults.latexOcrModel),
        searchIndexSource = preferences.booleanOrDefault(SEARCH_INDEX_SOURCE_KEY, defaults.searchIndexSource),
        searchIndexNotes = preferences.booleanOrDefault(SEARCH_INDEX_NOTES_KEY, defaults.searchIndexNotes),
        searchIndexOcr = preferences.booleanOrDefault(SEARCH_INDEX_OCR_KEY, defaults.searchIndexOcr),
        searchIndexTranscripts = preferences.booleanOrDefault(
          SEARCH_INDEX_TRANSCRIPTS_KEY,
          defaults.searchIndexTranscripts,
        ),
      ),
    )
  }

  suspend fun save(settings: ReaderSettings) {
    ReaderSettingsPolicy.validate(settings)
    dataStore.edit { preferences ->
      preferences[THEME_KEY] = settings.theme
      preferences[LANGUAGE_KEY] = settings.language
      preferences[LEFT_PERCENT_KEY] = settings.leftPercent
      preferences[NOTE_ANIMATION_KEY] = settings.noteAnimation
      preferences[NOTE_FONT_SIZE_KEY] = settings.noteFontSize
      preferences[ALLOW_INK_OUTSIDE_PAGE_KEY] = settings.allowInkOutsidePage
      preferences[PREPARE_AHEAD_PAGES_KEY] = settings.prepareAheadPages
      preferences[MARKDOWN_THEME_KEY] = settings.markdownTheme
      preferences[NOTE_BACKGROUND_KEY] = settings.noteBackground
      preferences[OCR_LANGUAGE_KEY] = settings.ocrLanguage
      preferences[LATEX_OCR_MODEL_KEY] = settings.latexOcrModel
      preferences[SEARCH_INDEX_SOURCE_KEY] = settings.searchIndexSource
      preferences[SEARCH_INDEX_NOTES_KEY] = settings.searchIndexNotes
      preferences[SEARCH_INDEX_OCR_KEY] = settings.searchIndexOcr
      preferences[SEARCH_INDEX_TRANSCRIPTS_KEY] = settings.searchIndexTranscripts
    }
  }
}

private val THEME_KEY = stringPreferencesKey("theme")
private val LANGUAGE_KEY = stringPreferencesKey("language")
private val LEFT_PERCENT_KEY = intPreferencesKey("left_percent")
private val NOTE_ANIMATION_KEY = booleanPreferencesKey("note_animation")
private val NOTE_FONT_SIZE_KEY = intPreferencesKey("note_font_size")
private val ALLOW_INK_OUTSIDE_PAGE_KEY = booleanPreferencesKey("allow_ink_outside_page")
private val PREPARE_AHEAD_PAGES_KEY = intPreferencesKey("prepare_ahead_pages")
private val MARKDOWN_THEME_KEY = stringPreferencesKey("markdown_theme")
private val NOTE_BACKGROUND_KEY = stringPreferencesKey("note_background")
private val OCR_LANGUAGE_KEY = stringPreferencesKey("ocr_language")
private val LATEX_OCR_MODEL_KEY = stringPreferencesKey("latex_ocr_model")
private val SEARCH_INDEX_SOURCE_KEY = booleanPreferencesKey("search_index_source")
private val SEARCH_INDEX_NOTES_KEY = booleanPreferencesKey("search_index_notes")
private val SEARCH_INDEX_OCR_KEY = booleanPreferencesKey("search_index_ocr")
private val SEARCH_INDEX_TRANSCRIPTS_KEY = booleanPreferencesKey("search_index_transcripts")

private fun Preferences.stringOrDefault(key: Preferences.Key<String>, default: String): String =
  try {
    this[key] ?: default
  } catch (_: ClassCastException) {
    default
  }

private fun Preferences.intOrDefault(key: Preferences.Key<Int>, default: Int): Int =
  try {
    this[key] ?: default
  } catch (_: ClassCastException) {
    default
  }

private fun Preferences.booleanOrDefault(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
  try {
    this[key] ?: default
  } catch (_: ClassCastException) {
    default
  }
