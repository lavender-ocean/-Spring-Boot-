package com.sky.aspect;


import com.sky.annotation.Idempotent;
import com.sky.exception.IdempotentException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

//核心思想：**同一个幂等 key 同一时间只允许一条请求执行业务；
// 重复请求过来抢不到锁，直接抛异常拦截，不执行业务代码**。


/**
 * 幂等切面
 * 核心逻辑：
 * 1. 拦截 @Idempotent 注解的方法
 * 2. 根据 SpEL 表达式生成锁 key
 * 3. 尝试加锁，成功 → 执行业务；失败 → 抛 IdempotentException
 * 4. finally 释放锁
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    @Autowired
    private RedissonClient redissonClient;

    // SpEL 表达式解析器
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    //方法参数名发现器（用于 SpEL 里 #userId 这种引用）
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    // 锁 key 前缀
    private static final String LOCK_PREFIX = "lock:idempotent:";


    @Around("@annotation(idempotent)")
    public Object Around(
            ProceedingJoinPoint joinPoint
            , Idempotent idempotent) throws Throwable {

        //Idempotent.Key() -> 就是SpEL表达式！！！

        //1.解析SpEL 生成锁 Key
        String key = LOCK_PREFIX + parseKey(joinPoint,idempotent.key());

        log.debug("【幂等】生成锁 key：{}", key);

        //尝试加锁
        RLock rLock = redissonClient.getLock(key);
        boolean acquired;
        try{
            acquired = rLock.tryLock(idempotent.timeout(), TimeUnit.SECONDS);
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotentException("请求被中断，请重试");
        }

        //3.加锁失败 ----> 抛出异常
        if (!acquired) {
            log.warn("【幂等】重复提交拦截：{}", key);
            throw new IdempotentException(idempotent.message());
        }

        // 4. 加锁成功 → 执行业务
        try {
            log.debug("【幂等】加锁成功：{}", key);
            return joinPoint.proceed();
        } finally {
            // 5. 释放锁（必须判断当前线程持有）
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("【幂等】释放锁：{}", key);
            }
        }



    }

    /**
     * 解析 SpEL 表达式
     * 示例：key = "#userId" → 从方法参数里取 userId 的值
     */
    private String parseKey(ProceedingJoinPoint joinPoint, String spel) {
        //拿到方法签名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);


        //构建SpEl 上下文
        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        // 解析表达式
        try {
            return String.valueOf(PARSER.parseExpression(spel).getValue(context));
        } catch (Exception e) {
            log.error("【幂等】SpEL 解析失败：spel={}", spel, e);
            throw new IdempotentException("幂等 key 生成失败");
        }



    }









}
