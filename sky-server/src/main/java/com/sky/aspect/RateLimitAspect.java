package com.sky.aspect;

import com.sky.annotation.RateLimit;
import com.sky.constant.RedisKeyConstant;
import com.sky.constant.RedisLuaConstant;
import com.sky.exception.RateLimitException;
import com.sky.utils.RedisLuaManager;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

/**
 * 限流切面
 *
 * 核心逻辑：
 *   1. 解析 SpEL → 生成限流 key
 *   2. 执行 Lua 脚本（滑动窗口）
 *   3. 返回 0 → 抛 RateLimitException
 *   4. Redis 挂了 → 放行（降级）
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RedisLuaManager redisLuaManager;

    @Autowired
    private HttpServletRequest request;

    /** SpEL 解析器 */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /** 方法参数名发现器 */
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();


    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {

        // 1. 生成限流 key
        String bizKey = buildBizKey(point, rateLimit); // 业务key
        String redisKey = RedisKeyConstant.rateLimit(bizKey);// 限流key

        log.debug("【限流】key = {}, limit = {}, windowMs = {}",
                redisKey, rateLimit.limit(), rateLimit.windowMs());

        // 2. 执行 Lua 脚本（滑动窗口）
        long now = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        Long result;
        try {
            result = redisLuaManager.executeLua(
                    RedisLuaConstant.RATE_LIMIT,
                    Collections.singletonList(redisKey),
                    rateLimit.windowMs(),
                    rateLimit.limit(),
                    now,
                    requestId
            );
        } catch (Exception e) {
            //Redis 异常 → 降级放行（限流是保护，不能让它挡业务）
            log.error("【限流】Redis 异常，降级放行：key = {}", redisKey, e);
            return point.proceed();
        }

        // 3. 判断结果
        if (result == null || result == 0L) {
            log.warn("【限流】触发限流：key = {}, limit = {}/{}ms",
                    redisKey, rateLimit.limit(), rateLimit.windowMs());
            throw new RateLimitException(rateLimit.message());
        }

        log.debug("【限流】放行：key = {}", redisKey);
        return point.proceed();
    }

    /**
     * 生成业务限流 key
     * - 未指定 SpEL：用"方法名 + IP"
     * - 指定 SpEL：用"方法名 + SpEL 结果"
     */
    private String buildBizKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String methodKey = signature.getDeclaringType().getSimpleName() + ":" + signature.getName();

        String suffix;
        if (StringUtils.hasText(rateLimit.key())) {
            suffix = parseSpel(point, rateLimit.key());
        } else {
            suffix = getClientIp();
        }

        return methodKey + ":" + suffix;
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpel(ProceedingJoinPoint point, String spel) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object[] args = point.getArgs();
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Object value = PARSER.parseExpression(spel).getValue(context);
            return value == null ? "null" : value.toString();
        } catch (Exception e) {
            log.error("【限流】SpEL 解析失败：spel = {}", spel, e);
            throw new RateLimitException("限流 key 生成失败");
        }
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 可能有多个 IP，取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        //本地调试的IP返回的是这个
        return request.getRemoteAddr();
    }
}