import { GA } from './markdown-native-ga';

export function mountNativeLab(getState, commit) {
  let key, run;
  const history = () => {
    const s = getState(),
      next = (s.seed ?? 20261102) + ':' + (s.mutationRate ?? 0.06);
    if (next !== key) {
      key = next;
      run = GA.run(s.seed ?? 20261102, s.mutationRate ?? 0.06);
    }
    return run.history;
  };
  const section = (title) => {
    const node = document.createElement('section'),
      heading = document.createElement('h3');
    heading.textContent = title;
    node.append(heading);
    return node;
  };
  const population = section('02 · 种群与个体'),
    selection = section('04 · 选择轮盘'),
    cross = section('05 · 交叉切点'),
    mutation = section('06 · 变异概率');
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 600 225');
  svg.setAttribute('aria-label', '36 个原生种群个体');
  population.append(svg);
  const wheel = document.createElement('canvas');
  wheel.width = 600;
  wheel.height = 240;
  wheel.tabIndex = 0;
  wheel.setAttribute('aria-label', '适应度选择轮盘，可拖动或使用左右方向键');
  const wheelValue = document.createElement('output');
  selection.append(wheel, wheelValue);
  const offspring = document.createElementNS(svg.namespaceURI, 'svg');
  offspring.setAttribute('viewBox', '0 0 600 125');
  offspring.setAttribute('aria-label', '原生交叉结果');
  const cut = document.createElement('input');
  cut.type = 'range';
  cut.min = '1';
  cut.max = '11';
  cut.setAttribute('aria-label', '交叉切点');
  const cutValue = document.createElement('output');
  cross.append(offspring, cut, cutValue);
  const rate = document.createElement('input');
  rate.type = 'range';
  rate.min = '0';
  rate.max = '30';
  rate.setAttribute('aria-label', '变异概率');
  const rateValue = document.createElement('output'),
    rateText = document.createElement('p');
  mutation.append(rate, rateValue, rateText);
  const snapshot = () => history()[getState().generation ?? 0];
  const selected = () =>
    Math.min(35, Math.max(0, getState().selectedIndividual ?? 0));
  let dragging = false,
    angle = 0;
  function choose(angle) {
    const items = snapshot().population,
      total = items.reduce((sum, item) => sum + item.fitness, 0);
    let threshold =
        (((((-Math.PI / 2 - angle) % (2 * Math.PI)) + 2 * Math.PI) %
          (2 * Math.PI)) /
          (2 * Math.PI)) *
        (total || items.length),
      index = 0;
    for (; index < items.length - 1; index++) {
      threshold -= total ? items[index].fitness : 1;
      if (threshold <= 0) break;
    }
    commit({
      ...getState(),
      wheelAngle: angle,
      selectedIndividual: index,
      genes: [...items[index].genes],
    });
  }
  function drawWheel(rotation) {
    const ctx = wheel.getContext('2d'),
      items = snapshot().population,
      total = items.reduce((sum, item) => sum + item.fitness, 0);
    ctx.clearRect(0, 0, 600, 240);
    let a = rotation;
    items.forEach((item, i) => {
      const end =
        a + (total ? item.fitness / total : 1 / items.length) * Math.PI * 2;
      ctx.beginPath();
      ctx.moveTo(300, 120);
      ctx.arc(300, 120, 99, a, end);
      ctx.closePath();
      ctx.fillStyle = i % 2 ? '#376d70' : '#a44c34';
      ctx.fill();
      ctx.strokeStyle = '#f0ecdf';
      ctx.stroke();
      a = end;
    });
    ctx.fillStyle = '#f0ecdf';
    ctx.beginPath();
    ctx.arc(300, 120, 43, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#293028';
    ctx.font = '12px monospace';
    ctx.textAlign = 'center';
    ctx.fillText('SELECT', 300, 124);
    ctx.fillStyle = '#a44c34';
    ctx.beginPath();
    ctx.moveTo(290, 8);
    ctx.lineTo(310, 8);
    ctx.lineTo(300, 26);
    ctx.closePath();
    ctx.fill();
  }
  wheel.onpointerdown = (e) => {
    dragging = true;
    const r = wheel.getBoundingClientRect();
    angle = Math.atan2(
      e.clientY - r.top - r.height / 2,
      e.clientX - r.left - r.width / 2,
    );
    wheel.setPointerCapture(e.pointerId);
    drawWheel(angle);
  };
  wheel.onpointermove = (e) => {
    if (!dragging) return;
    const r = wheel.getBoundingClientRect();
    angle = Math.atan2(
      e.clientY - r.top - r.height / 2,
      e.clientX - r.left - r.width / 2,
    );
    drawWheel(angle);
  };
  wheel.onpointerup = () => {
    if (!dragging) return;
    dragging = false;
    choose(angle);
  };
  wheel.onkeydown = (e) => {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      e.preventDefault();
      e.stopPropagation();
      choose(
        (getState().wheelAngle ?? 0) + (e.key === 'ArrowLeft' ? -0.25 : 0.25),
      );
    }
  };
  cut.oninput = () => commit({ ...getState(), cut: Number(cut.value) });
  rate.oninput = () =>
    commit({ ...getState(), mutationRate: Number(rate.value) / 100 });
  return {
    population,
    selection,
    cross,
    mutation,
    history,
    update() {
      const state = getState(),
        current = snapshot(),
        items = current.population,
        pick = selected();
      svg.replaceChildren();
      items.forEach((item, i) => {
        const group = document.createElementNS(svg.namespaceURI, 'g');
        group.setAttribute('role', 'button');
        group.setAttribute('tabindex', '0');
        group.setAttribute('aria-label', '个体 ' + (i + 1));
        const rect = document.createElementNS(svg.namespaceURI, 'rect');
        rect.setAttribute('x', String(8 + (i % 6) * 99));
        rect.setAttribute('y', String(8 + Math.floor(i / 6) * 36));
        rect.setAttribute('width', '91');
        rect.setAttribute('height', '29');
        rect.setAttribute('rx', '3');
        rect.setAttribute('fill', i === pick ? '#a44c34' : '#376d70');
        rect.setAttribute(
          'opacity',
          String(0.35 + (0.65 * item.fitness) / 101),
        );
        const text = document.createElementNS(svg.namespaceURI, 'text');
        text.setAttribute('x', String(53 + (i % 6) * 99));
        text.setAttribute('y', String(28 + Math.floor(i / 6) * 36));
        text.setAttribute('fill', '#fff');
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('font-size', '11');
        text.textContent =
          String(i + 1).padStart(2, '0') + ' · ' + item.fitness.toFixed(1);
        group.append(rect, text);
        const select = () =>
          commit({
            ...getState(),
            selectedIndividual: i,
            genes: [...item.genes],
          });
        group.onclick = select;
        group.onkeydown = (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            select();
          }
        };
        svg.append(group);
      });
      drawWheel(state.wheelAngle ?? 0);
      wheelValue.value =
        '选中个体 ' +
        (pick + 1) +
        ' · 适应度 ' +
        items[pick].fitness.toFixed(2);
      cut.value = String(state.cut ?? 6);
      const children = GA.crossover(
        items[pick].genes,
        items[(pick + 1) % items.length].genes,
        Number(cut.value),
      );
      offspring.replaceChildren();
      [children.a, children.b].forEach((genes, row) =>
        genes.forEach((gene, index) => {
          const rect = document.createElementNS(svg.namespaceURI, 'rect');
          rect.setAttribute('x', String(10 + index * 48));
          rect.setAttribute('y', String(10 + row * 56));
          rect.setAttribute('width', '43');
          rect.setAttribute('height', '43');
          rect.setAttribute(
            'fill',
            index < Number(cut.value) ? '#a44c34' : '#376d70',
          );
          const text = document.createElementNS(svg.namespaceURI, 'text');
          text.setAttribute('x', String(31 + index * 48));
          text.setAttribute('y', String(38 + row * 56));
          text.setAttribute('fill', '#fff');
          text.setAttribute('text-anchor', 'middle');
          text.textContent = String(gene);
          offspring.append(rect, text);
        }),
      );
      cutValue.value = '切点 ' + cut.value + ' · 子代 ' + children.a.join('');
      rate.value = String((state.mutationRate ?? 0.06) * 100);
      rateValue.value = rate.value + '%';
      rateText.textContent =
        '至少变异一次的概率 ' +
        ((1 - (1 - (state.mutationRate ?? 0.06)) ** 12) * 100).toFixed(1) +
        '% · 平均 ' +
        (12 * (state.mutationRate ?? 0.06)).toFixed(2) +
        ' 个位点变化。真实四十代演化已按此概率重新计算。';
    },
  };
}
