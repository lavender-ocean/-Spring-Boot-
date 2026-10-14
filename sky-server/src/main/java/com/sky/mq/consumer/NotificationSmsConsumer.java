package com.sky.mq.consumer;

import com.rabbitmq.client.Channel;
import com.sky.constant.MqConstant;
import com.sky.mq.message.NotificationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 短信消费者（Mock 占位）
 * 真实环境替换成阿里云/腾讯云短信 SDK
 */
@Slf4j
@Component
public class NotificationSmsConsumer {

    @RabbitListener(queues = MqConstant.NOTIFY_SMS_QUEUE)
    public void handleSms(NotificationMsg msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("📱 [短信Mock] ┌──────────────────────────────────");
        log.info("📱 [短信Mock] │ 手机号: {}", msg.getPhone());
        log.info("📱 [短信Mock] │ 事件: {}", msg.getEventType());
        log.info("📱 [短信Mock] │ 内容: {}", msg.getContent());
        log.info("📱 [短信Mock] │ 订单号: {}", msg.getOrderNumber());
        log.info("📱 [短信Mock] └──────────────────────────────────");

        // 真实环境：
        // 1. 调阿里云 SDK 发送
        // 2. 记录短信发送日志
        // 3. 失败重试 → 重试耗尽进 DLQ

        channel.basicAck(deliveryTag, false);
    }
}