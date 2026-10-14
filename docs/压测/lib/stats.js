'use strict';
/**
 * 压测指标采集器
 *
 * 记录：总请求数、分类计数（成功/业务码/HTTP 错误）、延迟样本、QPS、分位数
 * 口径说明（写报告时要照抄，避免"数字好看但口径不明"）：
 *   - QPS  = 完成请求数 / 统计窗口（不含 warmup）
 *   - P99  = 全部延迟样本升序后 99% 位置的值（不是平均值，避免被长尾掩盖）
 *   - 失败率 = 非预期结果数 / 总数（业务语义上的失败，不只看 HTTP 状态码）
 */

class Stats {
  constructor(name) {
    this.name = name;
    this.total = 0;
    this.latencies = []; // 单位 ms
    this.labels = new Map(); // 结果分类计数
    this.firstStartNs = null;
    this.lastEndNs = null;
    this.errorSamples = [];
  }

  /** 按请求记录一条：latencyMs = 该请求耗时，label = 结果分类（ok / biz:0 / http_500 ...） */
  record(latencyMs, label) {
    this.total++;
    this.latencies.push(latencyMs);
    this.labels.set(label, (this.labels.get(label) || 0) + 1);
  }

  noteError(msg) {
    if (this.errorSamples.length < 10) this.errorSamples.push(msg);
  }

  markStart(ns) {
    if (this.firstStartNs === null) this.firstStartNs = ns;
  }

  markEnd(ns) {
    this.lastEndNs = ns;
  }

  get durationSec() {
    if (this.firstStartNs === null || this.lastEndNs === null) return 0;
    return Number(this.lastEndNs - this.firstStartNs) / 1e9;
  }

  get qps() {
    const d = this.durationSec;
    return d > 0 ? this.total / d : 0;
  }

  /** 分位数：p 取 0~1；用 nearest-rank，避免插值误差对不上原始样本 */
  percentile(p) {
    if (this.latencies.length === 0) return 0;
    const sorted = this.getSorted();
    const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil(p * sorted.length) - 1));
    return sorted[idx];
  }

  getSorted() {
    if (!this._sorted) {
      this._sorted = this.latencies.slice().sort((a, b) => a - b);
    }
    return this._sorted;
  }

  mean() {
    if (this.latencies.length === 0) return 0;
    let s = 0;
    for (const v of this.latencies) s += v;
    return s / this.latencies.length;
  }

  max() {
    const sorted = this.getSorted();
    return sorted.length ? sorted[sorted.length - 1] : 0;
  }

  countByPrefix(prefix) {
    let n = 0;
    for (const [k, v] of this.labels) if (k.startsWith(prefix)) n += v;
    return n;
  }

  /** 成功数：label 为 'ok' 或 'biz:1'（秒杀抢到也算成功） */
  get okCount() {
    let n = 0;
    for (const [k, v] of this.labels) {
      if (k === 'ok' || k === 'biz:1') n += v;
    }
    return n;
  }

  toJSON() {
    return {
      name: this.name,
      total: this.total,
      ok: this.okCount,
      durationSec: +this.durationSec.toFixed(2),
      qps: +this.qps.toFixed(1),
      latencyMs: {
        mean: +this.mean().toFixed(2),
        p50: +this.percentile(0.5).toFixed(2),
        p90: +this.percentile(0.9).toFixed(2),
        p95: +this.percentile(0.95).toFixed(2),
        p99: +this.percentile(0.99).toFixed(2),
        max: +this.max().toFixed(2),
      },
      labels: Object.fromEntries([...this.labels.entries()].sort((a, b) => b[1] - a[1])),
      errorSamples: this.errorSamples,
    };
  }

  print() {
    const j = this.toJSON();
    console.log('');
    console.log(`================= 场景：${j.name} =================`);
    console.log(`总请求数 : ${j.total}    完成耗时 : ${j.durationSec}s`);
    console.log(`QPS      : ${j.qps}`);
    console.log(`延迟(ms) : mean=${j.latencyMs.mean}  p50=${j.latencyMs.p50}  p90=${j.latencyMs.p90}  p95=${j.latencyMs.p95}  p99=${j.latencyMs.p99}  max=${j.latencyMs.max}`);
    console.log(`结果分布 :`);
    for (const [k, v] of Object.entries(j.labels)) {
      console.log(`   ${k.padEnd(28)} ${v}  (${((v / j.total) * 100).toFixed(1)}%)`);
    }
    if (j.errorSamples.length) {
      console.log(`异常样本 : ${JSON.stringify(j.errorSamples.slice(0, 3))}`);
    }
    console.log('====================================================');
    return j;
  }
}

module.exports = { Stats };
