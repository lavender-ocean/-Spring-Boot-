package com.sky.utils;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;




/**
 * 二级缓存工具类（Caffeine 本地 + Redis 分布式）
 *
 * 查询顺序：
 *   Caffeine → Redis → DB
 * 写入顺序（回填）：
 *   DB → Redis → Caffeine
 * 删除顺序：
 *   Redis → Caffeine（延迟双删后续批次加）
 */

/**
 * 二级缓存工具类（Caffeine + Redis）
 * 支持：读缓存、写清缓存、延迟双删、按前缀清
 */

@Component
@Slf4j
public class TwoLevelCacheUtil implements Serializable {

    @Autowired
    private Cache<String,Object> caffeineCache;

    @Autowired
    private RedisTemplate redisTemplate;

    /** Redis TTL：10 分钟 */
    private static final long REDIS_TTL_SECONDS = 10 * 60;

    /** TTL 随机抖动：±60 秒（防雪崩） */
    private static final long TTL_JITTER_SECONDS = 60;

    //加入延迟双删
    /** 延迟双删延迟时间（毫秒） */
    private static final long DOUBLE_DELETE_DELAY_MS = 500;

    //延迟双删 线程池
    private final ScheduledExecutorService doubleDeleteScheduled =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r,"cache-double-delete");
                t.setDaemon(true);     // 守护线程，不阻塞 JVM 退出
                return t;
                    }
                    );

    //************************************************************************

    //现在加入 ---- 穿透防护 ---- 击穿防护 ---- 雪崩防护 ----

    //空值缓存 TTL：60 秒（防穿透）
    private static final long NULL_CACHE_TTL_SECONDS = 60;

    // 空值缓存标记（Caffeine 不能存 null）

