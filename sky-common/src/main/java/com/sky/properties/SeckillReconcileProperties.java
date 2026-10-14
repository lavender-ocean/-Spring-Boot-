package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀对账配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.seckill.reconcile")
public class SeckillReconcileProperties {

    /** 是否启用对账任务 */
    private boolean enabled = true;

    /** Cron 表达式 */
    private String cron = "0 * * * * ?";

    /** 告警阈值（差异 >= 此值才告警） */
    private int alertThreshold = 5;

    /** 自动校正阈值（差异 >= 此值才自动校正） */
    private int reconcileThreshold = 10;

    /** 锁等待时间（秒），0 = 不等待 */
    private long lockWaitSeconds = 0;

    /** 锁租期（秒） */
    private long lockLeaseSeconds = 30;

    /** 单次最多处理商品数（防大表全扫） */
    private int maxGoodsPerRun = 100;
}