package io.pagenote.feature.ocr

/**
 * pix2tex 词典（tokens.json：字符串数组，下标即 token id）。保留前缀与
 * pix2tex 词汇表一致：0=`<pad>`、1=`<bos>`、2=`<eos>`。解码为 token 直接
 * 拼接（结构字符与命令本身已含于 token），拼结果裁剪首尾空白。
 */
class LatexTokenizer private constructor(
  val tokens: List<String>,
) {
  val padId: Int = PAD_ID
  val bosId: Int = BOS_ID
  val eosId: Int = EOS_ID
  val vocabularySize: Int get() = tokens.size

  fun tokenAt(id: Int): String? = tokens.getOrNull(id)

  /** id 序列 → LaTeX；跳过 PAD/BOS，遇 EOS 停止；未知 id 以占位符呈现。 */
  fun decode(ids: IntArray): String {
    val builder = StringBuilder()
    for (id in ids) {
      if (id == EOS_ID) break
      if (id == PAD_ID || id == BOS_ID) continue
      builder.append(tokens.getOrNull(id) ?: UNKNOWN_TOKEN)
    }
    return builder.toString().trim()
  }

  /** 文本 token 化（按词典子串匹配，长 token 优先）；未知字符映射为未知 id。 */
  fun encode(text: String): IntArray {
    val sorted = sortedTokens
    val ids = mutableListOf<Int>()
    var index = 0
    while (index < text.length) {
      val match = sorted.firstOrNull { text.startsWith(it.first, index) }
      if (match == null) {
        ids.add(UNKNOWN_ID)
        index += 1
      } else {
        ids.add(match.second)
        index += match.first.length
      }
    }
    return ids.toIntArray()
  }

  private val sortedTokens: List<Pair<String, Int>> by lazy {
    tokens.mapIndexed { id, token -> token to id }
      .sortedByDescending { it.first.length }
  }

  companion object {
    const val PAD_ID = 0
    const val BOS_ID = 1
    const val EOS_ID = 2
    const val UNKNOWN_ID = -1
    const val UNKNOWN_TOKEN = "\u25A1"

    /**
     * 从词典数组构建。词典导出约定下标即 id，且保留前缀 0..2 为
     * `<pad>`/`<bos>`/`<eos>`；当词典未携带保留前缀时补齐前缀（后续 token
     * 的 id 相应后移），已携带时原样保留。
     */
    fun fromTokens(tokens: List<String>): LatexTokenizer {
      require(tokens.isNotEmpty()) { "empty latex token dictionary" }
      val hasReservedPrefix = tokens.size > EOS_ID &&
        tokens[PAD_ID] == "<pad>" && tokens[BOS_ID] == "<bos>" && tokens[EOS_ID] == "<eos>"
      val withPrefix = if (hasReservedPrefix) {
        tokens
      } else {
        listOf("<pad>", "<bos>", "<eos>") + tokens
      }
      return LatexTokenizer(withPrefix)
    }

    /** 解析 tokens.json 内容（JSON 字符串数组，允许对象包裹的 `tokens` 键）。 */
    fun fromJsonText(text: String): LatexTokenizer {
      val trimmed = text.trim()
      val array = if (trimmed.startsWith("[")) {
        parseArray(trimmed)
      } else {
        val marker = "\"tokens\""
        val keyIndex = trimmed.indexOf(marker)
        require(keyIndex >= 0) { "tokens.json must be an array or contain a \"tokens\" array" }
        parseArray(trimmed.substring(trimmed.indexOf('[', keyIndex)))
      }
      return fromTokens(array)
    }

    private fun parseArray(text: String): List<String> {
      val tokens = mutableListOf<String>()
      var index = 0
      while (index < text.length) {
        val start = text.indexOf('"', index)
        if (start < 0) break
        val builder = StringBuilder()
        var cursor = start + 1
        while (cursor < text.length) {
          val character = text[cursor]
          if (character == '\\') {
            val escaped = text.getOrNull(cursor + 1)
            when (escaped) {
              'n' -> builder.append('\n')
              't' -> builder.append('\t')
              'r' -> builder.append('\r')
              'u' -> {
                val hex = text.substring(cursor + 2, (cursor + 6).coerceAtMost(text.length))
                builder.append(hex.toIntOrNull(16)?.toChar() ?: ' ')
                cursor += 4
              }
              else -> builder.append(escaped ?: '\\')
            }
            cursor += 2
          } else if (character == '"') {
            break
          } else {
            builder.append(character)
            cursor += 1
          }
        }
        tokens.add(builder.toString())
        index = cursor + 1
      }
      return tokens
    }
  }
}
