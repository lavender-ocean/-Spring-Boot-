package com.sky.aspect;


import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 锁指标埋点切面
 * 作用：统计 @Idempotent 方法的锁获取耗时、失败次数
 * 只做指标统计，不重复加锁（锁逻辑在 IdempotentAspect 里）
 */
@Slf4j
@Aspect
@Component
public class LockMetricsAspect {

    @Autowired
    private MeterRegistry meterRegistry;


    @Around("@annotation(com.sky.annotation.Idempotent)")
    public Object measureLockMetrics(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();

        String methodName =
                methodSignature.getDeclaringType().getSimpleName() + "." + methodSignature.getName();

        long start = System.nanoTime();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            long duration = System.nanoTime() - start;

            //  Timer：记录耗时分布
            Timer.builder("lock.acquire.duration")
                    .tag("method", methodName)
                    .tag("result", success ? "success" : "fail")
                    .description("锁获取到业务完成的耗时")
                    .publishPercentiles(0.5, 0.95, 0.99)   // P50 / P95 / P99
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS);

            //  Counter：失败次数
            if (!success) {
                meterRegistry.counter("lock.acquire.failed.count",
                        "method", methodName).increment();
            }
        }
    }
}

