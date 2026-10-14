//package com.vueUser.interceptor;
//
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.ExpiredJwtException;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import com.vueUser.utils.userJwtUtil;
//
//@Component
//@Slf4j
//public class userJwtInterceptor implements HandlerInterceptor {
//
//
//    @Value("sky.user_jwt.user-secret-key")
//    private String secretKey;
//
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //从请求头拿用户token，这里我们用请求头key：user‑token，区分管理员token
//        String token = request.getHeader("user-token");
//        if (token == null || token.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//        try {
//            Claims claims = userJwtUtil.parseJwt(secretKey, token);
//            //校验token类型必须是 user，防止传入管理员token
//            String type = claims.get("type", String.class);
//            if (!"user".equals(type)) {
//                response.setStatus(401);
//                return false;
//            }
//            Long userId = Long.valueOf(claims.get("userId").toString());
//            //存入ThreadLocal，保存当前登录用户id，新建UserThreadLocal工具
//            com.vueUser.utils.userThreadLocal.setUserId(userId);
//        } catch (ExpiredJwtException e) {
//            log.error("用户token过期");
//            response.setStatus(401);
//            return false;
//        } catch (Exception e) {
//            log.error("用户token解析失败");
//            response.setStatus(401);
//            return false;
//        }
//        return true;
//    }
//
//}
//
//
//
//
//
