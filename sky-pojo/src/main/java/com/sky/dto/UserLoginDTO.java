package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * C 端用户登录请求
 */
@Data
public class UserLoginDTO implements Serializable {
    /** 手机号 */
    private String phone;
    /** 密码 */
    private String password;
}