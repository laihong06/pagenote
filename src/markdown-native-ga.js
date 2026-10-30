// Algorithm adapted from the founder-provided 遗传算法交互实验室.html.
// Pure data model: no HTML document, DOM globals or iframe execution.
'use strict';
export const GA = (() => {
  const LENGTH = 12;
  const HALF = 6;
  const POP_SIZE = 36;
  const GENERATIONS = 40;
  const ELITES = 2;
  const CROSS_RATE = 0.85;
  const PEAKS = [
    { x: 0.27, y: 0.34, height: 74, width: 0.16 },
    { x: 0.78, y: 0.68, height: 100, width: 0.085 },
  ];
  function randomFromSeed(seed) {
    return function () {
      let t = (seed += 0x6d2b79f5);
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function decode(genes) {
    const xBits = genes.slice(0, HALF).join('');
    const yBits = genes.slice(HALF).join('');
    return { x: parseInt(xBits, 2) / 63, y: parseInt(yBits, 2) / 63 };
  }
  function landscape(x, y) {
    return PEAKS.reduce(
      (sum, peak) =>
        sum +
        peak.height *
          Math.exp(
            -((x - peak.x) ** 2 + (y - peak.y) ** 2) / (2 * peak.width ** 2),
          ),
      0,
    );
  }
  function makeIndividual(genes, id) {
    const copy = genes.map(Number);
    const point = decode(copy);
    return { id, genes: copy, ...point, fitness: landscape(point.x, point.y) };
  }
  function bestOf(population) {
    return population.reduce((best, item) =>
      item.fitness > best.fitness ? item : best,
    );
  }
  function averageFitness(population) {
    return (
      population.reduce((sum, item) => sum + item.fitness, 0) /
      population.length
    );
  }
  function diversity(population) {
    let total = 0;
    for (let i = 0; i < LENGTH; i++) {
      const ones = population.filter((item) => item.genes[i] === 1).length;
      const p = ones / population.length;
      total += 4 * p * (1 - p);
    }
    return (total / LENGTH) * 100;
  }
  function initialPopulation(random, size = POP_SIZE) {
    return Array.from({ length: size }, (_, i) =>
      makeIndividual(
        Array.from({ length: LENGTH }, () => (random() < 0.5 ? 0 : 1)),
        i + 1,
      ),
    );
  }
  function roulette(population, random) {
    const total = population.reduce((sum, item) => sum + item.fitness, 0);
    if (total <= 0) return population[Math.floor(random() * population.length)];
    let threshold = random() * total;
    for (const item of population) {
      threshold -= item.fitness;
      if (threshold <= 0) return item;
    }
    return population[population.length - 1];
  }
  function crossover(a, b, cut) {
    return {
      a: [...a.slice(0, cut), ...b.slice(cut)],
      b: [...b.slice(0, cut), ...a.slice(cut)],
    };
  }
  function mutate(genes, random, rate) {
    return genes.map((gene) => (random() < rate ? 1 - gene : gene));
  }
  function nextGeneration(population, random, rate) {
    const ranked = [...population].sort((a, b) => b.fitness - a.fitness);
    const next = ranked
      .slice(0, ELITES)
      .map((item) => makeIndividual(item.genes, item.id));
    while (next.length < population.length) {
      const parentA = roulette(population, random);
      const parentB = roulette(population, random);
      let children = { a: [...parentA.genes], b: [...parentB.genes] };
      if (random() < CROSS_RATE)
        children = crossover(
          parentA.genes,
          parentB.genes,
          1 + Math.floor(random() * (LENGTH - 1)),
        );
      for (const genes of [children.a, children.b]) {
        if (next.length >= population.length) break;
        next.push(makeIndividual(mutate(genes, random, rate), next.length + 1));
      }
    }
    return next;
  }
  function snapshot(population) {
    const best = bestOf(population);
    return {
      population,
      best,
      average: averageFitness(population),
      diversity: diversity(population),
    };
  }
  function run(seed = 20261102, mutationRate = 0.06) {
    const random = randomFromSeed(seed);
    let population = initialPopulation(random);
    const history = [snapshot(population)];
    for (let generation = 1; generation <= GENERATIONS; generation++) {
      population = nextGeneration(population, random, mutationRate);
      history.push(snapshot(population));
    }
    return {
      seed,
      history,
      settings: {
        length: LENGTH,
        popSize: POP_SIZE,
        generations: GENERATIONS,
        elites: ELITES,
        crossRate: CROSS_RATE,
        mutationRate,
      },
    };
  }
  return {
    LENGTH,
    HALF,
    POP_SIZE,
    GENERATIONS,
    ELITES,
    CROSS_RATE,
    PEAKS,
    randomFromSeed,
    decode,
    landscape,
    makeIndividual,
    bestOf,
    averageFitness,
    diversity,
    initialPopulation,
    roulette,
    crossover,
    mutate,
    nextGeneration,
    run,
  };
})();
