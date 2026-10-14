package com.sky.utils;



import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类：使用BCrypt
 *
 */
public class PasswordUtil {

    private final static BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 加密
     */

    public static String encoderBCrypt(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验
     */

    public static boolean matchesBCrypt(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }





}
