package io.pagenote.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.floatingPhraseDataStore by preferencesDataStore(name = "floating_phrases")

fun floatingPhraseStore(context: Context): FloatingPhraseStore =
  FloatingPhraseStore(context.floatingPhraseDataStore)

/**
 * 悬浮窗常用语纯策略（移植差距清单 #24）：悬浮面板「常用语」页的
 * 归一化/去重/容量钳制都在这里决定，存储与服务 UI 只执行结果。
 */
object FloatingPhrasePolicy {
  /** 常用语容量上限（超出后最旧条目被挤出）。 */
  const val MAX_PHRASES = 50

  /** 单条常用语字符上限（超出截断）。 */
  const val MAX_CHARS = 2000

  /** 列表预览单条显示上限。 */
  const val PREVIEW_CHARS = 80

  /** 归一化：trim + 长度截断；空白输入返回 null（拒绝）。 */
  fun normalize(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.take(MAX_CHARS)
  }

  /** 添加一条常用语：归一化、按整串去重（已存在则提前到顶部）、容量钳制。 */
  fun add(existing: List<String>, raw: String): List<String> {
    val phrase = normalize(raw) ?: return existing
    val withoutDuplicate = existing.filterNot { it == phrase }
    return (listOf(phrase) + withoutDuplicate).take(MAX_PHRASES)
  }

  /** 删除指定下标（越界忽略）。 */
  fun remove(existing: List<String>, index: Int): List<String> {
    if (index !in existing.indices) return existing
    return existing.toMutableList().apply { removeAt(index) }
  }

  /** 列表条目单行预览：换行折叠为空格，超长截断。 */
  fun preview(phrase: String, maxChars: Int = PREVIEW_CHARS): String {
    require(maxChars > 0) { "bad preview width" }
    val flat = phrase.replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= maxChars) flat else flat.take(maxChars) + "…"
  }
}

/**
 * 常用语持久化（DataStore，模式同 [ReaderSettingsStore]）：数量键 +
 * 下标键存储，避免分隔符出现在文本内容里的问题。
 */
class FloatingPhraseStore(private val dataStore: DataStore<Preferences>) {
  val phrases: Flow<List<String>> = dataStore.data.map { preferences -> readPhrases(preferences) }

  /** 添加一条（重复的提前到顶部）；返回归一化后的文本，拒绝空白时返回 null。 */
  suspend fun add(raw: String): String? {
    val phrase = FloatingPhrasePolicy.normalize(raw) ?: return null
    dataStore.edit { preferences ->
      val current = readPhrases(preferences)
      writePhrases(preferences, FloatingPhrasePolicy.add(current, phrase))
    }
    return phrase
  }

  suspend fun remove(index: Int) {
    dataStore.edit { preferences ->
      val current = readPhrases(preferences)
      writePhrases(preferences, FloatingPhrasePolicy.remove(current, index))
    }
  }

  private fun readPhrases(preferences: Preferences): List<String> {
    val count = preferences[COUNT_KEY] ?: return emptyList()
    if (count <= 0) return emptyList()
    return (0 until count).mapNotNull { index -> preferences[phraseKey(index)] }
  }

  private fun writePhrases(preferences: MutablePreferences, phrases: List<String>) {
    val previousCount = preferences[COUNT_KEY] ?: 0
    preferences[COUNT_KEY] = phrases.size
    phrases.forEachIndexed { index, phrase -> preferences[phraseKey(index)] = phrase }
    for (index in phrases.size until previousCount) {
      preferences.remove(phraseKey(index))
    }
  }

  private fun phraseKey(index: Int): Preferences.Key<String> = stringPreferencesKey("phrase_$index")

  private companion object {
    val COUNT_KEY = intPreferencesKey("phrase_count")
  }
}
