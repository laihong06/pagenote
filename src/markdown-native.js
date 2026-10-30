import { Marked } from 'marked';
import { GA } from './markdown-native-ga';
import { mountNativeLab } from './markdown-native-lab';
import './markdown-native.css';
import { createNativeChromosome } from './markdown-native-three';

const nativeViews = new Map();
let removalObserver;
export function disposeNativeMarkdown(target) {
  for (const [block, dispose] of nativeViews)
    if (target.contains(block)) {
      dispose();
      nativeViews.delete(block);
    }
}
function trackNative(block, dispose) {
  nativeViews.set(block, dispose);
  if (!removalObserver) {
    removalObserver = new MutationObserver(() => {
      for (const [block, cleanup] of nativeViews)
        if (!block.isConnected) {
          cleanup();
          nativeViews.delete(block);
        }
      if (!nativeViews.size) {
        removalObserver.disconnect();
        removalObserver = null;
      }
    });
    removalObserver.observe(document.body, { childList: true, subtree: true });
  }
}

// Native modules are trusted PageNote renderers; the Markdown contains data,
// not executable document scripts or an embedded HTML file.
export function replaceNativeState(markdown, id, next, expected) {
  const normalized = markdown.replace(/\r\n?/g, '\n');
  const matches = [];
  new Marked().walkTokens(new Marked().lexer(normalized), (token) => {
    if (
      token.type !== 'code' ||
      !['markdowns', 'mds-native'].includes(token.lang?.trim().toLowerCase())
    )
      return;
    try {
      if (JSON.parse(token.text).id === id) matches.push(token);
    } catch {}
  });
  if (matches.length !== 1)
    throw Error('组件标识缺失或重复，未保存以免修改错误位置。');
  const token = matches[0],
    index = normalized.indexOf(token.raw);
  if (index < 0) throw Error('组件源码位置已变化，请重新载入。');
  if (
    expected &&
    JSON.stringify(JSON.parse(token.text)) !== JSON.stringify(expected)
  )
    throw Error('组件已被其他修改更新，请重新载入后再操作。');
  const originalOffset = (offset) => {
    let original = 0,
      normal = 0;
    while (normal < offset) {
      original +=
        markdown[original] === '\r' && markdown[original + 1] === '\n' ? 2 : 1;
      normal++;
    }
    return original;
  };
  const start = originalOffset(index),
    end = originalOffset(index + token.raw.length),
    ending = markdown.slice(start, end).includes('\r\n') ? '\r\n' : '\n';
  const fence = (
    '```markdowns\n' +
    JSON.stringify(next, null, 2) +
    '\n```' +
    (token.raw.match(/\n*$/)?.[0] || '')
  ).replaceAll('\n', ending);
  return markdown.slice(0, start) + fence + markdown.slice(end);
}

