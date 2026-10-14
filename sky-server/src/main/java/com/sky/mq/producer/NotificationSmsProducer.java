package com.sky.mq.producer;

import com.sky.constant.MqConstant;
import com.sky.mq.message.NotificationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 短信队列生产者
 * 用于 WS 消费者降级转投
 */
@Slf4j
@Component
public class NotificationSmsProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendSms(NotificationMsg msg) {

        // ★ 改动：补 CorrelationData —— Confirm 回调靠它识别"是哪条消息"
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());

        try {
            rabbitTemplate.convertAndSend(
                    "notify.sms.exchange",      // 独立 Direct 交换机
                    "notify.sms.send",          // 路由键
                    msg,
                    m -> {
                        m.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return m;
                    },
                    correlationData
            );

            // ===== 以下为改动前的原始调用（未传 CorrelationData），保留备查 =====
            // rabbitTemplate.convertAndSend(
            //         "notify.sms.exchange",      // 独立 Direct 交换机
            //         "notify.sms.send",          // 路由键
            //         msg,
            //         m -> {
            //             m.getMessageProperties().setDeliveryMode(
            //                     org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            //             return m;
            //         }
            // );

            log.info("通知已转入短信队列：phone = {}", msg.getPhone());
        } catch (Exception e) {
            log.error("短信队列投递失败：{}", msg, e);
        }
    }
}