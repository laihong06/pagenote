package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSGeneticAlgorithmTest {
  @Test
  fun producesDeterministicFortyGenerationPopulationForDesktopSeed() {
    val result = MarkdownSGeneticAlgorithm.run(seed = 20261102L, mutationRate = 0.06)

    assertEquals(41, result.history.size)
    assertEquals(36, result.history.first().population.size)
    assertEquals(36, result.history.last().population.size)
    assertEquals(99.97, result.history.last().best.fitness, 0.02)
    assertEquals(result, MarkdownSGeneticAlgorithm.run(seed = 20261102L, mutationRate = 0.06))
  }

  @Test
  fun validatesMutationRateAndCrossoverCut() {
    assertTrue(runCatching { MarkdownSGeneticAlgorithm.run(1, 0.31) }.isFailure)
    val pair = MarkdownSGeneticAlgorithm.crossover(List(12) { 0 }, List(12) { 1 }, 6)
    assertEquals(List(6) { 0 } + List(6) { 1 }, pair.first)
    assertEquals(List(6) { 1 } + List(6) { 0 }, pair.second)
  }

  @Test
  fun evaluatesFitnessLandscapeForPositionAndDecodedGenome() {
    assertEquals(100.05, MarkdownSGeneticAlgorithm.landscape(0.78, 0.68), 0.02)
    val genes = listOf(1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0)
    val (x, y) = MarkdownSGeneticAlgorithm.decode(genes)
    val best = MarkdownSGeneticAlgorithm.run(20261102L, 0.06).history.last().best
    assertEquals(best.fitness, MarkdownSGeneticAlgorithm.landscape(best.x, best.y), 1e-9)
    assertTrue(MarkdownSGeneticAlgorithm.landscape(x, y) in 0.0..100.0)
  }
}
