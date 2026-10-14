package com.sky.mq.producer;


import com.sky.constant.MqConstant;
import com.sky.mq.message.NotificationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知事件生产者
 * 职责：只负责投递事件，不关心消费者怎么处理
 */
@Component
@Slf4j
public class NotificationProducer {


    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendNotification(NotificationMsg msg){

        // ★ 改动：补 CorrelationData —— Confirm 回调靠它识别"是哪条消息"
        //   （原来没传，失败时回调里 correlationData = null，无法定位与补偿）
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());

        try {
            rabbitTemplate.convertAndSend(
                    MqConstant.NOTIFY_EXCHANGE
                    ,""
                    ,msg
                    ,m -> {
                        m.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return m;
                    }
                    ,correlationData
            );

            // ===== 以下为改动前的原始调用（未传 CorrelationData），保留备查 =====
            // rabbitTemplate.convertAndSend(
            //         MqConstant.NOTIFY_EXCHANGE
            //         ,""
            //         ,msg
            //         ,m -> {
            //             m.getMessageProperties().setDeliveryMode(
            //                     org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            //             return m;
            //         }
            // );

            log.info("通知事件已投递：eventType = {}, orderId = {}",
                    msg.getEventType(), msg.getOrderId());
        } catch (AmqpException e) {
            log.error("通知事件投递失败：{}", msg, e);
        }


    }








}
