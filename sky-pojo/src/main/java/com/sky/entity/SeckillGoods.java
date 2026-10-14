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
public class SeckillGoods implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String image;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer stock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;         // 0未开始 1进行中 2已结束
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}