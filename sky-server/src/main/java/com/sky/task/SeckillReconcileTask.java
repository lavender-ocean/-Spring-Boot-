package com.sky.task;


import com.sky.constant.LockKeyConstant;
import com.sky.properties.SeckillReconcileProperties;
import com.sky.service.SeckillReconcileService;
import com.sky.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "sky.seckill.reconcile",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SeckillReconcileTask {

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private SeckillReconcileService seckillReconcileService;

    @Autowired
    private SeckillReconcileProperties seckillReconcileProperties;

    @PostConstruct
    public void init() {
        log.info(" 秒杀对账任务已启用：cron={}, alertThreshold={}, reconcileThreshold={}",
                seckillReconcileProperties.getCron(), seckillReconcileProperties.getAlertThreshold(),
                seckillReconcileProperties.getReconcileThreshold());
    }

    /**
     * 定时执行对账
     *  Cron 通过配置注入（Spring @Scheduled 支持 SpEL）
     */
    @Scheduled(cron = "${sky.seckill.reconcile.cron:0 * * * * ?}")
    public void execute() {
        RLock lock = redisson.getLock(LockKeyConstant.TASK_SECKILL_RECONCILE);

        boolean acquired = false;
        try{

            // 只等待 0 秒（或配置值），拿不到直接跳过

            acquired = lock.tryLock(
                    seckillReconcileProperties.getLockWaitSeconds(),
                    seckillReconcileProperties.getLockLeaseSeconds(),
                    TimeUnit.SECONDS
            );



        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【对账任务】获取锁被中断");
            return;
        }

        if (!acquired) {
            log.debug("【对账任务】其他实例正在执行，跳过");
            return;
        }

        //到这里就成功的拿到锁啦！！！
        try{
            Map<String,Object> result = seckillReconcileService.reconcile();

            // 有差异时才打 INFO 日志，避免刷屏
            if ((int) result.get("mismatch") > 0) {
                log.info("【对账任务】执行完成：{}", result);
            }

        }catch (Exception e) {
            log.error("【对账任务】执行异常", e);
            //不抛出，让下一次能正常执行
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }




    }



}





















