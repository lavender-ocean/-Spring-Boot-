package com.sky.service;

import com.alibaba.fastjson.JSON;
import com.sky.Websocket.WebSocketServer;
import com.sky.constant.RedisKeyConstant;
import com.sky.entity.SeckillOrder;
import com.sky.mapper.SeckillGoodsMapper;
import com.sky.mapper.SeckillOrderMapper;
import com.sky.mq.message.SeckillMsg;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class SeckillProcessService {


    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 秒杀业务处理
     * 事务范围：扣库存 + 插订单。推送在事务外（best-effort）
     */

    @Transactional(rollbackFor = Exception.class)
    public void process(SeckillMsg msg) {

        //1.扣库存
        int affected = seckillGoodsMapper.deductStock(msg.getGoodsId());

        if (affected == 0) {
            // Redis 与 DB 不一致 → 补偿 Redis
            //???突然之间，DB中的商品有部分坏了，这就导致了redis的数量比db的数量多了！！！
            //然后延迟双删有时间差，那么就需要去做判断db的扣库存是否正常了！！！
            log.error("【秒杀】DB 库存不足，goodsId={}, userId={}",
                    msg.getGoodsId(), msg.getUserId());
            stringRedisTemplate.opsForValue().increment("seckill:stock:" + msg.getGoodsId());
            stringRedisTemplate.opsForSet().remove("seckill:users:" + msg.getGoodsId(),
                    String.valueOf(msg.getUserId()));
            // 推结果放到事务外（推送失败不回滚）
            afterCommitPush(msg, false, "秒杀失败，库存不足");
            return;
        }


        // 2. 插订单
        SeckillOrder order = SeckillOrder.builder()
                .userId(msg.getUserId())
                .goodsId(msg.getGoodsId())
                .orderNo(msg.getOrderNo())
                .amount(msg.getAmount())
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();


        try {
            seckillOrderMapper.insert(order);
            afterCommitPush(msg, true, "秒杀成功");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突 → 回滚库存
            log.warn("【秒杀】唯一索引冲突，回滚库存：userId={}, goodsId={}",
                    msg.getUserId(), msg.getGoodsId());
            seckillGoodsMapper.rollbackStock(msg.getGoodsId());

            // ===== 以下为改动前的原始实现（重复注册了一次推送，会导致 WS 推两条），保留备查 =====
            // afterCommitPush(msg, false, "重复下单");

            //补：Redis 也要回补，否则 Redis 比 DB 少 → 幽灵库存（该商品永远少卖一件）
            try {
                stringRedisTemplate.opsForValue()
                        .increment(RedisKeyConstant.seckillStock(msg.getGoodsId()));

                // ★ 这里必须用 add(SADD)，不能用 remove(SREM)：
                //   能走到 DuplicateKey 说明该用户"已经买过"（唯一索引已存在），
                //   把他塞回已购集合才能持续挡住他；
                //   若删掉他，他会再次穿过 Redis 闸门、又撞唯一索引 → 反复产生噪声，
                //   并让 DB 库存被 rollbackStock 反复抬高。
                stringRedisTemplate.opsForSet().add(
                        RedisKeyConstant.seckillUsers(msg.getGoodsId()),
                        String.valueOf(msg.getUserId()));

                // ===== 以下为改动前的原始实现（SREM，方向相反），保留备查 =====
                // stringRedisTemplate.opsForSet().remove(
                //         RedisKeyConstant.seckillUsers(msg.getGoodsId()),
                //         String.valueOf(msg.getUserId()));
            } catch (Exception ex) {
                log.error("【秒杀】Redis 补偿失败，交给对账兜底：goodsId={}", msg.getGoodsId(), ex);
            }
            afterCommitPush(msg, false, "重复下单");
            // 不抛出：业务上算处理完成
        }



    }






    private void afterCommitPush(SeckillMsg msg, boolean success, String content) {
        // WS 推送 best-effort，失败不影响事务
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("type", "SECKILL_RESULT");
                            payload.put("orderNo", msg.getOrderNo());
                            payload.put("success", success);
                            payload.put("content", content);
                            webSocketServer.sendToUser(msg.getUserId(), JSON.toJSONString(payload));
                        } catch (Exception e) {
                            log.error("【秒杀】推送失败（不影响业务）：userId={}", msg.getUserId(), e);
                        }
                    }
                });
    }


}