export function renderNativeMarkdown(target) {
  for (const code of [...target.querySelectorAll('pre > code')]) {
    const engine = [...code.classList]
      .find((c) => c.startsWith('language-'))
      ?.slice(9);
    if (!['markdowns', 'mds-native'].includes(engine)) continue;
    const block = document.createElement('figure');
    block.className = 'markdown-s-native';
    block.contentEditable = 'false';
    block.dataset.engine = 'markdowns';
    block.dataset.source = code.textContent;
    let state;
    try {
      state = JSON.parse(code.textContent);
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
          (!Number.isFinite(state[field]) ||
            state[field] < 0 ||
            state[field] > max)
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
    } catch (error) {
      block.textContent = 'Markdown S 组件无法读取：' + error.message;
      code.parentElement.replaceWith(block);
      continue;
    }
    const heading = document.createElement('figcaption');
    heading.textContent = '人工演化 · 原生 Markdown S';
    const focus = document.createElement('button');
    focus.type = 'button';
    focus.textContent = '专注作品';
    focus.onclick = async () => {
      try {
        if (document.fullscreenElement === block)
          await document.exitFullscreen();
        else await block.requestFullscreen();
      } catch {
        focus.textContent = '当前环境不支持全屏';
      }
    };
    block.addEventListener('fullscreenchange', () => {
      focus.textContent =
        document.fullscreenElement === block ? '返回笔记' : '专注作品';
    });
    const title = document.createElement('h2');
    title.textContent = '一次改变，看见新的可能';
    const description = document.createElement('p');
    description.textContent =
      '直接点击基因，或调整采样位置。状态写入当前 Markdown，随笔记自动保存。';
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 600 140');
    svg.setAttribute('aria-label', '原生 SVG 染色体');
    const output = document.createElement('output');
    output.setAttribute('aria-live', 'polite');
    const canvas = document.createElement('canvas');
    canvas.width = 600;
    canvas.height = 180;
    canvas.setAttribute('aria-label', '原生 Canvas 适应度地形');
    const label = document.createElement('label');
    label.textContent = '采样位置';
    const input = document.createElement('input');
    input.type = 'range';
    input.min = '0';
    input.max = '100';
    input.value = String(Number.isFinite(state.position) ? state.position : 50);
    input.setAttribute('aria-label', '采样位置');
    const value = document.createElement('output');
    const chartTitle = document.createElement('h3');
    chartTitle.textContent = '迭代轨迹 · 数据图表';
    const chart = document.createElementNS(svg.namespaceURI, 'svg');
    chart.setAttribute('viewBox', '0 0 600 180');
    chart.setAttribute('aria-label', '原生迭代图表');
    const generation = document.createElement('input');
    generation.type = 'range';
    generation.min = '0';
    generation.max = '40';
    generation.value = String(state.generation ?? 0);
    generation.setAttribute('aria-label', '查看世代');
    const generationValue = document.createElement('output');
    const threeTitle = document.createElement('h3');
    threeTitle.textContent = '三维染色体 · 拖动旋转／点击基因';
    const scene = document.createElement('div');
    scene.className = 'markdown-native-scene';
    const pause = document.createElement('button');
    pause.type = 'button';
    let sceneHandle,
      disposed = false;
    const details = document.createElement('details'),
      summary = document.createElement('summary'),
      pre = document.createElement('pre');
    summary.textContent = 'Markdown 数据';
    pre.textContent = block.dataset.source;
    details.append(summary, pre);
    const lab = mountNativeLab(
      () => state,
      (next) => commit(next),
    );
    const commit = (next) => {
      const event = new CustomEvent('markdown-s-native-change', {
        bubbles: true,
        cancelable: true,
        detail: { id: state.id, state: next, previous: state },
      });
      if (!block.dispatchEvent(event)) {
        draw();
        return false;
      }
      state = next;
      block.dataset.source = JSON.stringify(state, null, 2);
      pre.textContent = block.dataset.source;
      draw();
      return true;
    };
    const f = (x) => GA.landscape(x, GA.decode(state.genes).y) / 100;
    let brushing = false,
      brushMoved = false,
      brushStart = 0,
      brushX = 0,
      seen = new Set();
    const geneAt = (x) => {
      const rect = svg.getBoundingClientRect();
      return Math.max(
        0,
        Math.min(
          11,
          Math.floor((((x - rect.left) / rect.width) * 600 - 20) / (560 / 12)),
        ),
      );
    };
    svg.style.touchAction = 'pan-y';
    svg.onpointerdown = (e) => {
      brushing = true;
      brushMoved = false;
      brushStart = geneAt(e.clientX);
      brushX = e.clientX;
      seen = new Set();
    };
    svg.onpointermove = (e) => {
      if (!brushing || Math.abs(e.clientX - brushX) < 4) return;
      brushMoved = true;
      svg.setPointerCapture(e.pointerId);
      const next = geneAt(e.clientX),
        genes = [...state.genes];
      let changed = false;
      for (
        let i = Math.min(brushStart, next);
        i <= Math.max(brushStart, next);
        i++
      )
        if (!seen.has(i)) {
          seen.add(i);
          genes[i] ^= 1;
          changed = true;
        }
      if (changed) commit({ ...state, genes });
    };
    svg.onpointerup = () => {
      brushing = false;
    };
    svg.onpointercancel = () => {
      brushing = false;
    };
    canvas.onclick = (e) => {
      const rect = canvas.getBoundingClientRect();
      commit({
        ...state,
        position: Math.max(
          0,
          Math.min(
            100,
            Math.round(((e.clientX - rect.left) / rect.width) * 100),
          ),
        ),
      });
    };
    chart.onclick = (e) => {
      const rect = chart.getBoundingClientRect();
      commit({
        ...state,
        generation: Math.max(
          0,
          Math.min(
            40,
            Math.round(
              (((e.clientX - rect.left) / rect.width) * 600 - 20) / 14,
            ),
          ),
        ),
      });
    };
    function draw() {
      svg.replaceChildren();
      state.genes.forEach((gene, index) => {
        const group = document.createElementNS(svg.namespaceURI, 'g');
        group.setAttribute('role', 'button');
        group.setAttribute('tabindex', '0');
        group.setAttribute('aria-label', '基因 ' + (index + 1));
        const rect = document.createElementNS(svg.namespaceURI, 'rect');
        rect.setAttribute('x', String((index * 560) / state.genes.length + 20));
        rect.setAttribute('y', '25');
        rect.setAttribute('width', String(480 / state.genes.length));
        rect.setAttribute('height', '80');
        rect.setAttribute('rx', '24');
        rect.setAttribute('fill', gene ? '#a44c34' : '#376d70');
        const text = document.createElementNS(svg.namespaceURI, 'text');
        text.setAttribute(
          'x',
          String(
            (index * 560) / state.genes.length + 20 + 240 / state.genes.length,
          ),
        );
        text.setAttribute('y', '74');
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('fill', '#fcf8ed');
        text.textContent = String(gene);
        group.append(rect, text);
        const flip = () => {
          const genes = [...state.genes];
          genes[index] ^= 1;
          commit({ ...state, genes });
        };
        group.onclick = () => {
          if (!brushMoved) flip();
        };
        group.onkeydown = (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            e.stopPropagation();
            flip();
            svg
              .querySelector('[aria-label="基因 ' + (index + 1) + '"]')
              ?.focus();
          }
        };
        svg.append(group);
      });
      output.value = state.genes.join('');
      input.value = String(state.position ?? 50);
      const x = Number(input.value) / 100;
      value.value = (f(x) * 100).toFixed(2);
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, 600, 180);
      ctx.strokeStyle = '#376d70';
      ctx.lineWidth = 3;
      ctx.beginPath();
      for (let i = 0; i <= 600; i++) {
        const y = 155 - f(i / 600) * 140;
        i ? ctx.lineTo(i, y) : ctx.moveTo(i, y);
      }
      ctx.stroke();
      ctx.fillStyle = '#a44c34';
      ctx.beginPath();
      ctx.arc(x * 600, 155 - f(x) * 140, 7, 0, Math.PI * 2);
      ctx.fill();
      const history = lab.history();
      lab.update();
      chart.replaceChildren();
      const curve = document.createElementNS(svg.namespaceURI, 'polyline');
      curve.setAttribute(
        'points',
        Array.from(
          { length: 41 },
          (_, i) => `${20 + i * 14},${145 - history[i].best.fitness * 1.1}`,
        ).join(' '),
      );
      curve.setAttribute('fill', 'none');
      curve.setAttribute('stroke', '#376d70');
      curve.setAttribute('stroke-width', '3');
      chart.append(curve);
      const current = Number(state.generation ?? 0);
      generation.value = String(current);
      generationValue.value =
        '第 ' +
        current +
        ' 代 · 最佳适应度 ' +
        history[current].best.fitness.toFixed(2);
      const dot = document.createElementNS(svg.namespaceURI, 'circle');
      dot.setAttribute('cx', String(20 + current * 14));
      dot.setAttribute('cy', String(145 - history[current].best.fitness * 1.1));
      dot.setAttribute('r', '7');
      dot.setAttribute('fill', '#a44c34');
      chart.append(dot);
      pause.textContent = state.paused === false ? '暂停旋转' : '继续旋转';
      sceneHandle?.update();
    }
    input.oninput = () => commit({ ...state, position: Number(input.value) });
    generation.oninput = () =>
      commit({ ...state, generation: Number(generation.value) });
    pause.onclick = () =>
      commit({
        ...state,
        paused: state.paused === false,
        angle: sceneHandle?.angle() ?? state.angle ?? 0.15,
      });
    const section = (name, ...children) => {
      const node = document.createElement('section');
      node.dataset.nativeSection = name;
      node.append(...children);
      return node;
    };
    const sections = [
      section('01 编码与变异', svg, output),
      lab.population,
      section('03 适应度地形', label, canvas, input, value),
      lab.selection,
      lab.cross,
      lab.mutation,
      section('07 演化记录', chartTitle, chart, generation, generationValue),
      section('08 三维染色体', threeTitle, scene, pause),
    ];
    const nav = document.createElement('nav');
    nav.setAttribute('aria-label', '原生作品章节');
    sections.forEach((node, i) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent =
        node.dataset.nativeSection || node.querySelector('h3').textContent;
      button.onclick = () =>
        node.scrollIntoView({
          block: 'start',
          behavior: matchMedia('(prefers-reduced-motion:reduce)').matches
            ? 'instant'
            : 'smooth',
        });
      nav.append(button);
    });
    block.append(heading, focus, title, description, nav, ...sections, details);
    draw();
    code.parentElement.replaceWith(block);
    trackNative(block, () => {
      disposed = true;
      sceneHandle?.dispose();
    });
    createNativeChromosome(scene, () => state, commit)
      .then((handle) => {
        if (disposed || !block.isConnected) {
          handle?.dispose();
          return;
        }
        sceneHandle = handle;
      })
      .catch((error) => {
        scene.textContent = '当前设备无法绘制三维视图：' + error.message;
      });
  }
}
