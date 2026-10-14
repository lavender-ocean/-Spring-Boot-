package com.sky.controller.mock;

import com.sky.mock.properties.MockPayProperties;
import com.sky.mq.producer.PayCallbackProducer;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Mock 支付控制器（演示环境用）
 * 生产环境禁止暴露
 */
@Slf4j
@RestController
@RequestMapping("/mock/pay")
@Api(tags = "Mock 支付（演示环境）")
public class MockPayController {

    @Autowired
    private PayCallbackProducer payCallbackProducer;

    @Autowired
    private MockPayProperties mockPayProperties;

    /**
     * 模拟用户点击"确认支付"
     * 前端在收到 prepay_id 后调用这个接口
     */
    @PostMapping("/success")
    @ApiOperation("模拟支付成功（异步处理）")
    public Result<String> mockPaySuccess(@RequestParam String outTradeNo) {
        if (!mockPayProperties.isEnabled()) {
            return Result.error("Mock 支付未启用");
        }

        log.info(" [Mock] 模拟支付成功：outTradeNo = {}", outTradeNo);

        // 构造假的微信交易号
        String transactionId = "MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 关键：投递到 MQ，异步处理（削峰）
        payCallbackProducer.sendPayCallback(outTradeNo, transactionId, 1);

        return Result.success("支付回调已入队");
    }
}