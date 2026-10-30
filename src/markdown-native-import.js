import { GA } from './markdown-native-ga';

// Recognizes the supplied teaching template and converts its model to native
// Markdown data. No HTML, external scripts or document code is executed.
export function importNativeLesson(
  html,
  name = 'lesson.html',
  id = crypto.randomUUID(),
) {
  if (new TextEncoder().encode(html).byteLength > 2 * 1024 * 1024)
    throw Error('导入文件超过 2 MiB，请先精简课件。');
  if (
    !/<script\b[^>]*\bid=["']ga-core["']/i.test(html) ||
    !/\bid=["']evolutionMap["']/i.test(html)
  )
    throw Error(
      '尚不能转换此 HTML 模板；未执行文件中的脚本。当前支持提供的遗传算法交互实验室。',
    );
  const seed = 20261102,
    initial = GA.run(seed).history[0].population[0].genes;
  const state = {
    version: 1,
    id,
    kind: 'genetic-lab',
    model: 'ga-reference-v1',
    seed,
    genes: [...initial],
    position: 50,
    generation: 0,
    cut: 6,
    mutationRate: 0.06,
    paused: true,
    angle: 0.15,
    source: { name, conversion: 'native-genetic-template-v1' },
  };
  const manifest = {
    format: 'pagenote-native-lesson',
    version: 1,
    renderer: 'genetic-lab',
    model: 'ga-reference-v1',
    sourceName: name,
    dependencies: [
      { engine: 'svg', provider: 'pagenote' },
      { engine: 'canvas', provider: 'pagenote' },
      { engine: 'three', provider: 'pagenote-bundled' },
    ],
    warnings: [
      '已转换为原生教学模板，不执行或保留原网页脚本；不是任意 HTML/CSS/JS 的通用转换器。',
    ],
  };
  state.manifest = manifest;
  return {
    state,
    manifest,
    markdown:
      '# 遗传算法 · 原生交互实验\n\n直接操作下列图形；变更保存到当前 Markdown。\n\n```markdowns\n' +
      JSON.stringify(state, null, 2) +
      '\n```\n',
  };
}
