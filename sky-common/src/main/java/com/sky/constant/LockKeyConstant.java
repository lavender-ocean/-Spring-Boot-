package com.sky.constant;

/**
 * 分布式锁 Key 规范
 * 格式：lock:{业务}:{标识}
 */
public class LockKeyConstant {

    /** 下单幂等锁：lock:order:submit:{userId} */
    public static final String ORDER_SUBMIT_PREFIX = "lock:order:submit:";

    /** 菜品库存锁：lock:dish:stock:{dishId} */
    public static final String DISH_STOCK_PREFIX = "lock:dish:stock:";

    /** 支付幂等锁：lock:order:pay:{orderNumber} */
    public static final String ORDER_PAY_PREFIX = "lock:order:pay:";

    public static String orderSubmit(Long userId) {
        return ORDER_SUBMIT_PREFIX + userId;
    }

    public static String dishStock(Long dishId) {
        return DISH_STOCK_PREFIX + dishId;
    }

    public static String orderPay(String orderNumber) {
        return ORDER_PAY_PREFIX + orderNumber;
    }

    /** 秒杀对账任务锁 */
    public static final String TASK_SECKILL_RECONCILE = "task:seckill:reconcile";
}