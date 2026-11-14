package io.pagenote.feature.notes

import kotlin.math.exp

data class MarkdownSIndividual(
  val id: Int,
  val genes: List<Int>,
  val x: Double,
  val y: Double,
  val fitness: Double,
)

data class MarkdownSGeneration(
  val population: List<MarkdownSIndividual>,
  val best: MarkdownSIndividual,
  val average: Double,
  val diversity: Double,
)

data class MarkdownSRun(val seed: Long, val history: List<MarkdownSGeneration>, val mutationRate: Double)

object MarkdownSGeneticAlgorithm {
  const val GENE_COUNT = 12
  const val POPULATION_SIZE = 36
  const val GENERATIONS = 40
  private const val CROSS_RATE = 0.85
  private val peaks = listOf(
    Triple(0.27, 0.34, 74.0) to 0.16,
    Triple(0.78, 0.68, 100.0) to 0.085,
  )

  fun decode(genes: List<Int>): Pair<Double, Double> {
    require(genes.size == GENE_COUNT && genes.all { it in 0..1 })
    val x = genes.take(6).joinToString("").toInt(2) / 63.0
    val y = genes.drop(6).joinToString("").toInt(2) / 63.0
    return x to y
  }

  fun crossover(first: List<Int>, second: List<Int>, cut: Int): Pair<List<Int>, List<Int>> {
    require(first.size == GENE_COUNT && second.size == GENE_COUNT && cut in 1 until GENE_COUNT)
    return (first.take(cut) + second.drop(cut)) to (second.take(cut) + first.drop(cut))
  }

  fun landscape(x: Double, y: Double): Double {
    require(x.isFinite() && y.isFinite())
    return peaks.sumOf { (peak, width) ->
      peak.third * exp(-((x - peak.first) * (x - peak.first) + (y - peak.second) * (y - peak.second)) / (2.0 * width * width))
    }
  }

  fun run(seed: Long, mutationRate: Double): MarkdownSRun {
    require(mutationRate.isFinite() && mutationRate in 0.0..0.3)
    val random = SeededRandom(seed)
    var population = List(POPULATION_SIZE) { index ->
      individual(List(GENE_COUNT) { if (random.next() < 0.5) 0 else 1 }, index + 1)
    }
    val history = mutableListOf(snapshot(population))
    repeat(GENERATIONS) {
      val ranked = population.sortedByDescending { it.fitness }
      val next = ranked.take(2).map { individual(it.genes, it.id) }.toMutableList()
      while (next.size < POPULATION_SIZE) {
        val parentA = roulette(population, random)
        val parentB = roulette(population, random)
        var children = parentA.genes to parentB.genes
        if (random.next() < CROSS_RATE) {
          children = crossover(parentA.genes, parentB.genes, 1 + (random.next() * (GENE_COUNT - 1)).toInt())
        }
        listOf(children.first, children.second).forEach { child ->
          if (next.size < POPULATION_SIZE) {
            val mutated = child.map { gene -> if (random.next() < mutationRate) 1 - gene else gene }
            next += individual(mutated, next.size + 1)
          }
        }
      }
      population = next
      history += snapshot(population)
    }
    return MarkdownSRun(seed, history, mutationRate)
  }

  private fun roulette(population: List<MarkdownSIndividual>, random: SeededRandom): MarkdownSIndividual {
    val total = population.sumOf { it.fitness }
    if (total <= 0.0) return population[(random.next() * population.size).toInt()]
    var threshold = random.next() * total
    for (item in population) {
      threshold -= item.fitness
      if (threshold <= 0.0) return item
    }
    return population.last()
  }

  private fun individual(genes: List<Int>, id: Int): MarkdownSIndividual {
    val (x, y) = decode(genes)
    val fitness = landscape(x, y)
    return MarkdownSIndividual(id, genes.toList(), x, y, fitness)
  }

  private fun snapshot(population: List<MarkdownSIndividual>): MarkdownSGeneration {
    val best = population.maxBy { it.fitness }
    val average = population.sumOf { it.fitness } / population.size
    val diversity = (0 until GENE_COUNT).sumOf { geneIndex ->
      val p = population.count { it.genes[geneIndex] == 1 }.toDouble() / population.size
      4 * p * (1 - p)
    } / GENE_COUNT * 100
    return MarkdownSGeneration(population.toList(), best, average, diversity)
  }

  private class SeededRandom(seed: Long) {
    private var state = seed.toInt()
    fun next(): Double {
      state += 0x6d2b79f5
      var value = state
      value = (value xor (value ushr 15)) * (value or 1)
      value = value xor (value + (value xor (value ushr 7)) * (value or 61))
      return ((value xor (value ushr 14)).toUInt().toLong() / 4_294_967_296.0)
    }
  }
}
