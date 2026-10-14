package com.sky.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * C 端用户登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO implements Serializable {
    private Long id;
    private String phone;
    private String name;
//    private String token;


    @ApiModelProperty("访问令牌（短期）")
    private String accessToken;
    @ApiModelProperty("刷新令牌（长期）")
    private String refreshToken;
}