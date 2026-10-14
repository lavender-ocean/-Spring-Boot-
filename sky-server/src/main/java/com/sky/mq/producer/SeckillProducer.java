package com.sky.mq.producer;


import com.sky.constant.MqConstant;
import com.sky.mq.message.SeckillMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * 秒杀消息生产者
 *
 */
@Component
@Slf4j
public class SeckillProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendSeckillMsg(SeckillMsg seckillMsg) {
        CorrelationData correlationData = new CorrelationData(seckillMsg.getMsgId());

        try {
            rabbitTemplate.convertAndSend(
                    MqConstant.SECKILL_EXCHANGE
                    ,MqConstant.SECKILL_ROUTING_KEY
                    ,seckillMsg
                    ,msg -> {
                        msg.getMessageProperties()
                                .setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return msg;
                    }
                    ,correlationData
            );

            log.info("【秒杀】消息已投递：userId={}, goodsId={}, orderNo={}",
                    seckillMsg.getUserId(), seckillMsg.getGoodsId(), seckillMsg.getOrderNo());
        } catch (AmqpException e) {
            log.error("【秒杀】消息投递失败：{}", seckillMsg, e);
            throw new RuntimeException("秒杀消息投递失败",e);
        }

    }


}














