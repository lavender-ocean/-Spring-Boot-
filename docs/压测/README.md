# 压测工具（自研，零依赖）

`docs/压测/` 是配套 `.md` 报告的压测工具与原始数据，全部零依赖（只用 Node 内置模块），不污染项目代码。

```
docs/压测/
├── run.js            统一入口（4 个场景）
├── lib/jwt.js        离线签发 HS256 JWT（不登录即可造上千虚拟用户）
├── lib/http.js       带 keep-alive 连接池的请求器
├── lib/stats.js      QPS / 分位数 / 业务码分类统计
├── lib/loader.js     闭模型并发驱动器（含 warmup）
├── setup.sql         测试数据准备（幂等）
├── cleanup.sql       测试数据清理
├── results/          每次运行的原始指标 JSON
└── 压测报告.md        结论、口径、瓶颈分析、JMeter 迁移规划
```

## 快速开始

```bash
# 1. 准备数据（输出 loadtestDishId / loadtestGoodsId）
mysql --default-character-set=utf8mb4 -uroot -p sky_take_out < setup.sql

# 2. 预热秒杀库存（先清 key，否则 setIfAbsent 会跳过）
redis-cli -h <redisHost> del seckill:stock:<goodsId> seckill:users:<goodsId>
curl -X POST -H "token: <adminToken>" http://localhost:8080/admin/seckill/warmup/<goodsId>

# 3. 跑场景
node run.js read      --c=200 --d=30
node run.js seckill   --c=200 --d=30 --goods=<goodsId> --pool=2000
node run.js ratelimit --c=20  --d=10 --goods=<goodsId>
node run.js order     --c=50  --d=15 --dish=<dishId>  --pool=300

# 4. 清理
mysql --default-character-set=utf8mb4 -uroot -p sky_take_out < cleanup.sql
```

## 参数

| 参数 | 说明 |
|---|---|
| `--c` | 并发（虚拟用户数，闭模型） |
| `--d` | 测量时长（秒） |
| `--warmup` | 预热秒数（默认 3，不计入指标） |
| `--pool` | 虚拟用户池大小（默认 2000）**必须远大于 `--c`**，否则会撞"一人一单"和"3 次/秒/用户"限流 |
| `--userBase` | 虚拟用户 id 起点（默认 9001） |
| `--goods` / `--dish` | 秒杀商品 id / 下单菜品 id |
| `BASE`（环境变量） | 目标地址，默认 `http://localhost:8080` |

## 设计要点（关键实现细节）

1. **离线签 token**：压测需要上千虚拟用户，走登录接口既慢又会打满登录限流 → 直接用密钥签 HS256 JWT。
2. **用户池轮询**：秒杀"一人一单"、限流"3 次/秒/用户"都会让单用户压测失真 → 每请求换一个虚拟用户。
3. **闭模型 + warmup**：贴近真实用户行为，且不让 JIT/建连/缓存预热的开销污染指标。
4. **业务码分类**：本项目业务错误也返回 HTTP 200，所以按 `code` / `data.code` 分类统计（抢到/售罄/重复/被限流），避免"错误率 0% 但全是失败"的假象。
5. **keep-alive 连接池**：否则压测机自己会先因 TIME_WAIT / 端口耗尽崩溃。

## 已知坑

- Windows 下 `mysql` 必须加 `--default-character-set=utf8mb4`，否则中文写入报 `Data too long for column`。
- `redis-cli KEYS` 无结果时仍会输出一个空行，用 `wc -l` 计数会把 0 数成 1（本工具不使用 `wc -l` 判断 key 是否存在）。
- `/admin/cache/clear` 是 **GET**（不是 POST），清缓存时别用 POST，否则 405。
- **秒杀测试商品的时间窗口是相对当前时间建的**（setup.sql 里 `NOW()±INTERVAL`）。隔天再跑秒杀场景会得到 `-2 活动未开始或已结束`，需要重新执行 setup.sql 或手动刷新窗口：
  ```sql
  update seckill_goods set stock=100, start_time=now()-interval 1 hour,
         end_time=now()+interval 3 hour, status=1 where name='LOADTEST-SECKILL';
  ```
