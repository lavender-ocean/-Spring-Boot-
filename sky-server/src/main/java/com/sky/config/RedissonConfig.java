package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${sky.redis.host}")
    private String host;

    @Value("${sky.redis.port}")
    private int port;

    @Value("${sky.redis.database:0}")
    private int database;

    @Value("${sky.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                // 连接池配置
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20)
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        // 有密码才设置
        if (password != null && !password.isEmpty()) {
            serverConfig.setPassword(password);
        }

        RedissonClient client = Redisson.create(config);
        log.info("Redisson 客户端初始化完成：{}:{}", host, port);
        return client;
    }
}