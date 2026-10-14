package com.sky.service.impl;

import com.sky.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 默认告警实现：只打日志
 * 生产环境替换为：DingTalkAlertServiceImpl / EmailAlertServiceImpl
 */
@Slf4j
@Service
public class LogAlertServiceImpl implements AlertService {

    @Override
    public void send(String title, String content) {
        log.error(" 【ALERT】{}\n{}", title, content);
        // 生产环境：
        // dingTalkClient.send(title, content);
        // emailClient.send(...);
    }
}