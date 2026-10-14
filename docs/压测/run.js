'use strict';
/**
 * 压测统一入口（零依赖，Node 18+）
 *
 * 用法：
 *   node run.js read       --c=200 --d=30
 *   node run.js seckill    --c=200 --d=30 --goods=5 --pool=2000
 *   node run.js order      --c=50  --d=15 --dish=78 --pool=300
 *   node run.js ratelimit  --c=20  --d=10 --goods=5
 *
 * 参数：
 *   --c       并发（虚拟用户数，闭模型）
 *   --d       测量时长（秒）
 *   --warmup  预热时长（秒，默认 3，不计入指标）
 *   --pool    虚拟用户池大小（默认 2000）—— 必须远大于 c，
 *             否则同一个用户会被反复打到"秒杀限流(3次/1s)"和"一人一单"，压出来的是限流结果不是性能
 *   --userBase 虚拟用户 id 起点（默认 9001，别和真实用户撞）
 *   --goods   秒杀商品 id
 *   --dish    下单用的菜品 id
 *   BASE      环境变量，默认 http://localhost:8080
 *
 * 结果：控制台汇总 + docs/压测/results/*.json
 */
const fs = require('fs');
const path = require('path');
const { makeAgent, request } = require('./lib/http');
const { runLoad } = require('./lib/loader');
const { userToken } = require('./lib/jwt');

const BASE = process.env.BASE || 'http://localhost:8080';

function parseArgs(argv) {
  const rest = argv.slice(2);
  const scenario = rest[0] && !rest[0].startsWith('--') ? rest[0] : 'read';
  const a = {
    scenario,
    c: 100,
    d: 30,
    warmup: 3,
    pool: 2000,
    userBase: 9001,
    goods: 0,
    dish: 0,
  };
  for (const arg of rest) {
    const m = /^--([^=]+)=(.*)$/.exec(arg);
    if (!m) continue;
    const k = m[1];
    const raw = m[2];
    a[k] = raw !== '' && !isNaN(Number(raw)) ? Number(raw) : raw;
  }
  return a;
}

// 虚拟用户 token 缓存：每个 userId 只签一次 HMAC
const tokenCache = new Map();
function tokenFor(userId) {
  let t = tokenCache.get(userId);
  if (!t) {
    t = userToken(userId);
    tokenCache.set(userId, t);
  }
  return t;
}

function bucketOrderResult(json) {
  const msg = json.msg || '';
  if (msg.includes('库存不足')) return 'sold_out';
  if (msg.includes('购物车')) return 'empty_cart';
  if (msg.includes('地址')) return 'addr_error';
  return 'biz_err:' + msg.slice(0, 20);
}

async function main() {
  const a = parseArgs(process.argv);
  const agent = makeAgent(a.c);
  let seq = 0;
  const nextUser = () => a.userBase + (seq++ % a.pool);
  let task;
  let title;

  switch (a.scenario) {
    case 'read': {
      title = '缓存读取（/user/category/list + /user/dish/list，走二级缓存）';
      const paths = ['/user/category/list?type=1', '/user/dish/list?categoryId=11'];
      const token = tokenFor(1);
      task = async (id) => {
        const r = await request(agent, { url: BASE + paths[id % paths.length], token });
        if (r.status !== 200) return 'http:' + r.status;
        if (!r.json) return 'bad_json';
        return r.json.code === 1 ? 'ok' : 'biz:' + r.json.code;
      };
      break;
    }

    case 'seckill': {
      if (!a.goods) throw new Error('秒杀场景必须指定 --goods=<goodsId>');
      title = `秒杀抢购（goodsId=${a.goods}，用户池=${a.pool}）`;
      task = async () => {
        const token = tokenFor(nextUser());
        const r = await request(agent, {
          method: 'POST',
          url: `${BASE}/user/seckill/${a.goods}`,
          token,
        });
        if (r.status !== 200) return 'http:' + r.status;
        if (!r.json) return 'bad_json';
        if (r.json.code !== 1) return 'rate_limited'; // @RateLimit 拦截
        const inner = r.json.data ? r.json.data.code : null;
        return 'biz:' + inner; // 1=抢到 0=售罄 -1=重复
      };
      break;
    }

    case 'order': {
      if (!a.dish) throw new Error('下单场景必须指定 --dish=<dishId>');
      title = `下单主链路（加购 + 提交订单，用户池=${a.pool}）`;
      task = async () => {
        const userId = nextUser();
        const token = tokenFor(userId);
        // 第 1 步：加购（不计入延迟；下单接口会清空购物车，所以每轮都得先加）
        await request(agent, {
          method: 'POST',
          url: BASE + '/user/shoppingCart/add',
          token,
          body: { dishId: a.dish },
        });
        // 第 2 步：提交订单（被测目标；地址簿按 user_id 一对一预置，见 setup.sql）
        const r = await request(agent, {
          method: 'POST',
          url: BASE + '/user/order/submit',
          token,
          body: { addressBookId: userId, payMethod: 1 },
        });
        if (r.status !== 200) return 'http:' + r.status;
        if (!r.json) return 'bad_json';
        return r.json.code === 1 ? 'ok' : bucketOrderResult(r.json);
      };
      break;
    }

    case 'ratelimit': {
      if (!a.goods) throw new Error('限流场景必须指定 --goods=<goodsId>');
      title = `限流验证（同一用户 userId=1 高频打秒杀接口）`;
      const token = tokenFor(1);
      task = async () => {
        const r = await request(agent, {
          method: 'POST',
          url: `${BASE}/user/seckill/${a.goods}`,
          token,
        });
        if (r.status !== 200) return 'http:' + r.status;
        if (!r.json) return 'bad_json';
        if (r.json.code !== 1) return 'rate_limited';
        const inner = r.json.data ? r.json.data.code : null;
        return inner === -1 ? 'dup' : 'passed_biz:' + inner;
      };
      break;
    }

    default:
      throw new Error('未知场景：' + a.scenario);
  }

  const stats = await runLoad({
    name: title,
    concurrency: a.c,
    durationSec: a.d,
    warmupSec: a.warmup,
    task,
  });

  const json = stats.print();

  const dir = path.join(__dirname, 'results');
  fs.mkdirSync(dir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const file = path.join(dir, `${stamp}-${a.scenario}-c${a.c}.json`);
  fs.writeFileSync(file, JSON.stringify({ args: a, result: json }, null, 2), 'utf8');
  console.log('结果已写入：' + file);

  agent.destroy();
}

main().catch((e) => {
  console.error('[压测失败]', e.message || e);
  process.exit(1);
});
