package com.sky.interceptor;

import com.sky.context.BaseContext;
import com.sky.context.UserContext;
import com.sky.exception.TokenInvalidException;
import com.sky.properties.JwtProperties;
import com.sky.service.token.JwtAuthService;
import com.sky.service.token.JwtBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;


    //解析 token 之前 ---- 先去查找 黑名单
    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    //统一鉴权服务
    @Autowired
    private JwtAuthService jwtAuthService;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        log.info("当前线程的id:{}", Thread.currentThread().getId());

        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        /**
         *
         * 统一调用 JwtAuthservice
         */

        try {
            UserContext ctx = jwtAuthService.parseAdminToken(token);

            log.info("管理端鉴权成功，userId = {}, userType = {}"
                    , ctx.getUserId(), ctx.getUserType());
            // 写入 ThreadLocal
            BaseContext.set(ctx);
            return true;

        } catch (
        TokenInvalidException e) {
                log.warn("管理端鉴权失败：{}", e.getMessage());
                response.setStatus(401);
                return false;
        } catch (Exception e) {
            log.error("管理端鉴权异常", e);
            response.setStatus(500);
            return false;
        }


//
//        /**
//         * 先 去 查找 黑名单
//         */
//        if(jwtBlacklistService.isBlackListed(token)) {
//            log.warn("token 已失效（在黑名单中）");
//            response.setStatus(401);
//            return false;
//        }
//
//
//
//        //2、校验令牌
//        try {
//            log.info("jwt校验:{}", token);
//            //拿到 Claims
//            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
//
//            //校验身份类型
//            String userType = claims.get(JwtClaimsConstant.USER_TYPE, String.class);
//            if (!JwtClaimsConstant.TYPE_ADMIN.equals(userType)) {
//                log.warn("非管理员 token 尝试访问管理端接口，userType = {}", userType);
//                response.setStatus(403);   // 403 = 无权限（区别于 401 = 未登录）
//                return false;
//            }
//
//
//            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
//            log.info("当前员工id：{}", empId);
//
//            BaseContext.set(
//                    new UserContext(empId,JwtClaimsConstant.TYPE_ADMIN)
//            );
//
//
//            //3、通过，放行
//            return true;
//        } catch (Exception ex) {
//            //4、不通过，响应401状态码
//            response.setStatus(401);
//            return false;
//        }
    }


    /**
     *
     * 请求结束后 ---- 清理ThreadLocal
     * 不加这行，Tomcat 线程池复用时下一个请求会读到脏数据！
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    public void afterCompletion(HttpServletRequest request
            , HttpServletResponse response
            , Object handler
            , Exception ex) throws Exception {

        BaseContext.remove();

    }
}
