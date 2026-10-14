package com.sky.mq;


import com.sky.constant.QueueConstant;
import com.sky.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;


/**
 * 订单延迟队列消费者
 * 作用：应用启动后，启动一个守护线程持续从队列 take 消息
 */
@Slf4j
@Component
public class OrderDelayQueueConsumer  implements ApplicationRunner {

    @Autowired
    private RedissonClient redisson;

//    @Autowired
//    private OrderTimeoutHandler orderTimeoutHandler;

    @Autowired
    private OrderTimeoutService orderTimeoutService;   // 替换 OrderTimeoutHandler

    private volatile boolean running = true;

    private Thread consumerThread;


    /**
     * 应用启动后自动运行
     */

    @Override
    public void run(ApplicationArguments args) throws Exception {

        RBlockingQueue<Long> rBlockingQueue =
                redisson.getBlockingQueue(QueueConstant.ORDER_DELAY_QUEUE);

        consumerThread = new Thread(() -> {
            log.info(" 订单延迟队列消费者启动，监听：{}", QueueConstant.ORDER_DELAY_QUEUE);
            while (running) {
                try {
                    //  take() 阻塞等待，队列空时挂起，不消耗 CPU
                    Long orderId = rBlockingQueue.take();

                    //拿到东西的之后 说一声
                    log.info(" 从延迟队列取出订单：orderId = {}", orderId);

                    // 交给专门的 handler 处理（单一职责）
                    orderTimeoutService.handle(orderId);


                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("订单延迟队列消费者被中断，退出循环");
                    break;
                } catch (Exception e) {
                    // 消费失败不能让线程挂掉，要 catch 住继续循环
                    log.error("处理延迟订单失败，继续监听", e);
                }
            }
            log.info(" 订单延迟队列消费者已停止");

        }, "order-delay-consumer");
        //设置为守护队列
        consumerThread.setDaemon(true);
        consumerThread.start();
    }



    @PreDestroy
    public void destroy() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("关闭订单延迟队列消费者");
    }
}
