package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.json.JacksonObjectMapper;
import com.vueUser.interceptor.userJwtInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j

// @TODO 这个完整的 extends  WebMvcConfigurationSupport 是个大隐患
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private com.vueUser.interceptor.userJwtInterceptor userJwtInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                // .addPathPatterns("/admin/**", "/user/**")
                .excludePathPatterns("/admin/employee/login")
                .excludePathPatterns("/refresh")   // 刷新接口不需要鉴权（用 refreshToken 自证）
                // TODO 上传资源 先暂时 放行
                .excludePathPatterns("/admin/common/update")
                .excludePathPatterns("/admin/common/upload")

                ;

        // 用户端拦截器
        registry.addInterceptor(userJwtInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login");

        log.info("管理端 + 用户端拦截器注册完成");


    }


    /**
     * 通过knife4j生成接口文档
     * @return
     */
    @Bean
    public Docket docket() {
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("外卖项目接口文档")
                .version("2.0")
                .description("穹外卖项目接口文档")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo)
                .select()
                //重点：or 同时扫描两个controller包
                .apis(RequestHandlerSelectors.basePackage("com.sky.controller.admin")
                        .or(RequestHandlerSelectors.basePackage("com.vueUser.controller"))
                )
                .paths(PathSelectors.any())
                .build();
        return docket;
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");




        //本地磁盘存储路径（★ 改为项目内相对路径：原值是本机绝对路径，含个人目录信息，已移除）
        final String Image_Path_3 =
                "./images/";


        //url: /images/xxx.jpg 映射到上面磁盘文件夹
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + Image_Path_3);


    }


    /**
     * 扩展消息转换器
     */

    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {

        log.info("扩展消息转换器");
        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        //需要为消息转换器设置一个对象转换器，把java对象序列化成json数据
        converter.setObjectMapper(new JacksonObjectMapper());
        //将自己的消息转换器加入到容器中
        converters.add(0,converter);


    }



    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
//                .allowedOrigins("http://localhost:5173", "http://localhost:8085","http://localhost:8889") // 同时允许这两个地址
                // 使用 allowedOriginPatterns 替代 allowedOrigins
                .allowedOriginPatterns("http://localhost:*")  // 匹配 localhost 的所有端口
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }










}
