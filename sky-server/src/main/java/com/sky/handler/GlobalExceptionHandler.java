package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.*;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }


    @ExceptionHandler
    public Result SqlexceptionHandler(SQLIntegrityConstraintViolationException ex){

        String message = ex.getMessage();

        if(message.contains("Duplicate entry")){
            String[] split = message.split(" ");

            String username = split[2];

            String msg = username + MessageConstant.UNKNOWN_ERROR;
            return Result.error(msg);

        }
        else{
            return Result.error(message);
        }

    }

    @ExceptionHandler(ForbiddenException.class)
    public Result<String> handleForbiddenException(ForbiddenException e) {
        log.warn("权限不足：{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(TokenInvalidException.class)
    public Result<String> handleTokenInvalidException(TokenInvalidException e) {
        log.warn("Token 无效：{}", e.getMessage());
        return Result.error(e.getMessage());
    }


    @ExceptionHandler(IdempotentException.class)
    public Result<String> handleIdempotentException(IdempotentException e) {
        log.warn("幂等冲突：{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(LockAcquireException.class)
    public Result<String> handleLockAcquireException(LockAcquireException e) {
        log.warn("获取锁失败：{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public Result<String> handleRateLimitException(RateLimitException e) {
        log.warn("限流拦截：{}", e.getMessage());
        return Result.error(e.getMessage());
    }
}
