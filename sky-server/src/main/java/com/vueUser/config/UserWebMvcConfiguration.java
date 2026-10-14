//package com.vueUser.config;
//
//import com.vueUser.interceptor.userJwtInterceptor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//
//@Configuration
//@Slf4j
//public class UserWebMvcConfiguration implements WebMvcConfigurer {
//
//    @Autowired
//    private userJwtInterceptor userJwtInterceptor;
//
//
//    public void addInterceptors(InterceptorRegistry registry) {
//        log.info("注册用户端--------拦截器");
//        registry.addInterceptor(userJwtInterceptor)
//                .addPathPatterns("/user/**")
//                .excludePathPatterns(
//                        "/user/user/login"      // 登录接口放行
//                );
//
//
//
//    }
//
//
//}
