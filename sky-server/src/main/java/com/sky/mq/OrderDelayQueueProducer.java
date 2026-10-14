package com.sky.mq;


import com.sky.constant.QueueConstant;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * 订单延迟队列生产者
 * 作用：下单成功后，把订单 ID 投递到延迟队列，15 分钟后触发消费
 */
@Slf4j
@Component
public class OrderDelayQueueProducer {

    @Autowired
    private RedissonClient redissonClient;

    //阻塞队列来喽！！！
    private RBlockingQueue<Long> blockingQueue;

    //这个是延迟队列
    private RDelayedQueue<Long> delayedQueue;

    /**
     * 初始化：拿到 RBlockingQueue 和 RDelayedQueue
     * 必须在 @PostConstruct 里初始化，因为 RedissonClient 是注入的
     */
    @PostConstruct
    public void init() {
        this.blockingQueue = redissonClient.getBlockingQueue(QueueConstant.ORDER_DELAY_QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        log.info("订单延迟队列生产者初始化完成：queue = {}", QueueConstant.ORDER_DELAY_QUEUE);
    }


    /**
     * 投递订单到延迟队列
     * @param orderId 订单 ID
     */
    public void offer(Long orderId) {
        try{
            //投递 订单ID 到延迟队列里面去
            delayedQueue.offer(orderId,QueueConstant.ORDER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            log.info("订单已投递到延迟队列：orderId = {}, 延迟 {} 分钟",
                    orderId, QueueConstant.ORDER_TIMEOUT_MINUTES);
        } catch (Exception e) {
            log.error("投递延迟队列失败：orderId = {}", orderId, e);
            // 不抛异常：投递失败不影响下单主流程，有 @Scheduled 兜底
        }


    }

    @PreDestroy
    public void destroy() {
        if (delayedQueue != null) {
            delayedQueue.destroy();
        }
        log.info("关闭订单延迟队列生产者");
    }




}
