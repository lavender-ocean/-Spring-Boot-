package com.sky.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用通知消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMsg implements Serializable {

    /** 消息 ID（幂等） */
    private String msgId;

    /** 事件类型：
     * ORDER_PAID / ORDER_CONFIRMED / ORDER_DELIVERY
     * ORDER_COMPLETED / ORDER_CANCELLED
     */
    private String eventType;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNumber;

    /** 用户 ID（收件人） */
    private Long userId;

    /** 手机号（用于短信通道） */
    private String phone;

    /** 通知内容（展示给用户） */
    private String content;

    /** 事件时间戳 */
    private Long eventTime;

    public static NotificationMsg of(String eventType, Long orderId,
                                     String orderNumber, Long userId,
                                     String phone, String content) {
        return new NotificationMsg(
                java.util.UUID.randomUUID().toString(),
                eventType, orderId, orderNumber,
                userId, phone, content,
                System.currentTimeMillis()
        );
    }
}