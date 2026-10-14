// ============================================================================
// 【已停用，整体注释保留备查】2026-10-13
//
// 停用原因：
//   1. 本类与 com.sky.service.OrderTimeoutService 逻辑完全重复（两份实现维护成本高）
//   2. 本类是"非 CAS"的旧实现：先 getById 判断状态、再无条件 update，
//      与支付回调存在竞态（订单刚被支付、这里又把状态改成已取消）
//   3. 第 2 层（Redisson 延迟队列 OrderDelayQueueConsumer）已改为调用
//      OrderTimeoutService.handle()，本类已无任何引用
//
// 代码原样保留，便于对比"旧实现 vs CAS 实现"的差异。
// ============================================================================
//package com.sky.mq;
//
//
//import com.sky.entity.OrderDetail;
//import com.sky.entity.Orders;
//import com.sky.mapper.DishMapper;
//import com.vueUser.mapper.OrderDetailMapper;
//import com.vueUser.mapper.OrderMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.checkerframework.checker.units.qual.A;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
///**
// * 订单超时处理器
// * 单独抽出：便于单元测试、便于事务控制
// */
//@Slf4j
//@Component
//public class OrderTimeoutHandler {
//
//    @Autowired
//    private OrderMapper orderMapper;
//
//    @Autowired
//    private OrderDetailMapper orderDetailMapper;
//
//    @Autowired
//    private DishMapper dishMapper;
//
//    /**
//     * 处理超时订单
//     * @param orderId 订单 ID
//     */
//    @Transactional(rollbackFor = Exception.class)
//    public void handler(Long orderId) {
//
//        Orders ordersDB = orderMapper.getById(orderId);
//        if (ordersDB == null) {
//            log.warn("订单不存在，忽略：orderId = {}", orderId);
//            return;
//        }
//
//
//        //  幂等：已经支付或已经取消的订单不处理
//        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
//            log.info("订单状态非待付款，忽略：orderId = {}, status = {}",
//                    orderId, ordersDB.getStatus());
//            return;
//        }
//
//        // 1. 取消订单
//        Orders orders = new Orders();
//        orders.setId(orderId);
//        orders.setStatus(Orders.CANCELLED);
//        orders.setCancelReason("订单超时未支付，自动取消");
//        orders.setCancelTime(LocalDateTime.now());
//        orderMapper.update(orders);
//
//
//        //2.回滚事务
//        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);
//
//        if(orderDetails != null && !orderDetails.isEmpty()){
//            for (OrderDetail orderDetail : orderDetails) {
//                if (orderDetail.getDishId() != null) {
//                    dishMapper.rollbackStock(orderDetail.getDishId(), orderDetail.getNumber());
//                    log.info("库存回滚：orderId = {}, dishId = {}, count = {}",
//                            orderId, orderDetail.getDishId(), orderDetail.getNumber());
//                }
//            }
//
//
//
//        }
//
//        log.info(" 订单超时自动取消成功：orderId = {}", orderId);
//
//
//
//    }
//
//
//}
