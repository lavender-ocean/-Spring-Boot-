package com.sky.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * MQ 幂等工具类
 * 统一幂等判断，避免各消费者重复写
 *
 *  * 注意：checkAndMark 是在"业务处理成功后"才应该被调用。
 *  * 如果业务处理失败，不要调用，让重试可以再次处理
 */
@Slf4j
@Component
public class MqIdempotentHelper {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "mq:idempotent:";
    private static final long DEFAULT_TTL_DAYS = 7;


//    /**
//     * 判断是否已处理过
//     * 只读检查，不写键
//     * @return true = 已处理过（跳过），false = 未处理（继续）
//     */
//    public boolean isProcessed(String bizType, String msgId) {
//        if (msgId == null || msgId.isEmpty()) {
//            log.warn("msgId 为空，无法做幂等判断，默认放行");
//            return false;
//        }
//        String key = KEY_PREFIX + bizType + ":" + msgId;
//        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
//    }
//
//
//    /**
//     *  业务处理成功后调用，标记已处理
//     */
//    public void markProcessed(String bizType, String msgId) {
//        if (msgId == null || msgId.isEmpty()) {
//            return;
//        }
//        String key = KEY_PREFIX + bizType + ":" + msgId;
//        stringRedisTemplate.opsForValue().set(key, "1", DEFAULT_TTL_DAYS, TimeUnit.DAYS);
//        log.debug("【幂等】标记已处理：{}", key);
//    }

    /**
     * 判断是否是第一次处理
     * @param bizType 业务类型（如 "order:timeout", "pay", "notify:ws"）
     * @param msgId   消息唯一 ID
     * @return true = 第一次（继续处理），false = 重复（跳过）
     */
    public boolean isFirstProcess(String bizType, String msgId) {
        if (msgId == null || msgId.isEmpty()) {
            log.warn("msgId 为空，无法做幂等判断，默认放行");
            return true;
        }

        String key = KEY_PREFIX + bizType + ":" + msgId;
        //返回值是Boolean包装类，不要直接用 `if(isFirst)`
        Boolean isFirst = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", DEFAULT_TTL_DAYS, TimeUnit.DAYS);

        if (Boolean.TRUE.equals(isFirst)) {
            log.debug("【幂等】首次处理：{}", key);
            return true;
        } else {
            log.warn("【幂等】重复消息，跳过：{}", key);
            return false;
        }
    }

    /**
     * 业务失败时调用，删除幂等键，允许重试
     * 语义上比 clear 更清晰
     */
    public void rollback(String bizType, String msgId) {
        if (msgId == null || msgId.isEmpty()) return;
        String key = KEY_PREFIX + bizType + ":" + msgId;
        stringRedisTemplate.delete(key);
        log.warn("【幂等】业务失败，回滚幂等键：{}", key);
    }

    /**
     * 手动清除幂等键（用于测试或异常补偿）
     */
    public void clear(String bizType, String msgId) {
        String key = KEY_PREFIX + bizType + ":" + msgId;
        stringRedisTemplate.delete(key);
        log.info("【幂等】清除键：{}", key);
    }
}