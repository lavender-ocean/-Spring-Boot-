package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqFailedMsg implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String bizType;
    private String msgId;
    private String payload;
    private String failReason;
    private Integer retryCount;
    private String status;   // PENDING / RETRIED / GAVE_UP
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}