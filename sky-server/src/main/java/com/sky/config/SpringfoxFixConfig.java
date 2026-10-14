package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Springfox 修复配置
 *
 * 解决 Spring Boot 2.6+ 与 Springfox 3.0.0 的 PathPatternParser 兼容问题
 *
 * 核心思路：
 * 1. Spring Boot 2.6+ 默认使用 PathPatternParser 解析路径
 * 2. Springfox 3.0.0 内部假设使用 AntPathMatcher
 * 3. 导致 springfox 扫描到使用 PathPatternParser 的 handler 时 NPE
 * 4. 这个 BeanPostProcessor 在 Springfox 初始化前，过滤掉使用 PathPatternParser 的 handler
 */
@Slf4j
@Configuration
public class SpringfoxFixConfig {

    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
                }
                return bean;
            }

            /**
             * 过滤掉使用 PathPatternParser 的 handler
             * 保留只有 AntPathMatcher 的 handler，避免 Springfox NPE
             */
            private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
                List<T> copy = mappings.stream()
                        .filter(mapping -> mapping.getPatternParser() == null)
                        .collect(Collectors.toList());
                mappings.clear();
                mappings.addAll(copy);
                log.info("Springfox 修复：过滤后剩余 handler 数量 = {}", mappings.size());
            }

            /**
             * 反射获取 handlerMappings 字段
             */
            @SuppressWarnings("unchecked")
            private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
                try {
                    Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                    if (field == null) {
                        throw new IllegalStateException("找不到 handlerMappings 字段");
                    }
                    field.setAccessible(true);
                    return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}