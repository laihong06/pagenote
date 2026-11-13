// One engine lane. Cancelling/timing out revokes publication, but does not
// pretend that a renderer with no cancellation API has stopped executing.
export class RenderQueue {
  #pending = [];
  #active = false;
  #scheduled = false;
  constructor({ limit = 128 } = {}) {
    this.limit = limit;
  }
  enqueue({
    run,
    signal,
    priority = 0,
    timeout = 20000,
    publish = () => {},
    fail = () => {},
  }) {
    if (signal?.aborted) return Promise.resolve('cancelled');
    if (this.#pending.length >= this.limit) {
      fail(new Error('渲染队列已满，请稍后重试'));
      return Promise.resolve('overflow');
    }
    return new Promise((resolve) => {
      const job = { run, signal, priority, timeout, publish, fail, resolve };
      job.abort = () => {
        const index = this.#pending.indexOf(job);
        if (index >= 0) {
          this.#pending.splice(index, 1);
          signal.removeEventListener('abort', job.abort);
          resolve('cancelled');
        }
      };
      signal?.addEventListener('abort', job.abort, { once: true });
      this.#pending.push(job);
      this.#pending.sort((a, b) => b.priority - a.priority);
      if (!this.#scheduled) {
        this.#scheduled = true;
        queueMicrotask(() => {
          this.#scheduled = false;
          this.#drain();
        });
      }
    });
  }
  async #drain() {
    if (this.#active) return;
    this.#active = true;
    try {
      while (this.#pending.length) {
        const job = this.#pending.shift();
        let expired = false;
        const timer = setTimeout(() => {
          expired = true;
          if (!job.signal?.aborted)
            job.fail(new Error('渲染超时；底层任务结束前继续保持串行'));
          job.resolve('timeout');
        }, job.timeout);
        try {
          if (!job.signal?.aborted) {
            const result = await job.run();
            if (!expired && !job.signal?.aborted) job.publish(result);
          }
          job.resolve(
            expired ? 'timeout' : job.signal?.aborted ? 'cancelled' : 'ready',
          );
        } catch (error) {
          if (!expired && !job.signal?.aborted) job.fail(error);
          job.resolve('error');
        } finally {
          clearTimeout(timer);
          job.signal?.removeEventListener('abort', job.abort);
        }
      }
    } finally {
      this.#active = false;
    }
  }
}
