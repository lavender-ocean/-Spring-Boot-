package com.sky.exception;

/**
 * 限流异常
 */
public class RateLimitException extends BaseException {

    public RateLimitException(String msg) {
        super(msg);
    }
}