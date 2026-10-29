package io.pagenote.feature.ocr

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/** 一个束搜候选：token 序列（不含 BOS，含 EOS 截断）与打分。 */
data class LatexBeamCandidate(
  val tokenIds: IntArray,
  /** 归一化后的对数得分（越大越好；用于排序与展示置信度）。 */
  val score: Double,
) {
  val confidence: Double get() = exp(score).coerceIn(0.0, 1.0)

  override fun equals(other: Any?): Boolean =
    other is LatexBeamCandidate && other.score == score && other.tokenIds.contentEquals(tokenIds)

  override fun hashCode(): Int = tokenIds.contentHashCode() * 31 + score.hashCode()
}

/**
 * 自回归 LaTeX 束搜索（pix2tex 解码侧，纯 Kotlin 可注入打分函数）。
 *
 * - 每步对每个存活束取对数 softmax，累计 log 概率；
 * - 完成候选（产出 EOS）按长度惩罚归一化：`logProb / length^alpha`
 *   （alpha=0 退化为总 log 概率，pix2tex 解码默认按序列概率排序）；
 * - 保留 [beamWidth] 个存活束，去重（相同 token 序列只留最高分）；
 * - 越过 [maxTokens] 仍未 EOS 的束按当前序列收尾（标注截断）。
 */
object LatexBeamDecoder {
  suspend fun decode(
    tokenizer: LatexTokenizer,
    stepLogits: suspend (prefix: IntArray) -> FloatArray,
    beamWidth: Int = 4,
    maxTokens: Int = 256,
    lengthPenalty: Double = 0.0,
  ): List<LatexBeamCandidate> {
    require(beamWidth > 0) { "bad beam width: $beamWidth" }
    require(maxTokens > 0) { "bad max tokens: $maxTokens" }

    data class Beam(val ids: IntArray, val logProb: Double, val done: Boolean)

    var live = listOf(Beam(intArrayOf(tokenizer.bosId), 0.0, false))
    val finished = mutableListOf<Beam>()
    var steps = 0

    while (live.isNotEmpty() && steps < maxTokens) {
      val expanded = mutableListOf<Beam>()
      for (beam in live) {
        val logits = stepLogits(beam.ids)
        val logProbs = logSoftmax(logits)
        val top = topIndices(logProbs, beamWidth)
        for (id in top) {
          val nextIds = beam.ids + id
          val next = Beam(nextIds, beam.logProb + logProbs[id], id == tokenizer.eosId)
          if (next.done) finished.add(next) else expanded.add(next)
        }
      }
      steps += 1
      live = expanded
        .sortedByDescending { it.logProb }
        .distinctBy { it.ids.toList() }
        .take(beamWidth)
      // 存活束退化（全部步进到同一前缀）时提前结束。
      if (live.all { it.ids.size >= maxTokens + 1 }) break
    }
    // 达到步数上限仍未完成的存活束按截断序列收尾。
    finished.addAll(live.map { beam -> Beam(beam.ids, beam.logProb, done = true) })

    return finished
      .filter { it.ids.size > 1 } // 只有 BOS+EOS 的空序列不作为候选
      .map { beam ->
        val body = beam.ids.drop(1).dropLastWhile { it == tokenizer.eosId }.toIntArray()
        LatexBeamCandidate(
          tokenIds = body,
          score = normalized(beam.logProb, body.size, lengthPenalty),
        )
      }
      .distinctBy { it.tokenIds.toList() }
      .sortedByDescending { it.score }
  }

  fun normalized(logProb: Double, length: Int, penalty: Double): Double =
    if (penalty == 0.0 || length <= 1) logProb else logProb / length.toDouble().pow(penalty)

  private fun logSoftmax(logits: FloatArray): DoubleArray {
    val max = logits.maxOrNull() ?: 0f
    var sum = 0.0
    val shifted = DoubleArray(logits.size)
    for (index in logits.indices) {
      shifted[index] = (logits[index] - max).toDouble()
      sum += exp(shifted[index])
    }
    val logSum = ln(sum)
    for (index in shifted.indices) shifted[index] -= logSum
    return shifted
  }

  private fun topIndices(values: DoubleArray, count: Int): IntArray {
    return values.indices.sortedByDescending { values[it] }.take(count).toIntArray()
  }
}
