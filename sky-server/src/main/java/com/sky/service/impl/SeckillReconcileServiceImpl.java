package com.sky.service.impl;

import com.sky.constant.RedisKeyConstant;
import com.sky.entity.SeckillGoods;
import com.sky.entity.SeckillReconcileLog;
import com.sky.mapper.SeckillGoodsMapper;
import com.sky.mapper.SeckillOrderMapper;
import com.sky.mapper.SeckillReconcileLogMapper;
import com.sky.properties.SeckillReconcileProperties;
import com.sky.service.AlertService;
import com.sky.service.SeckillReconcileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * 秒杀库存对账服务（核心）
 *
 * 关键前提：秒杀链路是「先扣 Redis → 投递 MQ → 再扣 DB」，
 * 所以 Redis 一定"领先于"DB —— diff = Redis - DB < 0 属于**消息在途的正常中间态**，
 * 用 DB 覆盖 Redis 会把已经卖出去的库存补回来（假成功 / 售罄后可抢）。
 *
 * 处理 4 种场景：
 *   1. Redis key 丢失 → 仅在没有在途消息时重新预热，并重建「已购用户集合」
 *   2. diff > 0（Redis 偏多）→ 真异常（Redis 漏扣）→ 允许自动**下调** Redis
 *   3. diff < 0（Redis 偏少）→ 在途或消息丢失 → **绝不覆盖**，只告警
 *   4. diff == 0 → 一致，跳过
 */

/*
 * ===== 以下为改动前的原始注释，保留备查 =====
 * 处理 4 种场景：
 *   1. Redis key 丢失 → 重新预热
 *   2. Redis > DB → 异常（Redis 漏扣了）→ 校正
 *   3. Redis < DB → 可能 MQ 在飞 → 观察
 *   4. 一致 → 跳过
 */
@Service
@Slf4j


public class SeckillReconcileServiceImpl implements SeckillReconcileService {

    @Autowired
    private SeckillReconcileLogMapper seckillReconcileLogMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AlertService alertService;

    @Autowired
    private SeckillReconcileProperties seckillReconcileProperties;



    //用这个来做统一的返回的map的封装
    private Map<String, Object> result(int scanned, int mismatch, int reconciled, int alerted) {
        Map<String, Object> map = new HashMap<>();
        map.put("scanned", scanned);
        map.put("mismatch", mismatch);
        map.put("reconciled", reconciled);
        map.put("alerted", alerted);
        return map;
    }

    /** 在途冷却期：最近这段时间内投递过消息，就认为可能还有请求在飞 */
    private static final long IN_FLIGHT_COOLDOWN_MS = 5 * 60 * 1000L;

