package com.sky.mq.consumer;


import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import com.sky.Websocket.WebSocketServer;
import com.sky.constant.MqConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.entity.SeckillOrder;
import com.sky.mapper.MqFailedMsgMapper;
import com.sky.mapper.SeckillGoodsMapper;
import com.sky.mapper.SeckillOrderMapper;
import com.sky.mq.message.SeckillMsg;
import com.sky.service.SeckillProcessService;
import com.sky.service.SeckillService;
import com.sky.utils.MqIdempotentHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlbeans.impl.piccolo.util.DuplicateKeyException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀消息消费者
 * 核心：MySQL 兜底 + 唯一索引防超卖 + WebSocket 通知
 */
@Component
@Slf4j
public class SeckillConsumer {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private SeckillProcessService seckillProcessService;

    //幂等
    @Autowired
    private MqIdempotentHelper mqIdempotentHelper;

    @Autowired
    private MqFailedMsgMapper mqFailedMsgMapper;

    @RabbitListener(queues = MqConstant.SECKILL_QUEUE)
    public void handleSeckillMsg(
            SeckillMsg msg,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) Long deliveryTag
    ) throws IOException {

        if(!mqIdempotentHelper.isFirstProcess("seckill", msg.getMsgId())){
            channel.basicAck(deliveryTag, false);
            return;
        }

        try{
            seckillProcessService.process(msg);
            channel.basicAck(deliveryTag, false);
        }catch(Exception e){
            log.error("【秒杀】业务失败，进 DLQ：{}", msg, e);
            mqIdempotentHelper.rollback("seckill", msg.getMsgId());
            channel.basicNack(deliveryTag, false, false);
        }


    }



