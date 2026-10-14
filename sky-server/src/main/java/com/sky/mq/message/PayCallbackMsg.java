package com.sky.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付回调消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayCallbackMsg implements Serializable {

    /** 消息唯一 ID（幂等用） */
    private String msgId;

    /** 商户订单号 */
    private String outTradeNo;

    /** 微信支付交易号 */
    private String transactionId;

    /** 支付方式：1 微信，2 支付宝 */
    private Integer payMethod;

    /** 发送时间戳 */
    private Long sendTime;
}