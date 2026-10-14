package com.sky.mq.producer;

import com.sky.constant.MqConstant;
import com.sky.mq.message.PayCallbackMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 支付回调生产者
 */
@Slf4j
@Component
public class PayCallbackProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 投递支付回调消息
     */
    public void sendPayCallback(String outTradeNo, String transactionId, Integer payMethod) {
        PayCallbackMsg msg = new PayCallbackMsg(
                UUID.randomUUID().toString(),
                outTradeNo,
                transactionId,
                payMethod,
                System.currentTimeMillis()
        );

        CorrelationData correlationData = new CorrelationData(msg.getMsgId());

        try {
            rabbitTemplate.convertAndSend(
                    MqConstant.PAY_CALLBACK_EXCHANGE,
                    MqConstant.PAY_CALLBACK_ROUTING_KEY,
                    msg,
                    m -> {
                        m.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return m;
                    },
                    correlationData
            );
            log.info(" 支付回调消息已投递：outTradeNo = {}", outTradeNo);
        } catch (Exception e) {
            log.error(" 支付回调消息投递失败：outTradeNo = {}", outTradeNo, e);
        }
    }
}