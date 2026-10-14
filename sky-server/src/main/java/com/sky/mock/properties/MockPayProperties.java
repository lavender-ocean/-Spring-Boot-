package com.sky.mock.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mock 支付配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.mock-pay")
public class MockPayProperties {

    /**
     * 是否启用 Mock 支付
     * true  = 演示环境（不依赖微信商户号）
     * false = 生产环境（真实对接微信）
     */
    private boolean enabled = false;

    /**
     * 模拟用户支付耗时（毫秒）
     * 用于模拟"用户点击支付 → 微信扣款 → 回调"的时间
     */
    private long payDelayMs = 2000;
}