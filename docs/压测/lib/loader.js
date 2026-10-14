'use strict';
/**
 * 闭模型（closed-loop）并发驱动器
 *
 * 模型说明（写报告要写清，否则数字不可比）：
 *   启动 N 个"虚拟用户"，每个用户"发请求 → 等响应 → 再发下一个"，
 *   所以实际压力 = N 个并发在途请求，这与真实用户行为一致。
 *   （另一种是固定速率模型，这里不用：限流/排队场景下速率会被背压扭曲）
 *
 * warmup 的作用：JIT 预热、连接池建连、Caffeine 预热的开销不计入指标
 */
const { Stats } = require('./stats');

async function runLoad({ name, concurrency, durationSec, warmupSec = 3, task }) {
  const stats = new Stats(name);

  async function phase(seconds, record) {
    if (seconds <= 0) return;
    const deadline = Date.now() + seconds * 1000;
    let stop = false;

    const worker = async (id) => {
      while (!stop) {
        const t0 = process.hrtime.bigint();
        let label = 'ok';
        try {
          label = await task(id);
        } catch (e) {
          label = 'EXC:' + String(e.code || e.message).slice(0, 30);
          stats.noteError(String((e && e.stack) || e).split('\n')[0]);
        }
        const t1 = process.hrtime.bigint();

        if (record) {
          stats.record(Number(t1 - t0) / 1e6, label);
          stats.markStart(t0);
          stats.markEnd(t1);
        }
        if (Date.now() >= deadline) stop = true;
      }
    };

    await Promise.all(Array.from({ length: concurrency }, (_, i) => worker(i)));
  }

  console.log(
    `[${name}] warmup ${warmupSec}s → 测量 ${durationSec}s，并发 ${concurrency}（闭模型）`
  );
  await phase(warmupSec, false);
  await phase(durationSec, true);
  return stats;
}

module.exports = { runLoad };
