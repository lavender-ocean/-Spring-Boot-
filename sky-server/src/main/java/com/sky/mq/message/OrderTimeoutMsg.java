package com.sky.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单超时消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMsg implements Serializable {

    // 订单 ID
    private Long orderId;

    // 消息唯一 ID（幂等用）
    private String msgId;

    // 发送时间戳
    private Long sendTime;
}