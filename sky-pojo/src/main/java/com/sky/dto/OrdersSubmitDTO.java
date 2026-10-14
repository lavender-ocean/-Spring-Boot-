package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdersSubmitDTO implements Serializable {
    //地址簿id
    private Long addressBookId;



    //付款方式
    private int payMethod;
    //备注
    private String remark;
    //预计送达时间
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;
    //配送状态  1立即送出  0选择具体时间
    private Integer deliveryStatus;
    //餐具数量
    private Integer tablewareNumber;
    //餐具数量状态  1按餐量提供  0选择具体数量
    private Integer tablewareStatus;
    //打包费
    // ===== 以下为改动前的原始注释，保留备查 =====
    // // 打包费？？？
    // ★ 类型必须与 Orders 实体/DB（int）一致：如果用 BigDecimal，
    //   BeanUtils.copyProperties 会因类型不兼容而【静默跳过】这个字段（前端传了也丢）
    //   前端类型：packAmount?: number（见 front/sky_user/src/api/user/order.ts）
    private Integer packAmount;
    // ===== 以下为改动前的原始实现（BigDecimal，与实体 Integer 不兼容 → 字段被静默丢弃），保留备查 =====
    // private BigDecimal packAmount;   // ← 改成和 Orders 实体一致的类型
    //总金额
    private BigDecimal amount;
}
