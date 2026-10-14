package com.sky.annotation;


import java.lang.annotation.*;



/**
 * 幂等注解
 * 作用：防止同一用户在短时间内重复提交同一个请求
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * SpEL 表达式，用于生成锁 key
     * 示例："#userId"  或  "#dto.dishId + ':' + #userId"
     */
    String key();

    /**
     * 锁超时时间（秒），超过这个时间未获取到锁则降级
     */
    long timeout() default 3;

    /**
     * 重复提交时的错误提示
     */
    String message() default "请勿重复提交，请稍后再试";



}
