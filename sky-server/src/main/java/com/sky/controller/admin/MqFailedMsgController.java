package com.sky.controller.admin;

import com.sky.annotation.RequiresRole;
import com.sky.constant.RoleConstant;
import com.sky.entity.MqFailedMsg;
import com.sky.mapper.MqFailedMsgMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/mq/failed")
@Api(tags = "MQ 失败消息管理")
@Slf4j
public class MqFailedMsgController {

    @Autowired private MqFailedMsgMapper mqFailedMsgMapper;

    @GetMapping("/page")
    @ApiOperation("分页查询失败消息")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<PageResult> page(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String msgId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        com.github.pagehelper.PageHelper.startPage(page, pageSize);
        com.github.pagehelper.Page<MqFailedMsg> p =
                mqFailedMsgMapper.pageQuery(bizType, status, msgId);
        return Result.success(new PageResult(p.getTotal(), p.getResult()));
    }

    @PutMapping("/giveUp/{id}")
    @ApiOperation("手动放弃")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<String> giveUp(@PathVariable Long id) {
        mqFailedMsgMapper.giveUp(id);
        log.warn("【人工介入】放弃失败消息：id={}", id);
        return Result.success();
    }
}