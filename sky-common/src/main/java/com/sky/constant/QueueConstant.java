package com.sky.constant;

/**
 * 队列常量
 */
public class QueueConstant {

    /** 订单超时延迟队列 */
    public static final String ORDER_DELAY_QUEUE = "queue:order:delay";

    /** 订单超时时间（分钟） */
    public static final long ORDER_TIMEOUT_MINUTES = 15;
}