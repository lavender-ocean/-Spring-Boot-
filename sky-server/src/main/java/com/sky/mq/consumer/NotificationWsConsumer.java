package com.sky.mq.consumer;


import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import com.sky.Websocket.WebSocketServer;
import com.sky.constant.MqConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.mapper.MqFailedMsgMapper;
import com.sky.mq.message.NotificationMsg;
import com.sky.mq.producer.NotificationProducer;
import com.sky.mq.producer.NotificationSmsProducer;
import com.sky.utils.MqIdempotentHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.amqp.support.AmqpHeaders;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * 通知 WS 消费者
 * 逻辑：
 *   1. 幂等判断
 *   2. 检查用户是否在线
 *   3. 在线 → WebSocket 推送
 *   4. 离线 → 转投短信队列（降级）
 */

@Component
@Slf4j

public class NotificationWsConsumer {

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private NotificationSmsProducer notificationSmsProducer;

    @Autowired
    private MqIdempotentHelper mqIdempotentHelper;

    @Autowired
    private MqFailedMsgMapper mqFailedMsgMapper;

    @RabbitListener(queues = MqConstant.NOTIFY_WS_QUEUE)
    public void handleNotification(
            NotificationMsg msg,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        log.info(" [WS消费者] 收到通知：eventType = {}, userId = {}, orderId = {}",
                msg.getEventType(), msg.getUserId(), msg.getOrderId());


        try{

            //先做幂等
//            String idemKey = "mq:idempotent:notify:ws:" + msg.getMsgId();
//
//            Boolean ifFirst = stringRedisTemplate.opsForValue().setIfAbsent(
//                    idemKey,"1",7, TimeUnit.DAYS
//            );
//            if(!ifFirst){
//                log.warn("通知已处理，忽略：msgId = {}", msg.getMsgId());
//                log.warn("通知已处理，忽略：msgId = {}", msg.getMsgId());
//                channel.basicAck(deliveryTag, false);
//                return;
//            }

            //  一行搞定幂等
            if (!mqIdempotentHelper.isFirstProcess("notify:ws", msg.getMsgId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

//            //新幂等
//            if (mqIdempotentHelper.isProcessed("notify:ws", msg.getMsgId())) {
//                channel.basicAck(deliveryTag, false);
//                return;
//            }


            //先检测用户是否在线
            int onlineCount = webSocketServer.getUserOnlineCount(msg.getUserId());

            //用户在线则使用 websocket 来进行推送
            if(onlineCount > 0){
                Map<String,Object> payload = new HashMap<>();
                payload.put("type", msg.getEventType());
                payload.put("orderId", msg.getOrderId());
                payload.put("orderNumber", msg.getOrderNumber());
                payload.put("content", msg.getContent());

                String Json = JSON.toJSONString(payload);

                webSocketServer.sendToUser(msg.getUserId(), Json);

                log.info("【WS消费者】 WebSocket 推送成功：userId = {}, eventType = {}",
                        msg.getUserId(), msg.getEventType());

            }else {
                // ========== 离线：降级到短信 ==========
                log.info("【WS消费者】 用户 {} 离线，降级到短信通道", msg.getUserId());
                notificationSmsProducer.sendSms(msg);
            }
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("【WS消费者】 处理失败：{}", msg, e);
            // 关键：失败后删键，允许重试
            mqIdempotentHelper.rollback("notify:ws", msg.getMsgId());
            channel.basicNack(deliveryTag, false, false);
        }



//        mqIdempotentHelper.markProcessed("notify:ws", msg.getMsgId());




    }


    @RabbitListener(queues = MqConstant.NOTIFY_DLQ_QUEUE)
    public void handleDlq(NotificationMsg msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("【通知DLQ】 消息进入死信队列，需人工介入：{}", msg);

        try {
            mqFailedMsgMapper.insertIgnore(MqFailedMsg.builder()
                    .bizType("notify:ws")
                    .msgId(msg.getMsgId())
                    .payload(com.alibaba.fastjson.JSON.toJSONString(msg))
                    .status("PENDING")
                    .retryCount(0)
                    .createTime(java.time.LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("DLQ 落库失败", e);
        }

        channel.basicAck(deliveryTag, false);
    }
}

















