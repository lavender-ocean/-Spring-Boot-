package com.sky.service.token;


import com.sky.constant.JwtClaimsConstant;
import com.sky.context.UserContext;
import com.sky.exception.TokenInvalidException;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * JWT 鉴权服务（统一 HTTP / WebSocket 鉴权入口）
 */
@Slf4j
@Service
public class JwtAuthService {

    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 解析管理端 token
     * 校验：签名、黑名单、userType == EMP
     *
     * @return UserContext(userId, "EMP")
     */
    public UserContext parseAdminToken(String adminToken) {

        if (adminToken == null ||  adminToken.isEmpty()) {
            throw new TokenInvalidException("token 为空");
        }

        //1. 黑名单校验
        if( jwtBlacklistService.isBlackListed(adminToken) ) {
            throw new TokenInvalidException("token 已经在 黑名单中");
        }

        //2. 解析 token
        Claims claims;

        try {
            claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), adminToken);
        } catch (Exception e) {
            throw new TokenInvalidException("token 无效");
        }

        // 3. 校验身份类型
//        String userType = claims.get(JwtClaimsConstant.USER_TYPE, String.class);

        Object userTypeObj = claims.get(JwtClaimsConstant.USER_TYPE);
        String userType = userTypeObj == null ? null : userTypeObj.toString();


        if(!JwtClaimsConstant.TYPE_ADMIN.equals(userType)) {
            throw new TokenInvalidException("身份类型不符，期望 EMP，实际 " + userType);

        }

        //4.获取 empId  ----- EMP_ID
//        Long userId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID, String.class));

        //更加 健壮

        Object empIdObj = claims.get(JwtClaimsConstant.EMP_ID);
        Long userId = Long.valueOf(empIdObj.toString());


        /**
         * RABC
         */

        // 解析 role
        Object roleObj = claims.get(JwtClaimsConstant.ROLE);
        String role = roleObj == null ? null : roleObj.toString();

        return new UserContext(userId, JwtClaimsConstant.TYPE_ADMIN,role);

    }

    /**
     * 解析 C 端用户 token
     * 校验：签名、黑名单、userType == USER
     *
     * @return UserContext(userId, "USER")
     */
    public UserContext parseUserToken(String userToken) {

        if (userToken == null || userToken.isEmpty()) {
            throw new TokenInvalidException("token 为空");

        }

        //1. 黑名单
        if( jwtBlacklistService.isBlackListed(userToken) ) {
            throw new TokenInvalidException("token 已经在 黑名单中");

        }

        //2. 解析 token
        Claims claims;
        try {
            claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), userToken);
        } catch (Exception e) {
            throw new TokenInvalidException("token 无效");
        }

        //3.校验 身份类型
//        String userType = claims.get(JwtClaimsConstant.USER_TYPE, String.class);


        Object userTypeObj = claims.get(JwtClaimsConstant.USER_TYPE);
        String userType = userTypeObj == null ? null : userTypeObj.toString();

        if(!JwtClaimsConstant.TYPE_USER.equals(userType)) {
            throw new TokenInvalidException("身份类型不符，期望 USER，实际 " + userType);

        }

        //4.获取 userId  ----- USER_ID
//        Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID, String.class));


        Object userIdObj = claims.get(JwtClaimsConstant.USER_ID);
        Long userId = Long.valueOf(userIdObj.toString());




        return new UserContext(userId, JwtClaimsConstant.TYPE_USER);
    }




}



//
//核心设计思想：
//
//单一职责：JWT 解析只在这一处发生
//
//异常统一：所有失败都抛 TokenInvalidException
//
//返回统一：都返回 UserContext（含 userId + userType）
//
//黑名单内聚：黑名单校验也放在 Service 里，调用方不用关心



























