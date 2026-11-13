import assert from 'node:assert/strict';
import { GA } from '../src/markdown-native-ga.js';
const result = GA.run();
assert.equal(result.history.length, 41);
assert.ok(result.history.every((g) => g.population.length === 36));
assert.equal(result.history[40].best.fitness.toFixed(2), '99.97');
assert.deepEqual(
  GA.run(),
  result,
  'same stored seed must reproduce a reopened document',
);
assert.notDeepEqual(
  GA.run(20261102, 0.2).history,
  result.history,
  'mutation rate changes real evolution',
);
for (let i = 1; i < 41; i++)
  assert.ok(
    result.history[i].best.fitness >= result.history[i - 1].best.fitness,
    'elitism retains the previous best',
  );
const genes = '000001010000'.split('').map(Number);
assert.deepEqual(GA.decode(genes), { x: 1 / 63, y: 16 / 63 });
assert.deepEqual(
  GA.mutate(genes, () => 0.5, 0),
  genes,
);
assert.deepEqual(
  GA.mutate(genes, () => 0.5, 1),
  genes.map((g) => 1 - g),
);
assert.equal(
  GA.crossover(Array(12).fill(0), Array(12).fill(1), 6).a.join(''),
  '000000111111',
);
console.log(
  'PASS native GA: 36 individuals, 40 generations, reference 99.97, seed reproduction, mutation, selection model and crossover',
);
