package com.sky.dto;


import lombok.Data;

import java.io.Serializable;

/**
 *
 * 刷新令牌请求
 */

/**
 *`Serializable` 是 JDK 内置**标记接口**，接口里面**没有任何抽象方法**。
 *
 *
 * 标记接口作用：给 JVM 打标记，告诉虚拟机：**这个类的对象支持序列化 / 反序列化**。
 *
 */

@Data
public class RefreshTokenDTO implements Serializable {

    private String refreshToken;


}
