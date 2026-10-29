package io.pagenote.feature.ocr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexBeamDecoderTest {
  private val tokenizer = LatexTokenizer.fromTokens(listOf("a", "b", "c", "=", "x"))

  private fun logitsOf(preferences: Map<Int, Double>, size: Int): FloatArray =
    FloatArray(size) { preferences[it]?.toFloat() ?: -10f }

  @Test
  fun greedyStepFunctionProducesTopScoringSequenceFirst() = runBlocking {
    // 固定打分：任何前缀下 "="(3) 最优且无 EOS —— 解码到步数上限后截断收尾。
    val preferences = mapOf(3 to 5.0, 4 to 4.0, 5 to 3.0, tokenizer.eosId to 0.0)
    val candidates = LatexBeamDecoder.decode(
      tokenizer = tokenizer,
      stepLogits = { logitsOf(preferences, tokenizer.vocabularySize) },
      beamWidth = 2,
      maxTokens = 8,
    )
    assertTrue(candidates.isNotEmpty())
    val best = candidates.first()
    assertArrayEquals(IntArray(8) { 3 }, best.tokenIds)
    assertTrue(candidates[0].score >= candidates[candidates.size - 1].score)
  }

  @Test
  fun eosTerminatesSequencesAndEmptyCandidateIsFiltered() = runBlocking {
    var step = 0
    val candidates = LatexBeamDecoder.decode(
      tokenizer = tokenizer,
      stepLogits = { prefix ->
        // 第一步产出 "a"(id 3)，之后强制 EOS。
        val table = if (prefix.size == 1) mapOf(3 to 5.0) else mapOf(tokenizer.eosId to 9.0)
        step += 1
        logitsOf(table, tokenizer.vocabularySize)
      },
      beamWidth = 1,
      maxTokens = 8,
    )
    assertEquals(1, candidates.size)
    assertArrayEquals(intArrayOf(3), candidates[0].tokenIds)
    assertTrue(step >= 2)
    assertTrue(candidates[0].confidence in 0.0..1.0)
  }

  @Test
  fun prefixDependentScoringSelectsHighestProbabilityPath() = runBlocking {
    val candidates = LatexBeamDecoder.decode(
      tokenizer = tokenizer,
      stepLogits = { prefix ->
        when {
          prefix.size == 1 -> logitsOf(mapOf(3 to 5.0, 4 to 1.0), tokenizer.vocabularySize)
          prefix.lastOrNull() == 3 -> logitsOf(mapOf(4 to 5.0, tokenizer.eosId to 4.0), tokenizer.vocabularySize)
          else -> logitsOf(mapOf(tokenizer.eosId to 1.0), tokenizer.vocabularySize)
        }
      },
      beamWidth = 4,
      maxTokens = 4,
    )
    assertArrayEquals(intArrayOf(3, 4), candidates.first().tokenIds)
  }

  @Test
  fun lengthPenaltyNormalizesScoresButKeepsTheCandidateSet() = runBlocking {
    fun stepFor(prefix: IntArray): FloatArray = when {
      prefix.size == 1 -> logitsOf(mapOf(3 to 4.0, 4 to 3.9), tokenizer.vocabularySize)
      prefix.lastOrNull() == 3 -> logitsOf(mapOf(4 to 4.0, tokenizer.eosId to -1.0), tokenizer.vocabularySize)
      prefix.lastOrNull() == 4 -> logitsOf(mapOf(tokenizer.eosId to 6.0), tokenizer.vocabularySize)
      else -> logitsOf(mapOf(tokenizer.eosId to 0.0), tokenizer.vocabularySize)
    }
    val plain = LatexBeamDecoder.decode(tokenizer, { stepFor(it) }, 4, 4, 0.0)
    val penalized = LatexBeamDecoder.decode(tokenizer, { stepFor(it) }, 4, 4, 1.0)
    assertTrue(plain.isNotEmpty() && penalized.isNotEmpty())
    assertEquals(
      plain.map { it.tokenIds.toList() }.toSet(),
      penalized.map { it.tokenIds.toList() }.toSet(),
    )
    // 归一化改变分值域：同一序列在两种打分下数值不同。
    val plainScore = plain.first { it.tokenIds.contentEquals(intArrayOf(3, 4)) }.score
    val penalizedScore = penalized.first { it.tokenIds.contentEquals(intArrayOf(3, 4)) }.score
    assertTrue(Math.abs(plainScore - penalizedScore) > 1e-9)
  }

  @Test
  fun copyLastPositionLogitsReadsRowFinalStep() {
    val buffer = java.nio.FloatBuffer.wrap(
      floatArrayOf(
        // batch=2, length=2, vocab=3 —— 每行取最后一步。
        0f, 1f, 2f, 3f, 4f, 5f,
        6f, 7f, 8f, 9f, 10f, 11f,
      ),
    )
    val rows = LatexOcrEngine.copyLastPositionLogits(
      buffer = buffer,
      shape = longArrayOf(2, 2, 3),
      batchSize = 2,
      length = 2,
      vocabulary = 3,
    )
    assertEquals(2, rows.size)
    assertTrue(rows[0].contentEquals(floatArrayOf(3f, 4f, 5f)))
    assertTrue(rows[1].contentEquals(floatArrayOf(9f, 10f, 11f)))
  }
}
