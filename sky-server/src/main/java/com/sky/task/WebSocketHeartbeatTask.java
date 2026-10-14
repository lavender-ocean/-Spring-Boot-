package com.sky.task;

import com.sky.Websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WebSocket 心跳检测定时任务
 */
@Slf4j
@Component
public class WebSocketHeartbeatTask {

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 每 30 秒扫描一次，清理心跳超时的连接
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void checkHeartbeat() {
        log.debug("执行心跳扫描，当前在线数：{}", webSocketServer.getOnlineCount());
        webSocketServer.checkHeartbeat();
    }
}