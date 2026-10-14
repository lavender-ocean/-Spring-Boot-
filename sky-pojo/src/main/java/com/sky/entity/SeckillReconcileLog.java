package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀库存对账日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillReconcileLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long goodsId;
    private Integer dbStock;
    private Integer redisStock;
    private Integer diff;
    private String scene;         // KEY_MISSING / REDIS_MORE / REDIS_LESS
    private String action;        // NONE / REWARM / RECONCILE / ALERT
    private Boolean reconciled;
    private Boolean alerted;
    private LocalDateTime createTime;

    // ============ 场景常量 ============
    public static final String SCENE_KEY_MISSING = "KEY_MISSING";
    public static final String SCENE_REDIS_MORE = "REDIS_MORE";
    public static final String SCENE_REDIS_LESS = "REDIS_LESS";


    /**
     * > 最终存入`seckill_reconcile_log`表的`action`字段，**方便事后审计排查，看这条记录做了什么操作**。
     */

    // ============ 动作常量 ============
    public static final String ACTION_NONE = "NONE";//无操作（仅仅发现差异，既没有校正，也没有告警）
    public static final String ACTION_REWARM = "REWARM";//Redis key 丢失，重新预热
    public static final String ACTION_RECONCILE = "RECONCILE";//校正 + 告警
    public static final String ACTION_ALERT = "ALERT";//仅告警，没有校正
}