package com.sky.task;

import com.sky.entity.Orders;
import com.sky.service.OrderTimeoutService;
import com.vueUser.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class OrderTask {

    private static final String LOCK_TIMEOUT_TASK = "task:order:timeout";
    private static final String LOCK_DELIVERY_TASK = "task:order:delivery";

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedissonClient redissonClient;

    /** ★ 统一使用 OrderTimeoutService（取消 + 回滚库存的完整逻辑只保留一份） */
    @Autowired
    private OrderTimeoutService orderTimeoutService;

    /**
     * 第三层兜底：扫描超时未支付订单
     * ★ 原 bug：先把订单 update 成 CANCELLED，再调 handler()；handler 里
     *   「状态非待付款 → return」直接跳过库存回滚 → 兜底路径库存永远不回滚。
     *   现在只把 orderId 交给 service，由 service 内部 CAS + 回滚。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        RLock lock = redissonClient.getLock(LOCK_TIMEOUT_TASK);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!acquired) {
                return;
            }
            log.info("【兜底】扫描超时订单：{}", LocalDateTime.now());

            List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(
                    Orders.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(15));

            // ★ 判空顺序修正：先判 null 再 size()
            if (ordersList == null || ordersList.isEmpty()) {
                return;
            }

            for (Orders orders : ordersList) {
                orderTimeoutService.handle(orders.getId());   // 内部 CAS，幂等
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【兜底】获取任务锁被中断");
        } catch (Exception e) {
            log.error("【兜底】处理超时订单失败", e);   // ★ 不再静默吞异常
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 处理长时间处于「派送中」的订单 → 自动完成
     * ★ 原 bug：finally 里条件写成 !acquired，导致拿到锁后永不释放（看门狗一直续期），
     *   该任务每个进程生命周期只会执行一次。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDelierOrder() {
        RLock lock = redissonClient.getLock(LOCK_DELIVERY_TASK);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("【派送超时】其他实例正在执行，跳过");
                return;
            }
            log.info("【派送超时】处理派送中订单：{}", LocalDateTime.now());

            List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(
                    Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now().minusMinutes(60));

            if (ordersList == null || ordersList.isEmpty()) {
                return;
            }

            for (Orders orders : ordersList) {
                Orders update = new Orders();
                update.setId(orders.getId());
                update.setStatus(Orders.COMPLETED);
                // ★ 自动完成不是取消：只写 delivery_time，不写 cancel_reason / cancel_time
                update.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(update);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【派送超时】获取任务锁被中断");
        } catch (Exception e) {
            log.error("【派送超时】处理失败", e);
        } finally {
            // ★ 条件修正：acquired（不是 !acquired）
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}