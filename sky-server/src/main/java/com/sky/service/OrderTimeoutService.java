package com.sky.service;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.vueUser.mapper.OrderDetailMapper;
import com.vueUser.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OrderTimeoutService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private DishMapper dishMapper;

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long orderId) {
        Orders order = orderMapper.getById(orderId);
        if (order == null) {
            log.warn("订单不存在：{}", orderId);
            return;
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            log.info("订单状态非待付款，忽略：orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // ★ CAS：抢不到说明支付回调先到了 → 直接返回，绝不取消
        int affected = orderMapper.cancelWithCas(orderId, Orders.PENDING_PAYMENT,
                Orders.CANCELLED, "订单超时未支付，自动取消", LocalDateTime.now());
        if (affected == 0) {
            log.info("【超时】订单状态已被其他链路变更，跳过：orderId={}", orderId);
            return;
        }

        // ★ 只有 CAS 成功才回滚库存（原来 check-then-update 会和支付回调互相覆盖）
        List<OrderDetail> details = orderDetailMapper.getByOrderId(orderId);
        if (details != null && !details.isEmpty()) {
            for (OrderDetail d : details) {
                if (d.getDishId() != null) {
                    dishMapper.rollbackStock(d.getDishId(), d.getNumber());
                    log.info("库存回滚：orderId={}, dishId={}, count={}",
                            orderId, d.getDishId(), d.getNumber());
                }
            }
        }
        log.info("订单超时取消成功：orderId={}", orderId);
    }
}

//    @Transactional(rollbackFor = Exception.class)
//    public void handle(Long orderId) {
//        Orders order = orderMapper.getById(orderId);
//        if (order == null) {
//            log.warn("订单不存在：{}", orderId);
//            return;
//        }
//        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
//            log.info("订单状态非待付款，忽略：orderId={}, status={}", orderId, order.getStatus());
//            return;
//        }
//
//        // 更新订单
//        Orders update = new Orders();
//        update.setId(orderId);
//        update.setStatus(Orders.CANCELLED);
//        update.setCancelReason("订单超时未支付，自动取消");
//        update.setCancelTime(LocalDateTime.now());
//        orderMapper.update(update);
//
//        // 回滚库存
//        List<OrderDetail> details = orderDetailMapper.getByOrderId(orderId);
//        if (details != null && !details.isEmpty()) {
//            for (OrderDetail d : details) {
//                if (d.getDishId() != null) {
//                    dishMapper.rollbackStock(d.getDishId(), d.getNumber());
//                }
//            }
//        }
//        log.info("订单超时取消成功：orderId={}", orderId);
//    }
//}