package com.sky.mq.consumer;


import com.rabbitmq.client.Channel;
import com.sky.constant.MqConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.MqFailedMsgMapper;
import com.sky.mq.message.OrderTimeoutMsg;
import com.sky.service.OrderTimeoutService;
import com.sky.utils.MqIdempotentHelper;
import com.vueUser.mapper.OrderDetailMapper;
import com.vueUser.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
/**
 * 订单超时消费者
 * 幂等 + 状态机 + 库存回滚
 */
public class OrderTimeoutConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MqIdempotentHelper mqIdempotentHelper;

    @Autowired
    private OrderTimeoutService orderTimeoutService;

    @Autowired
    private MqFailedMsgMapper mqFailedMsgMapper;


    @RabbitListener(queues = MqConstant.ORDER_DELAY_QUEUE)
    public void handleOrderTimeout(OrderTimeoutMsg msg, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("【超时】收到：orderId={}, msgId={}", msg.getOrderId(), msg.getMsgId());

        if (!mqIdempotentHelper.isFirstProcess("order:timeout", msg.getMsgId())) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            orderTimeoutService.handle(msg.getOrderId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("【超时】处理失败：{}", msg, e);
            mqIdempotentHelper.rollback("order:timeout", msg.getMsgId());
            channel.basicNack(deliveryTag, false, false);
        }
    }

//    //消费订单超时消息
//    @Transactional(rollbackFor = Exception.class)
//    @RabbitListener(queues = MqConstant.ORDER_DELAY_QUEUE)
//    public void handleOrderTimeout(
//            OrderTimeoutMsg msg,
//            Channel channel,
//            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
//
//        //订单ID
//        Long orderId = msg.getOrderId();
//        //消息ID
//        String msgId = msg.getMsgId();
//        log.info("收到订单超时消息：orderId = {}, msgId = {}", orderId, msgId);
//
//        //第一层 ：redis幂等
////        String idempotentKey = "mq:idempotent:order:timeout:" + msgId;
////
////        Boolean isFirstProcess = stringRedisTemplate.opsForValue()
////                .setIfAbsent(idempotentKey,"1",7, TimeUnit.DAYS);
////
////        if(Boolean.FALSE.equals(isFirstProcess)){
////            log.warn("消息已处理，忽略：msgId = {}", msgId);
////            channel.basicAck(deliveryTag,false);
////            return;
////        }
//
//        if (!mqIdempotentHelper.isFirstProcess("order:timeout", msg.getMsgId())) {
//            channel.basicAck(deliveryTag, false);
//            return;
//        }
//
//        // 第二层： 状态机校验
//
//        Orders order = orderMapper.getById(orderId);
//        if (order == null) {
//            log.warn("订单不存在，忽略：orderId = {}", orderId);
//            channel.basicAck(deliveryTag, false);
//            return;
//        }
//
//        // 只有"待付款"才能取消
//        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
//            log.info("订单状态非待付款，忽略：orderId = {}, status = {}",
//                    orderId, order.getStatus());
//            channel.basicAck(deliveryTag, false);
//            return;
//        }
//
//        //第三层：乐观锁更新
//        Orders update = new Orders();
//        update.setId(orderId);
//        update.setStatus(Orders.CANCELLED);
//        update.setCancelReason("订单超时未支付，自动取消");
//        update.setCancelTime(LocalDateTime.now());
//        orderMapper.update(update);
//
//        //第四层 ：库存回滚
//
//        try {
//            List<OrderDetail> details =
//                    orderDetailMapper.getByOrderId(orderId);
//            //先判断是否非空
//            if(details != null && !details.isEmpty()){
//                for(OrderDetail orderDetail : details){
//                    //再判断是否非空
//                    if(orderDetail.getOrderId() != null){
//                        dishMapper.rollbackStock(
//                                orderDetail.getDishId(),
//                                orderDetail.getNumber());
//
//                        log.info("库存回滚：orderId = {}, dishId = {}, count = {}",
//                                orderId, orderDetail.getDishId(), orderDetail.getNumber());
//                    }
//                }
//            }
//
//            log.info("订单超时取消成功：orderId = {}", orderId);
//            channel.basicAck(deliveryTag,false);
//        } catch (IOException e) {
//            log.error("处理订单超时失败：orderId = {}, msgId = {}", orderId, msgId, e);
//
//            // 决定消息是否重回队列
//            // require = false：不重回队列，直接进死信
//
//            mqIdempotentHelper.rollback("order:timeout", msg.getMsgId());
//
//
//            channel.basicNack(deliveryTag, false, false);
//        }
//
//
//    }

    //死信队列监听 ----- 人工介入
    @RabbitListener(queues = MqConstant.ORDER_DLQ_QUEUE)
    public void handleDlq(
            OrderTimeoutMsg msg
            , Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("!!!!订单超时消息进入死信队列，需人工介入!!!!!!!!：{}", msg);

        try {
            mqFailedMsgMapper.insertIgnore(MqFailedMsg.builder()
                    .bizType("order:timeout")
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














