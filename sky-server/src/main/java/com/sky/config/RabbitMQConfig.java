package com.sky.config;

import com.sky.constant.MqConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.mapper.MqFailedMsgMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


/**
 * RabbitMQ 通用配置
 * 核心目标：
 *   1. Jackson 序列化（消息可读，不是 JDK 乱码）
 *   2. Publisher Confirm 回调日志
 *   3. Return Callback 回调日志（路由失败）
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** ★ 发送侧失败（confirm 未确认 / 路由失败）落库，供管理端查询与人工介入 */
    @Autowired
    private MqFailedMsgMapper mqFailedMsgMapper;

    /**
     *
     * 消息转换器 使用 Jackson 序列化 替代 默认的JDK序列化
     * 消息内容可读 json
     * 跨语言兼容
     */


    //Jackson2JsonMessageConverter：必须加，否则消息是 JDK 序列化的乱码
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 初始化时给 RabbitTemplate 设置回调
     * 一定要在 @PostConstruct 里设置，因为 RabbitTemplate 是单例
     */
    @PostConstruct
    public void initRabbitTemplate() {

        // ===== 以下为改动前的原始实现（失败只打日志，消息丢了不留痕迹），保留备查 =====
        // // 1. Publisher Confirm 回调：消息是否到达 Broker
        // rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
        //     if (ack) {
        //         log.info(" 消息已到达 Broker，correlationData = {}", correlationData);
        //     } else {
        //         log.error(" 消息未到达 Broker，correlationData = {}, cause = {}",
        //                 correlationData, cause);
        //         // 生产环境：这里应该把消息落库，定时重发
        //     }
        //
        // });
        //
        // // 2. Return Callback：消息到达 Broker 但路由失败
        // rabbitTemplate.setReturnsCallback(returned -> {
        //     log.error("消息路由失败！replyCode = {}, replyText = {}, " +
        //                     "exchange = {}, routingKey = {}, message = {}",
        //             returned.getReplyCode(),
        //             returned.getReplyText(),
        //             returned.getExchange(),
        //             returned.getRoutingKey(),
        //             new String(returned.getMessage().getBody()));
        //
        // });

        // 1. Publisher Confirm 回调：消息是否到达 Broker
        //    ★ 改动：失败时把 msgId + 原因落 mq_failed_msg（原来只打日志，消息丢了没有任何痕迹）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String msgId = (correlationData == null) ? null : correlationData.getId();
            if (ack) {
                log.info(" 消息已到达 Broker，msgId = {}", msgId);
                return;
            }
            log.error(" 消息未到达 Broker，msgId = {}, cause = {}", msgId, cause);
            // confirm 回调拿不到消息体，只能落"msgId + 原因"用于人工追溯
            savePublishFailure("publish:unconfirmed", msgId, "confirm nack, cause = " + cause, null);
        });

        // 2. Return Callback：消息到达 Broker 但路由失败（必须 mandatory=true 才会触发）
        //    ★ 改动：这条能拿到完整消息体，落库后是可直接人工重发的
        rabbitTemplate.setReturnsCallback(returned -> {
            String body = new String(returned.getMessage().getBody());

            // ★★ 已知 broker 行为，必须过滤：x-delayed-message 交换机会对**每一条**延迟消息返回 NO_ROUTE
            //    （插件在发布瞬间尚未完成路由），但消息本身仍会按期投递。
            //    2026-10-14 实测证据：连发 3 条 5 秒延迟消息 → ReturnCallback 触发 3 次，
            //    同时 order.delay.queue 的 deliver_get / ack 各 +3（退回一份 + 照常投递），并非消息丢失。
            //    所以该 exchange 的 return 不携带有效信息，过滤掉可避免：
            //      ① 误报刷 ERROR 日志  ② 每笔订单都往 mq_failed_msg 插一条假失败记录
            if (MqConstant.ORDER_DELAY_EXCHANGE.equals(returned.getExchange())) {
                log.debug("【延迟交换机】x-delayed-message 插件已知误报，忽略：exchange={}, routingKey={}",
                        returned.getExchange(), returned.getRoutingKey());
                return;
            }

            log.error("消息路由失败！replyCode = {}, replyText = {}, exchange = {}, routingKey = {}, message = {}",
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    body);
            String msgId = returned.getMessage().getMessageProperties().getMessageId();
            savePublishFailure("publish:unroutable", msgId,
                    String.format("unroutable, exchange=%s, routingKey=%s, replyCode=%s",
                            returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode()),
                    body);
        });


    }

    /**
     * 发送侧失败落库（bizType = publish:unconfirmed / publish:unroutable）
     *
     * 能力边界（重要）：
     *   mq_failed_msg 表没有 exchange/routingKey 字段，且 confirm 失败时拿不到消息体，
     *   所以这两类失败**当前只做「留痕 + 人工介入」**，不会被 MqFailedMsgRetryTask 自动重投
     *   （该任务只处理业务队列的消费失败：seckill / pay / order:timeout）。
     *   要形成发送侧自动补偿，需要生产者在发送前预存 payload 与路由信息。
     */
    private void savePublishFailure(String bizType, String msgId, String reason, String payload) {
        try {
            // msgId 为空时用时间戳兜底，避免 uk_msg_id 冲突
            String id = (msgId == null || msgId.isEmpty())
                    ? bizType + "-" + System.currentTimeMillis()
                    : msgId;
            mqFailedMsgMapper.insertIgnore(MqFailedMsg.builder()
                    .bizType(bizType)
                    .msgId(id)
                    .payload(payload)
                    .failReason(reason)
                    .status("PENDING")
                    .retryCount(0)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("发送侧失败落库异常：bizType={}, msgId={}", bizType, msgId, e);
        }
    }

    //上延迟交换机 ==============================================================

    //延迟交换机（x-delayed-message 类型）

    @Bean
    public CustomExchange orderDelayExchange() {

        Map<String,Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");   // 内部使用 direct 路由
        return new CustomExchange(
                MqConstant.ORDER_DELAY_EXCHANGE,
                "x-delayed-message",
                true,   // durable
                false,  // autoDelete
                args
        );



    }

    //订单超时队列 ------ 绑定到死信交换机
    /**
     * 订单超时队列
     * 绑定了死信交换机：消费失败重试耗尽后进 DLQ
     */
    @Bean
    public Queue orderDelayQueue() {

        return QueueBuilder.durable(MqConstant.ORDER_DELAY_QUEUE)
                .deadLetterExchange(MqConstant.ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstant.ORDER_DLX_ROUTING_KEY)
                .build();
    }



     //延迟队列绑定到延迟交换机
     @Bean
     public Binding orderDelayBinding(Queue orderDelayQueue, CustomExchange orderDelayExchange) {
         return BindingBuilder
                 .bind(orderDelayQueue)
                 .to(orderDelayExchange)
                 .with(MqConstant.ORDER_DELAY_ROUTING_KEY)
                 .noargs();
     }


    // 死信交换机 + 队列 ==========================================
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(MqConstant.ORDER_DLX_EXCHANGE, true, false);

    }




    //死信队列（人工介入）

    @Bean
    public Queue orderDlqQueue() {
        return QueueBuilder.durable(MqConstant.ORDER_DLQ_QUEUE).build();
    }


     //死信队列绑定到死信交换机
    @Bean
    public Binding orderDlxBinding(Queue orderDlqQueue, DirectExchange orderDlxExchange) {
        return BindingBuilder
                .bind(orderDlqQueue)
                .to(orderDlxExchange)
                .with(MqConstant.ORDER_DLX_ROUTING_KEY);
    }



    // ================== 支付回调交换机 + 队列 + 死信 ==================

    @Bean
    public DirectExchange payCallbackExchange() {
        return new DirectExchange(MqConstant.PAY_CALLBACK_EXCHANGE, true, false);
    }

    @Bean
    public Queue payCallbackQueue() {
        return QueueBuilder.durable(MqConstant.PAY_CALLBACK_QUEUE)
                .deadLetterExchange(MqConstant.PAY_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstant.PAY_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding payCallbackBinding() {
        return BindingBuilder
                .bind(payCallbackQueue())
                .to(payCallbackExchange())
                .with(MqConstant.PAY_CALLBACK_ROUTING_KEY);
    }

    //死信
    @Bean
    public DirectExchange payDlxExchange() {
        return new DirectExchange(MqConstant.PAY_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue payDlqQueue() {
        return QueueBuilder.durable(MqConstant.PAY_DLQ_QUEUE).build();
    }

    @Bean
    public Binding payDlxBinding() {
        return BindingBuilder
                .bind(payDlqQueue())
                .to(payDlxExchange())
                .with(MqConstant.PAY_DLX_ROUTING_KEY);
    }


    // ================== 通知交换机 + 队列 ==================

    /**
     * 通知 Fanout 交换机
     */
    @Bean
    public FanoutExchange notifyExchange() {
        return new FanoutExchange(MqConstant.NOTIFY_EXCHANGE, true, false);
    }

    /**
     * WebSocket 推送队列
     */
    @Bean
    public Queue notifyWsQueue() {
        return QueueBuilder.durable(MqConstant.NOTIFY_WS_QUEUE)
                .deadLetterExchange(MqConstant.NOTIFY_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstant.NOTIFY_DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 短信队列
     */
    @Bean
    public Queue notifySmsQueue() {
        return QueueBuilder.durable(MqConstant.NOTIFY_SMS_QUEUE)
                .deadLetterExchange(MqConstant.NOTIFY_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstant.NOTIFY_DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定：Fanout 无路由键
     */
    @Bean
    public Binding notifyWsBinding() {
        return BindingBuilder.bind(notifyWsQueue()).to(notifyExchange());
    }

    // ✅ 改成：短信队列绑定到独立的 Direct 交换机
    @Bean
    public DirectExchange notifySmsExchange() {
        return new DirectExchange("notify.sms.exchange", true, false);
    }

    @Bean
    public Binding notifySmsBinding() {
        return BindingBuilder
                .bind(notifySmsQueue())
                .to(notifySmsExchange())
                .with("notify.sms.send");   // 路由键
    }
    /**
     * 通知死信
     */
    @Bean
    public DirectExchange notifyDlxExchange() {
        return new DirectExchange(MqConstant.NOTIFY_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue notifyDlqQueue() {
        return QueueBuilder.durable(MqConstant.NOTIFY_DLQ_QUEUE).build();
    }

    @Bean
    public Binding notifyDlxBinding() {
        return BindingBuilder
                .bind(notifyDlqQueue())
                .to(notifyDlxExchange())
                .with(MqConstant.NOTIFY_DLX_ROUTING_KEY);
    }


    // ================== 秒杀交换机 + 队列 + 死信 ==================

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MqConstant.SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(MqConstant.SECKILL_QUEUE)
                .deadLetterExchange(MqConstant.SECKILL_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstant.SECKILL_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder
                .bind(seckillQueue())
                .to(seckillExchange())
                .with(MqConstant.SECKILL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillDlxExchange() {
        return new DirectExchange(MqConstant.SECKILL_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillDlqQueue() {
        return QueueBuilder.durable(MqConstant.SECKILL_DLQ_QUEUE).build();
    }

    @Bean
    public Binding seckillDlxBinding() {
        return BindingBuilder
                .bind(seckillDlqQueue())
                .to(seckillDlxExchange())
                .with(MqConstant.SECKILL_DLX_ROUTING_KEY);
    }

}

















