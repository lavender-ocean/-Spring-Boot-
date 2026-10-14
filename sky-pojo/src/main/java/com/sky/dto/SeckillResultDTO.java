package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultDTO implements Serializable {

    /** 状态码：1成功排队中 0售罄 -1重复 -2未开始 -3系统异常 */
    private Integer code;

    private String message;

    private String orderNo;

    public static SeckillResultDTO queuing(String orderNo) {
        return new SeckillResultDTO(1, "抢购成功，正在排队处理", orderNo);
    }

    public static SeckillResultDTO soldOut() {
        return new SeckillResultDTO(0, "商品已售罄", null);
    }

    public static SeckillResultDTO duplicate() {
        return new SeckillResultDTO(-1, "您已参与过该秒杀，请勿重复购买", null);
    }

    public static SeckillResultDTO notStarted() {
        return new SeckillResultDTO(-2, "秒杀活动未开始或已结束", null);
    }

    public static SeckillResultDTO error(String msg) {
        return new SeckillResultDTO(-3, msg, null);
    }
}