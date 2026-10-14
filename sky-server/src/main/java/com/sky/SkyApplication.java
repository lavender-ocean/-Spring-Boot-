package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling// 开启任务调度
@EnableCaching
@SpringBootApplication
@EnableTransactionManagement //开启注解方式的事务管理
@Slf4j
@ComponentScan(basePackages = {"com.sky","com.vueUser"}) //手动指定扫描两个包
@MapperScan({"com.sky.mapper", "com.vueUser.mapper"}) // 扫描所有 Mapper 所在包
public class SkyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkyApplication.class, args);
        log.info("server started");
    }
}
