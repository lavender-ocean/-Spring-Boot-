package com.sky.service;

import com.sky.mock.properties.MockPayProperties;
import com.sky.utils.WeChatPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 统一退款服务
 * Mock 模式直接返回，真实模式调微信
 */
@Service
@Slf4j
public class RefundService {

    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private MockPayProperties mockPayProperties;

    /**
     * 统一退款入口
     * @param outTradeNo  商户订单号
     * @param refundAmount 退款金额
     * @param totalAmount 原订单金额
     */
    public void refund(String outTradeNo, BigDecimal refundAmount, BigDecimal totalAmount) {
        // 退款单号必须唯一：原实现拿 outTradeNo 当退款单号，同一订单第二次退款（或失败重试）会被微信拒绝
        String refundNo = outTradeNo + "-R" + System.currentTimeMillis();

        if (mockPayProperties.isEnabled()) {
            log.info("【Mock 支付】跳过微信退款：outTradeNo={}, refundNo={}, amount={}",
                    outTradeNo, refundNo, refundAmount);
            return;
        }
        try {
            String result = weChatPayUtil.refund(outTradeNo, refundNo, refundAmount, totalAmount);
            log.info("微信退款成功：outTradeNo={}, refundNo={}, result={}", outTradeNo, refundNo, result);
        } catch (Exception e) {
            log.error("微信退款失败：outTradeNo={}, refundNo={}", outTradeNo, refundNo, e);
            throw new RuntimeException("微信退款失败：" + e.getMessage(), e);
        }
    }
}