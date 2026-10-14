package com.sky.config;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置
 */

@Configuration
@Slf4j
public class CaffeineConfig {


    /**
     * 一级缓存（本地）
     *
     * 设计要点：
     * - maximumSize=500：项目缓存数据 < 100 条，500 足够
     * - expireAfterWrite=5min：必须短于 Redis TTL（10min），防止本地脏数据
     * - recordStats()：开启统计，为监控命中率做准备
     */
    @Bean
    public Cache<String,Object> CaffeineCache() {
        Cache<String,Object> cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("Caffeine 本地缓存初始化完成：maximumSize=500, expireAfterWrite=5min, recordStats=on");

        return cache;
    }


}
