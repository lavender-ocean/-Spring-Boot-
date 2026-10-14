# 外卖平台（Spring Boot 多模块）

一个基于 Spring Boot 的外卖/点餐后端，在**开源项目**的基础上做了**架构级扩展**：
重点建设秒杀、缓存、消息可靠性、认证鉴权与可观测性，并配套一套压测工具做了性能与正确性验证。

> 说明：基础 CRUD（菜品/套餐/分类/购物车/订单/报表）为项目原有部分，秒杀、二级缓存、MQ 可靠性、
> 双 Token 认证、限流、分布式锁、压测验证等为自行设计与实现。

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Spring Boot 2.7、Spring MVC、MyBatis、PageHelper、AOP |
| 存储 | MySQL、Redis（含 Lua 脚本）、Caffeine（本地缓存） |
| 中间件 | RabbitMQ（死信队列 / 延迟消息插件）、Redisson（分布式锁 / 延迟队列） |
| 认证 | JWT（双 Token：access + refresh 轮换）、Spring Security Crypto（BCrypt） |
| 其他 | WebSocket、Knife4j/Swagger、Druid、Micrometer |

## 核心功能

- **秒杀**：Redis + Lua 原子预扣减（库存判断 + 一人一单判重 + 扣减）、MQ 异步落库、
  MySQL 原子兜底（`stock >= 1`）+ 唯一索引防重，失败双向补偿；定时对账按方向纠偏（只允许下调）。
- **缓存**：Caffeine + Redis 二级缓存，含穿透（空值缓存）/ 击穿（分段锁 + 双重检查）/
  雪崩（TTL 随机抖动）三防，写路径延迟双删。
- **消息可靠性**：Publisher Confirm + Return Callback、手动 ACK、消费幂等（Redis）、
  死信队列 + 本地消息表（`mq_failed_msg`）+ 定时重投 + 管理端人工处置。
- **订单状态机**：条件更新 CAS 流转状态，解决「支付回调 vs 超时取消」竞态；
  订单超时三层兜底（MQ 延迟消息 / Redisson 延迟队列 / 定时扫描）。
- **认证鉴权**：双 Token 轮换 + 登出黑名单 + RBAC（`@RequiresRole` 注解 + AOP）。
- **限流与幂等**：`@RateLimit`（Redis Lua 滑动窗口，Redis 异常降级放行）、`@Idempotent`（Redisson 防重复提交）。

## 目录结构

```
sky-take-out
├── sky-common   # 通用工具、常量、Lua 脚本、外部配置
├── sky-pojo     # 实体 / DTO / VO
├── sky-server   # 启动模块、Controller / Service / Mapper / MQ / 定时任务 / 切面
├── docs/压测     # 自研压测工具（Node，零依赖）+ 压测报告 + 原始数据
└── schema.sql   # 建表脚本
```

## 快速开始

1. 建库并导入表结构与基础数据：`mysql -uroot -p < schema.sql`
2. 启动 MySQL / Redis / RabbitMQ（延迟消息需装 `rabbitmq_delayed_message_exchange` 插件）
3. 准备本地配置：复制模板并填入**你自己的**地址与凭据
   ```bash
   cp sky-server/src/main/resources/application-dev-example.yml \
      sky-server/src/main/resources/application-dev.yml
   ```
   > 仓库中不含任何真实凭据；`application-dev.yml` 已在 `.gitignore` 中，不会被提交。
4. 启动：`mvn -pl sky-server spring-boot:run`（默认 8080，接口文档 `/doc.html`）

## 压测

自研 Node 压测脚本（零依赖，闭模型 + 分位数统计 + 业务码分类）位于 `docs/压测/`，覆盖 4 个场景：

| 场景 | 并发 | 吞吐 | P99 | 关键结论 |
|---|---|---|---|---|
| 缓存读取 | 200 | 6.4k QPS | 68.7 ms | 30 秒 19.3 万请求，MySQL 仅 1 次查询 |
| 缓存击穿 | 100 | 5.7k QPS | 48.9 ms | 2.8 万请求 DB 仅回源 2 次（回源比 14216:1） |
| 秒杀 + 防超卖 | 200 | 3.7k QPS | 134.6 ms | 100 件库存 618 ms 售罄，成功数恰等库存，零超卖 |
| 限流验证 | 20 | 5.6k QPS | 8.8 ms | 5.6 万请求仅放行 30 次（= 3 次/秒 × 10 秒） |

> 均为**单机实测**（应用与中间件同机），仅用于相对比较与正确性验证，不代表生产容量。
> 详见 [`docs/压测/压测报告.md`](docs/压测/压测报告.md)。

## 许可

仅用于学习与个人作品展示。
