// A scope owns task identity independently of DOM attributes. Aborting a scope
// invalidates commits even when an underlying renderer cannot be interrupted.
export class RenderScope {
  #controller = new AbortController();
  #tasks = new Map();
  #generation = 0;
  get signal() {
    return this.#controller.signal;
  }
  begin(key, source) {
    this.#tasks.get(key)?.controller.abort();
    const controller = new AbortController();
    const task = { generation: ++this.#generation, source, controller };
    this.#tasks.set(key, task);
    return {
      generation: task.generation,
      signal: controller.signal,
      current: () =>
        !this.signal.aborted &&
        !controller.signal.aborted &&
        this.#tasks.get(key) === task,
      cancel: () => controller.abort(),
    };
  }
  abort() {
    this.#controller.abort();
    for (const task of this.#tasks.values()) task.controller.abort();
    this.#tasks.clear();
  }
}