//    private static final Object NULL_OBJECT = new Object(){
//        @Override
//        public String toString() {
//            return "NULL_CACHE_MARKER";
//
//        }
//    };

    private static final String NULL_OBJECT = "NULL_CACHE_MARKER";

    //  击穿防护 --- 按key分段锁（本项目仅实例使用）

    private static final int LOCK_SEGMENT_COUNT = 64;

    private final ReentrantLock[] lockSegments = new ReentrantLock[LOCK_SEGMENT_COUNT];
    {
        for (int i = 0; i < LOCK_SEGMENT_COUNT; i++) {
            lockSegments[i] = new ReentrantLock();
        }
    }


    //按 key 的 hash 分配锁（避免所有 key 抢一把锁）
    private ReentrantLock getLock(String key) {
        int index = Math.abs(key.hashCode() % LOCK_SEGMENT_COUNT);
        return lockSegments[index];
    }
    //  返回缓存条目数
    public long getCaffeineSize() {
        return caffeineCache.estimatedSize();
    }

    //现在加入可观测数据
    @Autowired
    private MeterRegistry meterRegistry;



    //************************************************************************

    /**
     * 双级缓存查询
     *
     * @param key         缓存 key（推荐用 CacheKeyConstant 生成）
     * @param dbQueryFunc 缓存未命中时的 DB 回查函数
     * @return 数据（可能为 null）
     */

    // 双级缓存查询（含穿透、击穿防护）



    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> dbQueryFunc) {

        // 1. 一级缓存 caffine
        Object caffeineVal = caffeineCache.getIfPresent(key);

        if(caffeineVal != null){
            log.debug("[二级缓存]命中caffeine：{}",caffeineVal);

            // 埋点：Caffeine 命中
            meterRegistry.counter("cache.hit.count", "level", "caffeine").increment();


            //新增 防穿透 ---- 空值标记
            if(caffeineVal.equals(NULL_OBJECT)){
                return null;
            }

            return (T)caffeineVal;
        }

        // 2. 二级缓存Redis
        Object redisVal;
        try {
            redisVal = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("【二级缓存】Redis 查询异常，降级查 DB：{}", key, e);
            redisVal = null;
        }

        // 二级缓存命中 redis
        if(redisVal != null){
            log.debug("【二级缓存】命中Redis:{}",redisVal);


            //  埋点：Redis 命中
            meterRegistry.counter("cache.hit.count", "level", "redis").increment();

            //回填Caffeine
            caffeineCache.put(key,redisVal);
            //新增 防穿透 ---- 空值标记

            if(redisVal.equals(NULL_OBJECT)){
                return null;
            }
            return (T) redisVal;

        }

        //2.5 ========加锁 + 双重检查 ========

        ReentrantLock lock = getLock(key);
        boolean acquire;
        try {
            acquire = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 恢复线程中断标记
            Thread.currentThread().interrupt();
            log.warn("【二级缓存】获取分段锁被中断 key={}", key);
            // 获取锁失败，直接回源数据库
            return dbQueryFunc.get();
        }

        if (!acquire) {
            log.warn("【二级缓存】获取分段锁超时，直接回源DB key={}", key);
            return dbQueryFunc.get();
        }
        // tryLock拿到锁，直接进入try‑finally，不再写 lock.lock()！
        try {
            // 双重检查（防止拿到锁前别人已经回填）
            Object recheck = caffeineCache.getIfPresent(key);
            if (recheck != null) {
                log.debug("【二级缓存】双重检查命中 Caffeine：{}", key);
                if (recheck.equals(NULL_OBJECT)) {
                    return null;
                }
                return (T) recheck;
            }
            //Redis检查
            Object recheckRedis = redisTemplate.opsForValue().get(key);
            if (recheckRedis != null) {
                log.debug("【二级缓存】双重检查命中 Redis：{}", key);
                caffeineCache.put(key, recheckRedis);
                if (recheckRedis.equals(NULL_OBJECT)) {
                    return null;
                }
                return (T) recheckRedis;
            }

            //继续进行 ===========防护修改==========

            //3. 回源DB
            log.debug("【二级缓存】未命中，查询 DB：{}", key);
            T dbData = dbQueryFunc.get();


            // 埋点：DB 回源
            meterRegistry.counter("cache.miss.count").increment();

            //4.回填两级缓存
            if (dbData != null) {
                //正常数据 写正常 TTL
                //先做 Redis
                Long ttl =
                        REDIS_TTL_SECONDS
                                + ThreadLocalRandom.current()
                                .nextLong(-TTL_JITTER_SECONDS, TTL_JITTER_SECONDS);

                try {
                    redisTemplate.opsForValue().set(key, dbData, ttl, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("【二级缓存】Redis 写入异常：{}", key, e);

                }
                //再做 caffeine
                caffeineCache.put(key, dbData);
            }
            else{
                // 穿透防护：DB 也没有 → 缓存空值（短 TTL）

                log.debug("【二级缓存】DB 为空，写入空值缓存：{}", key);

                try {
                    redisTemplate.opsForValue().set(key, NULL_OBJECT,
                            NULL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("【二级缓存】Redis 空值写入异常：{}", key, e);
                }
                caffeineCache.put(key, NULL_OBJECT);


            }

                // 注意：空值不缓存，批次 5 会加空值缓存（防穿透）
                return dbData;

        }finally {
            //一定要 手动来 释放 锁
            lock.unlock();
        }
}



    //==========延迟双删 版本=-===========-=-


    /**
     * 延迟双删（写操作时调用）
     * 1. 立即删 Redis + Caffeine
     * 2. 延迟 500ms 再删 Redis（兜底，防止并发场景下脏数据被回填）
     */
    public void delete(String key) {
        //1.立即删除
        doDelete(key);

        //2.异步延迟 再删除
        doubleDeleteScheduled.schedule( ()-> {
            try{
                redisTemplate.delete(key);
                caffeineCache.invalidate(key); // 补充：本机再次清理Caffeine
                log.debug("【二级缓存】延迟双删执行：{}", key);
            }catch (Exception e){
                log.error("【二级缓存】延迟双删失败：{}", key, e);
            }
        }, DOUBLE_DELETE_DELAY_MS, TimeUnit.MILLISECONDS );
    }

    /**
     * 按前缀删除（分类改名等大范围失效时用）
     *  Redis 用了 KEYS，生产环境需改成 SCAN
     */

    public void deleteByPrefix(String prefix) {
        //1.Redis: 按前缀删除
        try{
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if(keys != null && !keys.isEmpty()){
                redisTemplate.delete(keys);
                log.info("【二级缓存】按前缀删 Redis：{}，共 {} 个", prefix, keys.size());

            }
        } catch (Exception e) {
            log.error("【二级缓存】按前缀删 Redis 异常：{}", prefix, e);
        }

        //2. Caffeine：按前缀删（Caffeine 支持 asMap 遍历）

        try{
            Set<String> caffeineKeys = caffeineCache.asMap().keySet();
            caffeineKeys.removeIf( k -> {
                if(k.startsWith(prefix)){
                    caffeineCache.invalidate(k);
                    return true;
                }
                return false;
            });
            log.info("【二级缓存】按前缀删 Caffeine：{}", prefix);

        } catch (Exception e) {
            log.error("【二级缓存】按前缀删 Caffeine 异常：{}", prefix, e);
        }
    }

    /**
     * 立即删（不延迟，内部用）
     */
    private void doDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("【二级缓存】Redis 删除异常：{}", key, e);
        }
        caffeineCache.invalidate(key);
        log.debug("【二级缓存】立即删除：{}", key);
    }


    //******************************************************************************

    /**
     * 删除缓存（简单版）
     */


    /**
     *     //先删 Redis 再删 Caffeine
     *     public void delete(String key){
     *         //删除 redis
     *         try{
     *             redisTemplate.delete(key);
     *         }catch (Exception e){
     *             log.error("【二级缓存】Redis 删除异常：{}", key, e);
     *         }
     *         //删除 Caffeine
     *         caffeineCache.invalidate(key);
     *         log.info("【二级缓存】删除缓存：{}", key);
     *     }

     */

    // ******************************************************************************

//    /**
//     * 批量删除（按前缀）
//     * TODO
//     *  生产不建议用 KEYS，批次 3 会改成维护 key 索引
//     */
//    public void deleteByPattern(String prefix){
//        try{
//            java.util.Set<String> keys =
//                    redisTemplate.keys(prefix + "*");
//            if(keys != null && !keys.isEmpty()){
//                redisTemplate.delete(keys);
//                log.info("【二级缓存】按前缀删除 Redis：{}，共 {} 个", prefix, keys.size());
//            }
//        }catch (Exception e){
//            log.error("【二级缓存】按前缀删除异常：{}", prefix, e);
//
//        }
//
//        // Caffeine 无法按前缀删，只能全清（简单粗暴但有效）
//        caffeineCache.invalidateAll();
//        log.info("【二级缓存】Caffeine 全清");
//
//    }

    //************************************************************************************

    // 获取 Caffeine 命中率统计（可观测性）
    public CacheStats getCaffeineStats() {
        return caffeineCache.stats();
    }

    /**
     * 优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭二级缓存延迟双删线程池...");
        doubleDeleteScheduled.shutdown();
    }



}




























