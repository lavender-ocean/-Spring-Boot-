package com.sky.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMsg implements Serializable {

    /** 消息唯一 ID（幂等） */
    private String msgId;

    /** 用户 ID */
    private Long userId;

    /** 商品 ID */
    private Long goodsId;

    /** 订单号 */
    private String orderNo;

    /** 秒杀价格 */
    private java.math.BigDecimal amount;

    /** 发送时间戳 */
    private Long sendTime;

    public static SeckillMsg of(Long userId, Long goodsId, String orderNo, java.math.BigDecimal amount) {
        return new SeckillMsg(
                java.util.UUID.randomUUID().toString(),
                userId, goodsId, orderNo, amount,
                System.currentTimeMillis()
        );
    }
}