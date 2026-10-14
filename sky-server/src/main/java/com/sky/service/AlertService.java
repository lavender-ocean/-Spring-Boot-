package com.sky.service;

/**
 * 告警服务
 * 生产环境可扩展：钉钉、飞书、邮件、短信
 */
public interface AlertService {

    /**
     * 发送告警
     * @param title 标题
     * @param content 内容
     */
    void send(String title, String content);
}