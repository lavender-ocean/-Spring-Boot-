package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 *  双令牌对（登录 / 刷新接口都返回这个）
 *
 */
public class TokenPairVO {

    //访问令牌
    private String accessToken;

    //刷新令牌
    private String refreshToken;


}
