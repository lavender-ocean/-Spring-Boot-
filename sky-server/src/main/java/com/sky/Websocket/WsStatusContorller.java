package com.sky.Websocket;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Api(tags = "WebSocket状态接口")
@RequestMapping("admin/ws")
@RestController
public class WsStatusContorller {


    @Autowired
    private WebSocketServer webSocketServer;

    @GetMapping("/online")
    @ApiOperation("查询当前在线客户端数量")
    public Result<Map<String,Object>> online(){

        Map<String,Object> map = new HashMap<>();

        map.put("count",webSocketServer.getOnlineCount());
        map.put("ids",webSocketServer.getOnlineIds());

        log.info("查询在线客户端{}",map);


        return Result.success(map);
    }




}
