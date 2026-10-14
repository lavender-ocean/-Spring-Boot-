package com.sky.task;

import com.alibaba.fastjson.JSON;
import com.sky.constant.MqConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.mapper.MqFailedMsgMapper;
import com.sky.mq.message.OrderTimeoutMsg;
import com.sky.mq.message.PayCallbackMsg;
import com.sky.mq.message.SeckillMsg;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MqFailedMsgRetryTask {

    @Autowired
    private MqFailedMsgMapper mqFailedMsgMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /** 定时任务锁 key */
    private static final String LOCK_KEY = "task:mq:failed:retry";

    @Scheduled(cron = "0 */5 * * * ?")
    public void retry() {

        RLock rLock = redissonClient.getLock(LOCK_KEY);

        boolean acquired = false;

        try{

            // 不等待，拿不到直接跳过
            acquired = rLock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("【DLQ重投】其他实例正在执行，跳过");
                return;
            }

            log.info("【DLQ重投】开始执行");

            retryByType("seckill", MqConstant.SECKILL_EXCHANGE, MqConstant.SECKILL_ROUTING_KEY, SeckillMsg.class);
            retryByType("pay", MqConstant.PAY_CALLBACK_EXCHANGE, MqConstant.PAY_CALLBACK_ROUTING_KEY, PayCallbackMsg.class);
            retryByType("order:timeout", MqConstant.ORDER_DELAY_EXCHANGE, MqConstant.ORDER_DELAY_ROUTING_KEY, OrderTimeoutMsg.class);


            log.info("【DLQ重投】执行完成");
        }catch (Exception e){
            Thread.currentThread().interrupt();
            log.warn("【DLQ重投】获取锁被中断");

        }finally {
            if (acquired && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }




    }



    private <T> void retryByType(String bizType, String exchange, String routingKey, Class<T> clazz) {
        List<MqFailedMsg> list = mqFailedMsgMapper.listPending(bizType, 50);
        for (MqFailedMsg fm : list) {
            if (fm.getRetryCount() != null && fm.getRetryCount() >= 5) {
                mqFailedMsgMapper.updateStatus(fm.getId(), "GAVE_UP");
                log.error("【DLQ重投】超过 5 次放弃：msgId={}", fm.getMsgId());
                continue;
            }
            try {
                T msg = JSON.parseObject(fm.getPayload(), clazz);
                rabbitTemplate.convertAndSend(exchange, routingKey, msg);

                //  原 bug：只 incrementRetry 不改状态 → 已成功重投的消息每 5 分钟再投一次，
                //   直到 5 次后被误标 GAVE_UP。现在重投即置 RETRIED；
                //   若业务又失败 → 重新进 DLQ → handleDlq 里 requeueIfExists 放回 PENDING 并计数。
                mqFailedMsgMapper.updateStatus(fm.getId(), "RETRIED");
                log.info("【DLQ重投】已重投：msgId={}, retry={}", fm.getMsgId(),
                        (fm.getRetryCount() == null ? 0 : fm.getRetryCount()) + 1);
            } catch (Exception e) {
                log.error("【DLQ重投】失败：{}", fm, e);
            }

        }
    }

//    private <T> void retryByType(String bizType, String exchange, String routingKey, Class<T> clazz) {
//        List<MqFailedMsg> list = mqFailedMsgMapper.listPending(bizType, 50);
//        for (MqFailedMsg fm : list) {
//            // 超过 5 次放弃
//            if (fm.getRetryCount() != null && fm.getRetryCount() >= 5) {
//                mqFailedMsgMapper.updateStatus(fm.getId(), "GAVE_UP");
//                log.error("【DLQ重投】超过 5 次放弃：msgId={}", fm.getMsgId());
//                continue;
//            }
//            try {
//                T msg = JSON.parseObject(fm.getPayload(), clazz);
//                rabbitTemplate.convertAndSend(exchange, routingKey, msg);
//                // 不改状态，只加计数（重投后如果又失败会再进 DLQ）
//                mqFailedMsgMapper.incrementRetry(fm.getId());
//                log.info("【DLQ重投】成功：msgId={}, retry={}", fm.getMsgId(), fm.getRetryCount() + 1);
//            } catch (Exception e) {
//                log.error("【DLQ重投】失败：{}", fm, e);
//            }
//        }
//    }
}