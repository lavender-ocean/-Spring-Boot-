package com.sky.service;

import java.util.Map;

/**
 * 秒杀库存对账服务
 */
public interface SeckillReconcileService {

    /**
     * 执行一次对账
     * return 对账结果统计
     */
    Map<String, Object> reconcile();
}