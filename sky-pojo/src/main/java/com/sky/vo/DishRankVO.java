package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishRankVO implements Serializable {
    private Long dishId;
    private String dishName;
    private Integer sales;
    private Long rank;      // 排名（1 = 第一）
}