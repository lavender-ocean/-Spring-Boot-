package com.sky.task;

import com.sky.mapper.SeckillReconcileLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 对账日志清理任务（每天凌晨 3 点执行）
 * 删除 30 天前的日志
 */
@Slf4j
@Component
public class SeckillReconcileLogCleanTask {

    @Autowired
    private SeckillReconcileLogMapper reconcileLogMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    public void clean() {
        try {
            reconcileLogMapper.cleanOldLogs();
            log.info("【对账日志】清理 30 天前的记录完成");
        } catch (Exception e) {
            log.error("【对账日志】清理失败", e);
        }
    }
}