//    @Transactional(rollbackFor = Exception.class)
//    @RabbitListener(queues = MqConstant.SECKILL_QUEUE)
//    public void handleSeckillMsg(
//            SeckillMsg seckillMsg
//            , Channel channel
//            ,@Header(AmqpHeaders.DELIVERY_TAG) Long deliveryTag
//    ) throws IOException {
//        log.info("【秒杀消费者】收到消息：userId={}, goodsId={}, orderNo={}",
//                seckillMsg.getUserId(), seckillMsg.getGoodsId(), seckillMsg.getOrderNo());
//
//        //1.先做幂等
////        String idemKey = "mq:idempotent:seckill:" + seckillMsg.getMsgId();
////
////        Boolean isFirst = stringRedisTemplate
////                .opsForValue()
////                .setIfAbsent(idemKey,"1",7, TimeUnit.DAYS);
////
////        if(Boolean.FALSE.equals(isFirst)){
////            log.warn("【秒杀消费者】消息已处理，忽略：msgId = {}", seckillMsg.getMsgId());
////            channel.basicAck(deliveryTag,false);
////            return;
////        }
//
//        if(!mqIdempotentHelper.isFirstProcess("seckill", seckillMsg.getMsgId())){
//            log.warn("【秒杀消费者】消息已处理，忽略：msgId = {}", seckillMsg.getMsgId());
//            channel.basicAck(deliveryTag, false);
//            return;
//        }
//
////
////        if (mqIdempotentHelper.isProcessed("seckill", seckillMsg.getMsgId())) {
////            log.warn("【秒杀消费者】消息已处理，忽略：msgId = {}", seckillMsg.getMsgId());
////            channel.basicAck(deliveryTag, false);
////            return;
////        }
//
//        //2. 然后使用mysql 来做原子扣减 --- 兜底方案
//        try {
//            int affect = seckillGoodsMapper.deductStock(seckillMsg.getGoodsId());
//            //那么按理来说 redis+lua判断之后的stock绝对大于1，才能放进来，
//            //但是也有可能 是 redis和mysql不一致 --- 那么就要再去判断！！！
//            if(affect == 0 ){
//                // MySQL 库存不足 → 说明 Redis 与 DB 不一致
//                log.error("【秒杀消费者】MySQL 库存不足，Redis 与 DB 不一致：goodsId={}, userId={}",
//                        seckillMsg.getGoodsId(), seckillMsg.getUserId());
//
//                //TODO 直接一个定时任务去做大兜底！！！！！！！！！！！！！！----神了
//                //如果定时任务，每隔 1 分钟，扫描 seckill_goods，
//                // 对比 DB 库存和 Redis 库存，发现不一致就做校正。
//                // 这个场景，是不是就适合 Redisson 锁 + SET？
//
//                //补偿 与 更正redis
//                stringRedisTemplate.opsForValue()
//                        .increment("seckill:stock:" + seckillMsg.getGoodsId());
//                stringRedisTemplate.opsForSet()
//                        .remove("seckill:users:"
//                                + seckillMsg.getGoodsId(),String.valueOf(seckillMsg.getUserId()));
//
//                //关键：失败后删键，允许重试
//                mqIdempotentHelper.rollback("seckill", seckillMsg.getMsgId());
//
//                //然后通知用户失败
//                pushResult(seckillMsg.getUserId(), seckillMsg.getOrderNo(), false, "秒杀失败，库存不足");
//                channel.basicAck(deliveryTag, false);
//
//                return;
//
//            }
//
//            // 3.创建秒杀订单（唯一索引兜底）
//            SeckillOrder order = SeckillOrder.builder()
//                    .userId(seckillMsg.getUserId())
//                    .goodsId(seckillMsg.getGoodsId())
//                    .orderNo(seckillMsg.getOrderNo())
//                    .amount(seckillMsg.getAmount())
//                    .status(1)   // 1 成功
//                    .createTime(LocalDateTime.now())
//                    .updateTime(LocalDateTime.now())
//                    .build();
//
//            //下单成功
//            try{
//                seckillOrderMapper.insert(order);
//                log.info("【秒杀消费者】下单成功：userId={}, goodsId={}, orderNo={}",
//                        seckillMsg.getUserId(), seckillMsg.getGoodsId(), seckillMsg.getOrderNo());
//                // 通知用户成功
//                pushResult(seckillMsg.getUserId(), seckillMsg.getOrderNo(), true, "秒杀成功");
//
//            }catch (Exception e) {
//                // 唯一索引冲突（理论不该发生，因为 Redis 已判重）
//                log.warn("【秒杀消费者】唯一索引冲突，回滚 MySQL 库存：userId={}, goodsId={}",
//                        seckillMsg.getUserId(), seckillMsg.getGoodsId());
//
//                //关键：失败后删键，允许重试
//                mqIdempotentHelper.rollback("seckill", seckillMsg.getMsgId());
//
//                // 回滚 MySQL 库存
//                seckillGoodsMapper.rollbackStock(seckillMsg.getGoodsId());
//                pushResult(seckillMsg.getUserId(), seckillMsg.getOrderNo(), false, "重复下单");
//            }
//
//            channel.basicAck(deliveryTag, false);
//        } catch (IOException e) {
//            log.error(" 【秒杀消费者】处理失败：{}", seckillMsg, e);
//
//            //关键：失败后删键，允许重试
//            mqIdempotentHelper.rollback("seckill", seckillMsg.getMsgId());
//            channel.basicNack(deliveryTag, false, false);   // 进 DLQ
//        }
//
//
//    }

    /**
     * 死信队列监听
     */
    @RabbitListener(queues = MqConstant.SECKILL_DLQ_QUEUE)
    public void handleDlq(SeckillMsg msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("【秒杀DLQ】消息进入死信队列，需人工介入：{}", msg);

        try {
            mqFailedMsgMapper.insertIgnore(MqFailedMsg.builder()
                    .bizType("seckill")
                    .msgId(msg.getMsgId())
                    .payload(com.alibaba.fastjson.JSON.toJSONString(msg))
                    .status("PENDING")
                    .retryCount(0)
                    .createTime(java.time.LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("DLQ 落库失败", e);
        }


        channel.basicAck(deliveryTag, false);
    }


    /**
     * websocket来推送结果
     */

    private void pushResult(
            Long userId
            , String orderNo
            , boolean isSuccess
            , String msg) throws IOException {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "SECKILL_RESULT");
            payload.put("orderNo", orderNo);
            payload.put("success", isSuccess);
            payload.put("content", msg);

            webSocketServer.sendToUser(userId, JSON.toJSONString(payload));
            log.info("【秒杀消费者】结果已推送：userId={}, success={}", userId, isSuccess);
        } catch (Exception e) {
            log.error("【秒杀消费者】推送失败：userId={}", userId, e);
        }
    }


}


















