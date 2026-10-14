package com.sky.mq.producer;


import com.sky.constant.MqConstant;
import com.sky.mq.message.OrderTimeoutMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

//订单超时消息 生产者
@Component
@Slf4j
public class OrderTimeoutProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送订单超时消息
     * @param orderId 订单 ID
     * @param delayMs 延迟时间（毫秒）
     */
    //用long --> 能转 int

    public void sendTimeOutMsg(Long orderId, long delayMs){

        OrderTimeoutMsg msg = new OrderTimeoutMsg(
                orderId
                ,UUID.randomUUID().toString()
                ,System.currentTimeMillis());

        // CorrelationData：用于 Confirm 回调关联
        CorrelationData correlationData =
                new CorrelationData(msg.getMsgId());

        try {
            rabbitTemplate.convertAndSend(
                     MqConstant.ORDER_DELAY_EXCHANGE
                    ,MqConstant.ORDER_DELAY_ROUTING_KEY
                    ,msg
                    ,message -> {

                         //设置每条消息的延时时间
                        message.getMessageProperties().setDelay((int)delayMs);
                        //持久化
                        message.getMessageProperties()
                                .setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);


                        return message;
                    },
                    correlationData

            );
            log.info(" 订单超时消息已投递：orderId = {}, delayMs = {}", orderId, delayMs);
        } catch (Exception e) {
            log.error("投递订单超时消息失败：orderId = {}", orderId, e);
            //  生产环境：落库 + 定时重发（兜底）
        }

    }



}
