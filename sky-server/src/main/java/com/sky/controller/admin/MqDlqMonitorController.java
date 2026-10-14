//package com.sky.controller.admin;
//
//import com.sky.annotation.RequiresRole;
//import com.sky.constant.RoleConstant;
//import com.sky.result.Result;
//import io.swagger.annotations.Api;
//import io.swagger.annotations.ApiOperation;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.core.QueueInformation;
//import org.springframework.amqp.rabbit.core.RabbitAdmin;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.HashMap;
//import java.util.Map;
//

//TODO MQ 死信队列监控

///**
// * MQ 死信队列监控（仅超管）
// */
//@Slf4j
//@RestController
//@RequestMapping("/admin/mq")
//@Api(tags = "MQ 监控")
//public class MqDlqMonitorController {
//
//    @Autowired
//    private RabbitAdmin rabbitAdmin;
//
//    /**
//     * 查询各队列消息数
//     */
//    @GetMapping("/queues")
//    @ApiOperation("查询所有队列状态")
//    @RequiresRole(RoleConstant.SUPER_ADMIN)
//    public Result<Map<String, Object>> queues() {
//        Map<String, Object> result = new HashMap<>();
//
//        // 业务队列
//        result.put("order.delay.queue", getQueueInfo("order.delay.queue"));
//        result.put("pay.callback.queue", getQueueInfo("pay.callback.queue"));
//        result.put("notify.ws.queue", getQueueInfo("notify.ws.queue"));
//        result.put("notify.sms.queue", getQueueInfo("notify.sms.queue"));
//
//        // 死信队列（重点关注）
//        result.put("order.dlq.queue", getQueueInfo("order.dlq.queue"));
//        result.put("pay.dlq.queue", getQueueInfo("pay.dlq.queue"));
//        result.put("notify.dlq.queue", getQueueInfo("notify.dlq.queue"));
//
//        return Result.success(result);
//    }
//
//    /**
//     * 只查死信队列（告警用）
//     */
//    @GetMapping("/dlq")
//    @ApiOperation("查询所有死信队列消息数")
//    @RequiresRole(RoleConstant.SUPER_ADMIN)
//    public Result<Map<String, Object>> dlq() {
//        Map<String, Object> result = new HashMap<>();
//        result.put("order.dlq.queue", getQueueInfo("order.dlq.queue"));
//        result.put("pay.dlq.queue", getQueueInfo("pay.dlq.queue"));
//        result.put("notify.dlq.queue", getQueueInfo("notify.dlq.queue"));
//        return Result.success(result);
//    }
//
//    private Map<String, Object> getQueueInfo(String queueName) {
//        Map<String, Object> info = new HashMap<>();
//        try {
//            QueueInformation queueInfo = rabbitAdmin.getQueueInfo(queueName);
//            if (queueInfo == null) {
//                info.put("exists", false);
//                info.put("messageCount", 0);
//            } else {
//                info.put("exists", true);
//                info.put("messageCount", queueInfo.getMessageCount());
//                info.put("consumerCount", queueInfo.getConsumerCount());
//            }
//        } catch (Exception e) {
//            log.error("查询队列失败：{}", queueName, e);
//            info.put("error", e.getMessage());
//        }
//        return info;
//    }
//}