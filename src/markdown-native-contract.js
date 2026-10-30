// Shared static contract for the renderer and AI note validator. No DOM or execution.
export const nativeSourceLimit = 200 * 1024;
export function parseNativeState(source) {
  if (new TextEncoder().encode(source).byteLength > nativeSourceLimit)
    throw Error('原生数据块超过 200 KiB');
  const state = JSON.parse(source);
  if (
    state.version !== 1 ||
    typeof state.id !== 'string' ||
    !state.id.trim() ||
    state.id.length > 80 ||
    state.kind !== 'genetic-lab' ||
    !Array.isArray(state.genes) ||
    state.genes.length !== 12 ||
    state.genes.some((g) => g !== 0 && g !== 1)
  )
    throw Error(
      '需要 version:1、唯一 id、kind:genetic-lab 和 12 个二进制 genes。',
    );
  for (const [field, max] of [
    ['position', 100],
    ['generation', 40],
  ])
    if (
      state[field] !== undefined &&
      (!Number.isFinite(state[field]) || state[field] < 0 || state[field] > max)
    )
      throw Error(field + ' 超出有效范围');
  if (state.angle !== undefined && !Number.isFinite(state.angle))
    throw Error('angle 必须为有限数值');
  if (state.paused !== undefined && typeof state.paused !== 'boolean')
    throw Error('paused 必须为布尔值');
  if (state.generation !== undefined && !Number.isInteger(state.generation))
    throw Error('generation 必须为整数');
  for (const [field, min, max] of [
    ['cut', 1, 11],
    ['selectedIndividual', 0, 35],
    ['seed', 0, 4294967295],
  ])
    if (
      state[field] !== undefined &&
      (!Number.isInteger(state[field]) ||
        state[field] < min ||
        state[field] > max)
    )
      throw Error(field + ' 超出有效范围');
  if (
    state.mutationRate !== undefined &&
    (!Number.isFinite(state.mutationRate) ||
      state.mutationRate < 0 ||
      state.mutationRate > 0.3)
  )
    throw Error('mutationRate 超出有效范围');
  if (state.wheelAngle !== undefined && !Number.isFinite(state.wheelAngle))
    throw Error('wheelAngle 必须为有限数值');

  return state;
}
