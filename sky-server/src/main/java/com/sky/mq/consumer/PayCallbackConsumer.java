package com.sky.mq.consumer;

import com.rabbitmq.client.Channel;
import com.sky.constant.MqConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.mapper.MqFailedMsgMapper;
import com.sky.mq.message.PayCallbackMsg;
import com.sky.utils.MqIdempotentHelper;
import com.vueUser.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 支付回调消费者
 * 幂等 + 业务处理
 */
@Slf4j
@Component
public class PayCallbackConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MqIdempotentHelper mqIdempotentHelper;

    @Autowired
    private MqFailedMsgMapper mqFailedMsgMapper;

    @RabbitListener(queues = MqConstant.PAY_CALLBACK_QUEUE)
    public void handlePayCallback(PayCallbackMsg msg,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info(" 收到支付回调消息：outTradeNo = {}, msgId = {}",
                msg.getOutTradeNo(), msg.getMsgId());

//        // ========== 第 1 层：Redis 幂等 ==========
//        String idempotentKey = "mq:idempotent:pay:" + msg.getMsgId();
//        Boolean isFirstProcess = stringRedisTemplate.opsForValue()
//                .setIfAbsent(idempotentKey, "1", 7, TimeUnit.DAYS);
//
//        if (Boolean.FALSE.equals(isFirstProcess)) {
//            log.warn("支付回调已处理，忽略：msgId = {}", msg.getMsgId());
//            channel.basicAck(deliveryTag, false);
//            return;
//        }


        if (!mqIdempotentHelper.isFirstProcess("pay", msg.getMsgId())) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            // ========== 第 2 层：业务处理（内部还有状态机幂等）==========
            orderService.paySuccess(msg.getOutTradeNo());

            log.info(" 支付回调处理成功：outTradeNo = {}", msg.getOutTradeNo());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理支付回调失败：outTradeNo = {}", msg.getOutTradeNo(), e);
            // 关键：失败后删键，允许重试

            mqIdempotentHelper.rollback("pay", msg.getMsgId());
            // 不重回队列，进入死信
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 死信队列监听
     */
    @RabbitListener(queues = MqConstant.PAY_DLQ_QUEUE)
    public void handleDlq(PayCallbackMsg msg, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("支付回调消息进入死信队列，需人工介入：{}", msg);

        try {
            mqFailedMsgMapper.insertIgnore(MqFailedMsg.builder()
                    .bizType("pay")
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