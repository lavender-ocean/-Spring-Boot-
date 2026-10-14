package com.vueUser.controller;

import com.sky.annotation.RateLimit;
import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;

import com.sky.service.token.RefreshTokenService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import com.vueUser.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Api(tags = "C端-用户相关接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    // 加入 双令牌 机制
    @Autowired
    private RefreshTokenService refreshTokenService;

//    /**
//     * 微信登录
//     * @param userLoginDTO
//     * @return
//     */
//    @PostMapping("/login")
//    @ApiOperation("微信登录")
//    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
//        log.info("微信用户登录：{}",userLoginDTO.getCode());
//
//        //微信登录
//        User user = userService.wxLogin(userLoginDTO);
//
//        //为微信用户生成jwt令牌
//        Map<String, Object> claims = new HashMap<>();
//        claims.put(JwtClaimsConstant.USER_ID,user.getId());
//        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
//
//        UserLoginVO userLoginVO = UserLoginVO.builder()
//                .id(user.getId())
//                .openid(user.getOpenid())
//                .token(token)
//                .build();
//        return Result.success(userLoginVO);
//    }


    /**
     * C端 用户登陆 ： 手机号 + 密码
     */
    @RateLimit(limit = 5, windowMs = 60000,
            key = "#userLoginDTO.phone",
            message = "登录过于频繁，请 1 分钟后再试")
    @PostMapping("/login")
    @ApiOperation("C端用户登陆")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) throws Exception {
        log.info("C端用户登录信息：{}", userLoginDTO);

        //做登陆的校验
        User user = userService.login(userLoginDTO);

        /**

        //生成JWT
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.USER_TYPE,JwtClaimsConstant.TYPE_USER);

        //使用 jwtProperties中的 user-- 用户 的 设置来生成token
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
         *
         */

        //接下来 使用 双令牌 机制

        //1. accessToken
        Map<String,Object> accessClaims = new HashMap<>();
        accessClaims.put(JwtClaimsConstant.USER_ID, user.getId());
        accessClaims.put(JwtClaimsConstant.USER_TYPE,JwtClaimsConstant.TYPE_USER);
        //不需要 使用 payload
        String accessToken = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                accessClaims
        );

        //2.生成 refreshToken
        Map<String,Object> refreshClaims = new HashMap<>();
        refreshClaims.put(JwtClaimsConstant.USER_ID, user.getId());
        refreshClaims.put("type", "refresh");
        //生成token
        String refreshToken = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserRefreshTtl(),
                refreshClaims
        );



        UserLoginVO vo = UserLoginVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .name(user.getName())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();


        log.info("C端用户登录成功，id = {}", user.getId());

        refreshTokenService.save("user",refreshToken,user.getId(),jwtProperties.getUserRefreshTtl());

        return Result.success(vo);

    }


}
