package com.sky.exception;



/**
 * Token 无效异常（统一异常，便于拦截器统一 catch）
 */

public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException(String message) {

        super(message);
    }
}
