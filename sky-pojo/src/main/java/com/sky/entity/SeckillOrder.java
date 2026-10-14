package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long goodsId;
    private String orderNo;
    private BigDecimal amount;
    private Integer status;         // 0处理中 1成功 2失败
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}