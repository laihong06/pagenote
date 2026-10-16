// Gesture engine adapted from obroccolio/pagenote PR #6, commit 77d30dd.
// Chromium delivers trackpad pinch as Ctrl + wheel. Ordinary wheel events are
// left to the scroller, including diagonal deltas and macOS momentum phases.
export function createReadingGestures({ panes, onError }) {
  let active = null,
    pending = 0,
    pointer,
    frame = 0,
    timer;
  function flush() {
    frame = 0;
    if (!active || !pending) return;
    const pane = active,
      delta = pending;
    pending = 0;
    pane.setScale(
      Math.max(
        pane.min,
        Math.min(pane.max, pane.getScale() * Math.exp(-delta * 0.01)),
      ),
      pointer,
    );
  }
  function finish() {
    clearTimeout(timer);
    if (frame) cancelAnimationFrame(frame);
    flush();
    const pane = active;
    active = null;
    if (pane) Promise.resolve(pane.settle()).catch(onError);
  }
  document.addEventListener(
    'wheel',
    (event) => {
      if (!event.ctrlKey && !event.metaKey) {
        if (active) finish();
        return;
      }
      if (document.querySelector('dialog[open]')) return;
      const pane = panes.find(
        (p) => p.element.contains(event.target) && p.enabled(),
      );
      if (!pane) return;
      event.preventDefault();
      event.stopPropagation();
      if (!Number.isFinite(event.deltaY) || !event.deltaY) return;
      if (active !== pane) {
        finish();
        active = pane;
        pane.start?.();
      }
      // Precise pinch deltas stay proportional. Bound notched mouse wheels so a
      // single Ctrl/Command + wheel notch does not jump across the zoom range.
      const unit =
        event.deltaMode === 1
          ? 16
          : event.deltaMode === 2
            ? pane.element.clientHeight
            : 1;
      pending += Math.max(-10, Math.min(10, event.deltaY * unit));
      pointer = {
        target: event.target,
        clientX: event.clientX,
        clientY: event.clientY,
      };
      if (!frame) frame = requestAnimationFrame(flush);
      clearTimeout(timer);
      timer = setTimeout(finish, 160);
    },
    { passive: false, capture: true },
  );
  return {
    get active() {
      return !!active;
    },
    cancel() {
      clearTimeout(timer);
      if (frame) cancelAnimationFrame(frame);
      frame = pending = 0;
      active = null;
    },
  };
}

export function pageAnchor(scroller, sheet, event) {
  if (!sheet) return null;
  const r = sheet.getBoundingClientRect(),
    s = scroller.getBoundingClientRect();
  const pointer =
    event &&
    (r.width >= scroller.clientWidth - 57 ||
      r.height >= scroller.clientHeight - 57);
  const x = pointer
    ? Math.max(r.left, Math.min(r.right, event.clientX))
    : r.left + r.width / 2;
  const y = pointer
    ? Math.max(r.top, Math.min(r.bottom, event.clientY))
    : r.top + r.height / 2;
  return {
    x: (x - r.left) / r.width,
    y: (y - r.top) / r.height,
    screenX: x - s.left,
    screenY: y - s.top,
  };
}

export function restorePageAnchor(scroller, sheet, anchor) {
  if (!sheet || !anchor) return;
  const r = sheet.getBoundingClientRect(),
    s = scroller.getBoundingClientRect();
  scroller.scrollBy({
    left: r.left + r.width * anchor.x - s.left - anchor.screenX,
    top: r.top + r.height * anchor.y - s.top - anchor.screenY,
    behavior: 'instant',
  });
}
