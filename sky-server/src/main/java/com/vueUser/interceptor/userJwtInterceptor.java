package com.vueUser.interceptor;


import com.sky.exception.TokenInvalidException;
import com.sky.service.token.JwtAuthService;
import com.sky.service.token.JwtBlacklistService;
import org.springframework.web.method.HandlerMethod;

import com.sky.context.BaseContext;
import com.sky.context.UserContext;
import com.sky.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class userJwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;


    // 希望   * 解析 token之前 前去查找 黑名单 ****
    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    //统一 鉴权
    @Autowired
    private JwtAuthService jwtAuthService;


    public boolean preHandle(final HttpServletRequest request
            , final HttpServletResponse response
            , final Object handler) throws Exception {

        log.info("=====进入C端拦截器，uri:{}",request.getRequestURI()); //新增

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        //用户端 读取 token ---- getUserTokenName
        String token = request.getHeader(jwtProperties.getUserTokenName());


        try {
            // 统一调用 Service
            UserContext ctx = jwtAuthService.parseUserToken(token);
            log.info("C端鉴权成功，userId = {}, userType = {}", ctx.getUserId(), ctx.getUserType());

            BaseContext.set(ctx);
            return true;
        } catch (TokenInvalidException e) {
            log.warn("C端鉴权失败：{}", e.getMessage());
            response.setStatus(401);
            return false;
        } catch (Exception e) {
            log.error("C端鉴权异常", e);
            response.setStatus(500);
            return false;
        }
//
//        /**
//         * 解析 token之前 前去查找 黑名单
//         *
//         */
//        if(jwtBlacklistService.isBlackListed(token)) {
//            log.warn("C端token已经失效 ：：：：：： token 在 黑名单中 ！");
//            response.setStatus(401);
//            return false;
//        }
//
//
//        try {
//            //解析 token
//            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
//
//            String userType = claims.get(JwtClaimsConstant.USER_TYPE, String.class);   // ← USER_TYPE（值是 "userType"）
//
//            //进行身份的校验  --- 必须是 user
//            if (!JwtClaimsConstant.TYPE_USER.equals(userType)) {
//
//                log.warn("非 C 端用户 token 尝试访问 /user/**，userType = {}", userType);
//                response.setStatus(403);
//                return false;
//            }
//
//            Long userId = claims.get(JwtClaimsConstant.USER_ID, Long.class);
//            log.info("当前用户 id = {}", userId);
//
//            //存入上下文
//            BaseContext.set(new UserContext(userId, JwtClaimsConstant.TYPE_USER));
//
//            return true;
//        } catch (Exception ex) {
//            log.warn("C 端 token 校验失败：{}", ex.getMessage());
//            response.setStatus(401);
//            return false;
//
//
//        }
//

    }


    /**
     * 清理 ThreadLocal
     *
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }

}


