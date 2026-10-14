package com.sky.exception;

/**
 * 幂等冲突异常（重复提交）
 */
public class IdempotentException extends RuntimeException {
    public IdempotentException(String message) {
        super(message);
    }
}