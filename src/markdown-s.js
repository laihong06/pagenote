import './markdown-s.css';
import { RenderScope } from './render-scope';

const previewScopes = new WeakMap();
const livePreviews = new Map();
let removalObserver;
function untrackPreview(block) {
  livePreviews.delete(block);
  if (!livePreviews.size) {
    removalObserver?.disconnect();
    removalObserver = null;
  }
}
function trackPreview(block, stop) {
  livePreviews.set(block, stop);
  if (!removalObserver) {
    removalObserver = new MutationObserver(() => {
      for (const [node, dispose] of livePreviews)
        if (!node.isConnected) dispose();
    });
    removalObserver.observe(document.body, { childList: true, subtree: true });
  }
}
document.addEventListener('visibilitychange', () => {
  if (document.hidden) for (const stop of [...livePreviews.values()]) stop();
});
window.addEventListener('pagenote-render-suspend', () => {
  for (const stop of [...livePreviews.values()]) stop();
});
export function disposeMarkdownS(target) {
  previewScopes.get(target)?.abort();
  previewScopes.delete(target);
  target.querySelectorAll('.mds-frame').forEach((frame) => frame.remove());
}

export const markdownSEngines = Object.freeze({
  mds: 'HTML / CSS / JavaScript',
  'mds-html': 'HTML / CSS / JavaScript',
  'mds-svg': 'SVG / SMIL',
  'mds-canvas': 'Canvas 2D',
  'mds-three': 'Three.js / WebGL',
  'mds-chart': 'SVG 数据图表',
});
const maxSourceLength = 200 * 1024;
const escapeHTML = (s) =>
  String(s).replace(
    /[&<>"']/g,
    (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[
        c
      ],
  );
const inlineScript = (s) =>
  '<script>' + s.replace(/<\/script/gi, '<\\/script') + '</script>';

export function chartHTML(source) {
  const data = JSON.parse(source);
  if (
    !['bar', 'line'].includes(data.type) ||
    !Array.isArray(data.values) ||
    !Array.isArray(data.labels) ||
    data.values.length < 1 ||
    data.values.length > 100 ||
    data.labels.length !== data.values.length ||
    data.values.some(
      (v) => typeof v !== 'number' || !Number.isFinite(v) || v < 0,
    )
  )
    throw Error(
      '图表需要 type: bar 或 line、等长 labels / values 数组，支持 1–100 个非负有限数值。',
    );
  const max = Math.max(1, ...data.values),
    count = data.values.length;
  const points = data.values.map((v, i) => [
    55 + ((i + 0.5) * 660) / count,
    310 - (v / max) * 240,
  ]);
  const marks =
    data.type === 'bar'
      ? points
          .map(
            ([x, y], i) =>
              `<rect x="${x - 230 / count}" y="${y}" width="${460 / count}" height="${310 - y}" rx="3" fill="#4f79ed"><title>${escapeHTML(data.labels[i])}: ${data.values[i]}</title></rect>`,
          )
          .join('')
      : `<polyline points="${points.map((p) => p.join(',')).join(' ')}" fill="none" stroke="#4f79ed" stroke-width="3"/>` +
        points
          .map(
            ([x, y], i) =>
              `<circle cx="${x}" cy="${y}" r="5" fill="#4f79ed"><title>${escapeHTML(data.labels[i])}: ${data.values[i]}</title></circle>`,
          )
          .join('');
  return `<h2>${escapeHTML(data.title || '数据图表')}</h2><svg viewBox="0 0 760 370" role="img" aria-label="${escapeHTML(data.title || '数据图表')}"><path d="M55 55V310H720" fill="none" stroke="#94a3b8"/>${marks}${points.map(([x], i) => `<text x="${x}" y="338" text-anchor="middle" fill="currentColor" font-size="12">${escapeHTML(String(data.labels[i]).slice(0, 24))}</text>`).join('')}<text x="10" y="65" fill="currentColor">${max}</text></svg><table><thead><tr><th>项目</th><th>数值</th></tr></thead><tbody>${data.values.map((v, i) => `<tr><td>${escapeHTML(data.labels[i])}</td><td>${v}</td></tr>`).join('')}</tbody></table>`;
}

export async function markdownSDocument(engine, source) {
  if (!Object.hasOwn(markdownSEngines, engine))
    throw Error('未知 Markdown S 引擎');
  if (new TextEncoder().encode(source).byteLength > maxSourceLength)
    throw Error('组件源码超过限制，请拆成多个组件。');
  const library =
    engine === 'mds-three'
      ? (await import('./generated/markdown-s-three.js')).default
      : '';
  const body = engine === 'mds-chart' ? chartHTML(source) : source;
  // Opaque origin + no host bridge; all resources are inline/offline.
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html{color-scheme:light dark}body{font:15px/1.6 system-ui,sans-serif;margin:20px;overflow-wrap:anywhere}canvas,svg,img{max-width:100%}button,input,select{font:inherit}table{border-collapse:collapse}td,th{padding:4px 12px;text-align:left}#mds-runtime-error{color:#be123c;white-space:pre-wrap}@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;animation-iteration-count:1!important;transition-duration:.01ms!important}}</style>${inlineScript(`addEventListener('error',e=>{const p=document.createElement('pre');p.id='mds-runtime-error';p.textContent='组件错误：'+e.message;document.body.append(p)});addEventListener('unhandledrejection',e=>{const p=document.createElement('pre');p.id='mds-runtime-error';p.textContent='组件错误：'+String(e.reason);document.body.append(p)});`)}${library ? inlineScript(library) : ''}</head><body>${body}</body></html>`;
}

export function renderMarkdownS(target) {
  let scope = previewScopes.get(target);
  if (!scope) {
    scope = new RenderScope();
    scope.activePreviews = new Set();
    previewScopes.set(target, scope);
  }
  for (const code of [...target.querySelectorAll('pre > code')]) {
    const engine = [...code.classList]
      .find((c) => c.startsWith('language-'))
      ?.slice(9)
      .toLowerCase();
    if (!Object.hasOwn(markdownSEngines, engine || '')) continue;
    const block = document.createElement('figure');
    block.className = 'markdown-s-block';
    block.contentEditable = 'false';
    block.dataset.engine = engine;
    block.dataset.source = code.textContent;
    const bar = document.createElement('figcaption');
    const title = document.createElement('strong');
    title.textContent = 'Markdown S · ' + markdownSEngines[engine];
    const run = document.createElement('button');
    run.type = 'button';
    run.textContent = '运行';
    const stop = document.createElement('button');
    stop.type = 'button';
    stop.textContent = '停止';
    stop.disabled = true;
    const status = document.createElement('p');
    status.className = 'mds-status';
    status.setAttribute('role', 'status');
    status.textContent = '点击运行交互预览 · 离线组件';
    const details = document.createElement('details');
    const summary = document.createElement('summary');
    summary.textContent = '查看源码';
    const pre = document.createElement('pre');
    pre.textContent = code.textContent;
    details.append(summary, pre);
    bar.append(title, run, stop);
    block.append(bar, status, details);
    let generation = 0,
      frame,
      task;
    stop.onclick = () => {
      generation++;
      task?.cancel();
      scope.activePreviews.delete(block);
      untrackPreview(block);
      frame?.remove();
      frame = null;
      run.disabled = false;
      stop.disabled = true;
      status.textContent = '已停止；可以重新运行。';
    };
    run.onclick = async () => {
      for (const active of scope.activePreviews)
        if (!active.isConnected) scope.activePreviews.delete(active);
      if (scope.activePreviews.size >= 2 && !scope.activePreviews.has(block)) {
        status.textContent = '最多同时运行两个演示，请先停止一个。';
        return;
      }
      scope.activePreviews.add(block);
      trackPreview(block, () => stop.onclick());
      const current = ++generation;
      task = scope.begin(block, block.dataset.source);
      const runningTask = task;
      frame?.remove();
      run.disabled = true;
      stop.disabled = false;
      status.textContent = '正在启动…';
      try {
        const html = await markdownSDocument(engine, block.dataset.source);
        if (
          !runningTask.current() ||
          current !== generation ||
          !block.isConnected
        )
          return;
        frame = document.createElement('iframe');
        frame.className = 'mds-frame';
        frame.name = 'pagenote-mds-' + crypto.randomUUID();
        frame.title = 'Markdown S ' + markdownSEngines[engine];
        frame.setAttribute('sandbox', 'allow-scripts');
        frame.setAttribute(
          'allow',
          "camera 'none'; microphone 'none'; geolocation 'none'; clipboard-read 'none'; clipboard-write 'none'",
        );
        frame.referrerPolicy = 'no-referrer';
        frame.srcdoc = html;
        details.before(frame);
        status.textContent = '预览已启动 · 停止可释放组件资源';
      } catch (e) {
        if (!runningTask.current() || current !== generation) return;
        status.textContent = '无法运行：' + e.message;
        scope.activePreviews.delete(block);
        untrackPreview(block);
        run.disabled = false;
        stop.disabled = true;
      }
    };
    code.parentElement.replaceWith(block);
  }
}
