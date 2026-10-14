package com.sky.constant;

/**
 * RabbitMQ 常量类
 * 命名规范：{模块}.{类型}.{动作}
 */
public class MqConstant {

    // ================== 交换机 ==================

    /** 订单延迟交换机（延迟插件） */
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay.timeout";

    /** 订单死信交换机（延迟队列重试耗尽后进入） */
    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";
    public static final String ORDER_DLQ_QUEUE = "order.dlq.queue";
    public static final String ORDER_DLX_ROUTING_KEY = "order.dlx.dead";

    // ================== 支付相关 ==================


    /** 支付回调交换机 */
    public static final String PAY_CALLBACK_EXCHANGE = "pay.callback.exchange";
    public static final String PAY_CALLBACK_QUEUE = "pay.callback.queue";
    public static final String PAY_CALLBACK_ROUTING_KEY = "pay.callback.success";

    /** 支付死信 */
    public static final String PAY_DLX_EXCHANGE = "pay.dlx.exchange";
    public static final String PAY_DLQ_QUEUE = "pay.dlq.queue";
    public static final String PAY_DLX_ROUTING_KEY = "pay.dlx.dead";


    // Fanout 不需要路由键，但保留占位

    // ================== 延迟时间 ==================

    /** 订单超时时间（毫秒）：15 分钟 */
    public static final long ORDER_TIMEOUT_DELAY_MS = 15 * 60 * 1000L;


    // ================== 通知相关 ==================

    public static final String NOTIFY_INBOX_QUEUE = "notify.inbox.queue";

    /** 通知 Fanout 交换机 */
    public static final String NOTIFY_EXCHANGE = "notify.exchange";

    /** WebSocket 推送队列 */
    public static final String NOTIFY_WS_QUEUE = "notify.ws.queue";

    /** 短信队列（Mock） */
    public static final String NOTIFY_SMS_QUEUE = "notify.sms.queue";

    /** 通知死信 */
    public static final String NOTIFY_DLX_EXCHANGE = "notify.dlx.exchange";
    public static final String NOTIFY_DLQ_QUEUE = "notify.dlq.queue";
    public static final String NOTIFY_DLX_ROUTING_KEY = "notify.dlx.dead";


    // ================== 秒杀相关 ==================

    /** 秒杀交换机 */
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    /** 秒杀队列 */
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    /** 秒杀路由键 */
    public static final String SECKILL_ROUTING_KEY = "seckill.order.submit";

    /** 秒杀死信 */
    public static final String SECKILL_DLX_EXCHANGE = "seckill.dlx.exchange";
    public static final String SECKILL_DLQ_QUEUE = "seckill.dlq.queue";
    public static final String SECKILL_DLX_ROUTING_KEY = "seckill.dlx.dead";
}