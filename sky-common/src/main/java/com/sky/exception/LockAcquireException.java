package com.sky.exception;

/**
 * 获取分布式锁失败
 */
public class LockAcquireException extends RuntimeException {
    public LockAcquireException(String message) {
        super(message);
    }
}