    /**
     * 是否有消息在途。
     * SeckillServiceImpl 在成功投递 MQ 后会写 seckill:last_dispatch:{goodsId}（TTL 10 分钟），
     * 这里读它判断「最近是否有人刚抢到、消费者可能还没落库」。
     * 打点缺失或已过期 → 认为无在途（有活动在跑时打点会被不断刷新）。
     */
    private boolean isInFlight(Long goodsId) {
        String last = stringRedisTemplate.opsForValue()
                .get(RedisKeyConstant.seckillLastDispatch(goodsId));
        if (last == null) {
            return false;
        }
        try {
            return System.currentTimeMillis() - Long.parseLong(last) < IN_FLIGHT_COOLDOWN_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 从已成功的秒杀订单重建「已购用户集合」。
     * Redis key 丢失后如果不重建，Redis 层的一人一单会失效（人人都能再抢一次），
     * 只能靠 DB 唯一索引兜底 —— 而那条路径会 rollbackStock 抬高 DB 库存，制造新的偏差。
     */
    private void rebuildUsersSet(Long goodsId) {
        List<Long> userIds = seckillOrderMapper.listUserIdsByGoodsId(goodsId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String userKey = RedisKeyConstant.seckillUsers(goodsId);
        String[] arr = userIds.stream().map(String::valueOf).toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(userKey, arr);
        stringRedisTemplate.expire(userKey, 7, TimeUnit.DAYS);
        log.info("【对账】已重建已购用户集合：goodsId={}, 用户数={}", goodsId, userIds.size());
    }

    //单个活动商品对账
    //return null = 一致（无需记录），否则返回日志
    private  SeckillReconcileLog reconcileOne(SeckillGoods seckillGoods) {

        Long goodsId = seckillGoods.getId();
        Integer dbStock = seckillGoods.getStock();
        String stockKey = RedisKeyConstant.seckillStock(goodsId);

        String redisStockStr = stringRedisTemplate.opsForValue().get(stockKey);

        //场景1：redisKey 丢失
        if(redisStockStr == null) {

            // ★ 修复：key 丢失 + 有在途消息 → 此刻从 DB 重新预热会把已售库存补回来（假成功 / 售罄后可抢），
            //         必须等消费者落库、偏差收敛之后再做（这条分支原来完全绕过阈值判断）
            if (isInFlight(goodsId)) {
                log.warn("【对账】Key 丢失但存在在途消息，跳过重新预热：goodsId={}, DB={}", goodsId, dbStock);
                return SeckillReconcileLog.builder()
                        .goodsId(goodsId)
                        .dbStock(dbStock)
                        .redisStock(null)
                        .diff(null)
                        .scene(SeckillReconcileLog.SCENE_KEY_MISSING)
                        .action(SeckillReconcileLog.ACTION_NONE)   // 只记录，不动数据
                        .reconciled(false)
                        .alerted(false)
                        .createTime(LocalDateTime.now())
                        .build();
            }

            log.warn("【对账】Redis key 丢失：goodsId={}, DB={}", goodsId, dbStock);

            //重新预热：把 DB 的库存写回 Redis（带 TTL，一步到位）
            stringRedisTemplate.opsForValue()
                    .set(stockKey, String.valueOf(dbStock), 7, TimeUnit.DAYS);

            // ===== 以下为改动前的原始实现，保留备查 =====
            // //重新预热
            // //把DB里面的东西，重新加载到redis上
            // stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));
            // //重新设置过期时间
            // stringRedisTemplate.expire(stockKey,7, TimeUnit.DAYS);

            // ★ 修复：同时重建「已购用户集合」，否则 Redis 层一人一单失效
            rebuildUsersSet(goodsId);

            // 告警
            String msg = String.format("Redis key 丢失，已重新预热\n商品ID：%d\nDB 库存：%d",
                    goodsId, dbStock);
            alertService.send(" 秒杀库存 Key 丢失", msg);

            return SeckillReconcileLog.builder()
                    .goodsId(goodsId)
                    .dbStock(dbStock)
                    .redisStock(null)
                    .diff(null)
                    .scene(SeckillReconcileLog.SCENE_KEY_MISSING)
                    .action(SeckillReconcileLog.ACTION_REWARM)
                    .reconciled(true)
                    .alerted(true)
                    .createTime(LocalDateTime.now())
                    .build();


        }


        //把redis里面的库存数量 ---- 转为Integer类型
        Integer redisStock = Integer.parseInt(redisStockStr);
        //diff ---- 差异 就是用redis现在的数量去减DB中查到的数量
        int diff = redisStock - dbStock;

        //场景2：redis和DB的数据一致
        if(diff == 0){
            return null;
        }

        //场景3：redis和DB的数据不一致

        String scene = diff > 0
                ? SeckillReconcileLog.SCENE_REDIS_MORE //redis 的数量多过 DB的 数量
                : SeckillReconcileLog.SCENE_REDIS_LESS; // redis 的数量少于 DB的数量

        //查看数量的差距是否到达了警告的阈值！！！（这个阈值是自己在Properties设置的）
        boolean needAlert = Math.abs(diff) >= seckillReconcileProperties.getAlertThreshold();
        //是否到达自动校正阈值
        boolean needReconcile = Math.abs(diff) >= seckillReconcileProperties.getReconcileThreshold();

        // ★★★ 核心修复：方向判断 ★★★
        // 秒杀是「先扣 Redis → MQ → 再扣 DB」，Redis 必定领先于 DB，
        // 所以 diff < 0（Redis 偏少）是**消息在途的正常中间态**，不是异常。
        // 原代码只看 |diff| 就用 DB 覆盖 Redis = 把已经卖出去的库存补回来
        //   → 用户收到假的"抢购成功"、售罄的商品又变成可抢。
        // 规则：只允许「往下修」（diff > 0 才说明 Redis 真的漏扣了）。
        if (needReconcile && diff < 0) {
            if (isInFlight(goodsId)) {
                log.info("【对账】存在在途消息，跳过校正：goodsId={}, Redis={}, DB={}, diff={}",
                        goodsId, redisStock, dbStock, diff);
            } else {
                // 冷却期内没有投递记录却仍偏少 → 疑似 MQ 消息丢失（Redis 才是对的、DB 少扣了）
                // 这种情况同样不能把 Redis 调高，只能告警人工核查
                log.error("【对账】Redis 长期少于 DB，疑似消息丢失，需人工核查：goodsId={}, Redis={}, DB={}, diff={}",
                        goodsId, redisStock, dbStock, diff);
            }
            needReconcile = false;      // ★ 绝不覆盖
        }

        //****************************************************************************************
        //一开始默认：**不校正、不告警、无动作**。
        //后面代码判断，如果满足校正阈值 → 把`reconciled=true`、`action=ACTION_RECONCILE`
        //满足告警阈值 → `alerted=true`


        String action = SeckillReconcileLog.ACTION_NONE;

        /**
         * **`action`**：本次对账对这个商品执行了**什么动作**
         * 可选常量：
         *
         * - `ACTION_NONE`：无操作（仅仅发现差异，既没有校正，也没有告警）
         * - `ACTION_ALERT`：仅告警，没有校正
         * - `ACTION_RECONCILE`：校正 + 告警
         * - `ACTION_REWARM`：Redis key 丢失，重新预热
         *
         * >
         * > 最终存入`seckill_reconcile_log`表的`action`字段，**方便事后审计排查，看这条记录做了什么操作**。
         */

        boolean reconciled = false;

        /**
         * **`reconciled`（boolean）**：标记**这条记录有没有执行库存校正**
         *
         * - `false`：没有校正
         * - `true`：已经自动把 Redis 库存**下调**为 DB 的值（只允许往下修，不会往上补）
         *   [改动前原文] - `true`：已经自动把 Redis 库存修正为 DB 的值
         *
         * >
         * > 对应数据库 `reconciled tinyint(1)`，存入日志表。
         */

        boolean alerted = false;

        /**
         * **`alerted`（boolean）**：标记**有没有发送告警**
         *
         * - `false`：没告警
         * - `true`：已经发送钉钉 / 日志告警
         *
         * >
         * > 对应数据库 `alerted tinyint(1)`。
         */

        //开始自动校正 --- 但只允许"往下修"（diff > 0）
        // ===== 以下为改动前的原始注释，保留备查 =====
        // //开始自动校正 --- 以DB为主
        if(needReconcile){

            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));

            //  补 TTL
            stringRedisTemplate.expire(stockKey, 7, TimeUnit.DAYS);

            action = SeckillReconcileLog.ACTION_RECONCILE;

            reconciled = true;

            // ===== 以下为改动前的原始实现，保留备查 =====
            // log.error("【对账】自动校正：goodsId={}, Redis 从 {} 改为 {}",
            //         goodsId, redisStock, dbStock);

            log.error("【对账】自动校正（仅下调）：goodsId={}, Redis 从 {} 改为 {}",
                    goodsId, redisStock, dbStock);
        }

        // 是否属于「正常的在途态」：Redis 偏少 + 冷却期内有投递
        boolean inFlightNow = (diff < 0) && isInFlight(goodsId);

        // ===== 以下为改动前的原始实现（无方向判断、无在途过滤），保留备查 =====
        // if(needAlert){
        //     String msg = String.format(
        //             "秒杀库存不一致\n商品ID：%d\nDB：%d\nRedis：%d\n差异：%+d\n已自动校正：%s",
        //             goodsId, dbStock, redisStock, diff, reconciled ? "是" : "否（仅告警）");
        //     alertService.send("秒杀库存不一致",msg);
        //     alerted = true;
        //
        //     if (action.equals(SeckillReconcileLog.ACTION_NONE)) {
        //         action = SeckillReconcileLog.ACTION_ALERT;
        //     }
        //
        // }

        if(needAlert && !inFlightNow){
            String direction = diff > 0
                    ? "Redis 偏多（Redis 漏扣，已自动下调）"
                    : "Redis 偏少（超过冷却期仍偏少，疑似消息丢失，需人工核查）";
            String msg = String.format(
                    "秒杀库存不一致\n商品ID：%d\nDB：%d\nRedis：%d\n差异：%+d\n判定：%s\n已自动校正：%s",
                    goodsId, dbStock, redisStock, diff, direction, reconciled ? "是" : "否（仅告警）");
            alertService.send("秒杀库存不一致",msg);
            alerted = true;

            if (action.equals(SeckillReconcileLog.ACTION_NONE)) {
                action = SeckillReconcileLog.ACTION_ALERT;
            }

        }

        // ===== 以下为改动前的原始实现，保留备查 =====
        // // 日志级别：达到告警阈值用 ERROR，否则 WARN
        // if (needAlert) {
        //     log.error(" 【对账】差异过大：goodsId={}, DB={}, Redis={}, diff={}",
        //             goodsId, dbStock, redisStock, diff);
        // } else {
        //     log.warn(" 【对账】差异观察中：goodsId={}, DB={}, Redis={}, diff={}",
        //             goodsId, dbStock, redisStock, diff);
        // }

        // 日志级别：正常在途降为 INFO；达到告警阈值用 ERROR，否则 WARN
        if (inFlightNow) {
            log.info(" 【对账】Redis 暂时偏少，属正常在途：goodsId={}, DB={}, Redis={}, diff={}",
                    goodsId, dbStock, redisStock, diff);
        } else if (needAlert) {
            log.error(" 【对账】差异过大：goodsId={}, DB={}, Redis={}, diff={}",
                    goodsId, dbStock, redisStock, diff);
        } else {
            log.warn(" 【对账】差异观察中：goodsId={}, DB={}, Redis={}, diff={}",
                    goodsId, dbStock, redisStock, diff);
        }

        return SeckillReconcileLog.builder()
                .goodsId(goodsId)
                .dbStock(dbStock)
                .redisStock(redisStock)
                .diff(diff)
                .scene(scene)
                .action(action)
                .reconciled(reconciled)
                .alerted(alerted)
                .createTime(LocalDateTime.now())
                .build();




    }






    @Override
    public Map<String, Object> reconcile(){

        long startTime = System.currentTimeMillis();

        //1.只去查看现在正在进行中的活动！ 防止全面的扫描大表
        List<SeckillGoods> list = seckillGoodsMapper.listActiveGoods();

        if(list == null || list.isEmpty()){
            return result(0,0,0,0);
        }

        //2.限制单次的处理数量
        if(list.size() > seckillReconcileProperties.getMaxGoodsPerRun() ){

            log.warn("【对账】商品数超过限制，截断为 {} 个",
                    seckillReconcileProperties.getMaxGoodsPerRun());
            list = list.subList(0, seckillReconcileProperties.getMaxGoodsPerRun());

        }

        //3.逐个对账
        List<SeckillReconcileLog> logs = new ArrayList<>();

        //一轮定时任务会遍历一堆秒杀商品，这 3 个是计数器：
        //
        //1. `mismatch`：**本轮扫描中，库存不一致的商品总数**
        //2. `reconciled`：**本轮里面，执行了自动校正的商品数量**
        //3. `alerted`：**本轮里面，触发告警的商品数量**

        int mismatch = 0;
        int reconciled = 0;
        int alerted = 0;


        for(SeckillGoods seckillGoods : list){
            SeckillReconcileLog reconcileLog = reconcileOne(seckillGoods);
            if(reconcileLog == null){
                continue; //说明库存一致 就跳过
            }

            logs.add(reconcileLog);

            mismatch++;

            if (Boolean.TRUE.equals(reconcileLog.getReconciled())) {
                reconciled++;
            }
            if (Boolean.TRUE.equals(reconcileLog.getAlerted())) {
                alerted++;
            }
        }
        // 4. 批量落库
        if (!logs.isEmpty()) {
            try {

                seckillReconcileLogMapper.insertBatch(logs);
            } catch (Exception e) {
                log.error("【对账】日志落库失败", e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("【对账】完成：扫描 {} 个，差异 {} 个，校正 {} 个，告警 {} 个，耗时 {}ms",
                list.size(), mismatch, reconciled, alerted, duration);

        return result(list.size(), mismatch, reconciled, alerted);

    }







}




















