package com.sky.annotation;


import java.lang.annotation.*;


/**
 * 接口限流注解
 *
 * 使用示例：
 *   @RateLimit(limit = 3, windowMs = 1000)                  // 默认按 IP 限流，1 秒 3 次
 *   @RateLimit(limit = 5, windowMs = 1000, key = "#userId") // 按用户 ID 限流
 *   @RateLimit(limit = 5, windowMs = 1000,
 *              key = "T(com.sky.context.BaseContext).getCurrentId()") // 按登录用户限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流阈值 */
    int limit() default 10;

    /** 窗口大小（毫秒） */
    int windowMs() default 1000;

    /**
     * SpEL 表达式（可选）
     * 不填 → 默认按 IP 限流
     * 填了 → 按 SpEL 计算结果限流（如 "#userId"）
     */
    String key() default "";

    /** 限流提示 */
    String message() default "请求过于频繁，请稍后再试";


}













