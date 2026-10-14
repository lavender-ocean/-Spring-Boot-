package com.vueUser.service.Impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.sky.Websocket.WebSocketServer;
import com.sky.constant.LockKeyConstant;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mock.properties.MockPayProperties;
import com.sky.mq.OrderDelayQueueConsumer;
import com.sky.mq.OrderDelayQueueProducer;
import com.sky.mq.message.NotificationMsg;
import com.sky.mq.producer.NotificationProducer;
import com.sky.mq.producer.NotificationSmsProducer;
import com.sky.mq.producer.OrderTimeoutProducer;
import com.sky.mq.producer.PayCallbackProducer;
import com.sky.result.PageResult;
import com.sky.service.RefundService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.vueUser.mapper.*;
import com.vueUser.service.OrderService;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;


    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    //用于 submit中 来进行 回滚
    @Autowired
    private DishMapper dishMapper;

    //加入分布式锁
    @Autowired
    private RedissonClient redissonClient;


    //加入延迟队列
    @Autowired
    private OrderDelayQueueProducer orderDelayQueueProducer;

    //加入RabbitMQ
    @Autowired
    private OrderTimeoutProducer orderTimeoutProducer;

    //加入Mock模拟支付
    @Autowired
    private MockPayProperties mockPayProperties;
    @Autowired
    private PayCallbackProducer payCallbackProducer;

    //加入给用户的信息推送
    @Autowired
    private NotificationProducer notificationProducer;

    @Autowired
    private RefundService refundService;

    /**
     * 带分布式锁的库存扣减
     * @param dishId 菜品 ID
     * @param count  扣减数量
     * @return true = 扣减成功，false = 库存不足
     */
    public boolean deductDishStockWithLock(Long dishId,Integer count){

        //拿到redis中的key
        String lockKey = LockKeyConstant.dishStock(dishId);
        //拿到锁对象
        RLock rLock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try{

            acquired = rLock.tryLock(3, TimeUnit.SECONDS);

            if(!acquired){
                log.warn("获取库存锁超时：dishId={}", dishId);
                return false;
            }

            //数据库原子扣减

            int affected = dishMapper.deductStock(dishId, count);
            if (affected > 0) {
                log.info(" 库存扣减成功：dishId={}, count={}", dishId, count);
                return true;
            } else {
                log.warn(" 库存不足：dishId={}, count={}", dishId, count);
                return false;
            }


        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("库存扣减被中断：dishId={}", dishId, e);
            return false;
        } finally {
            if(acquired && rLock.isHeldByCurrentThread()){
                rLock.unlock();
            }
        }


    }



















    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional(rollbackFor = Exception.class)    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());


        log.info("跑到这里来了");

//
//
//        if (addressBook == null) {
//            //地址簿为空，业务异常
//            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
//        }


        Long currentUserId = BaseContext.getCurrentId();
        Long addressBookId = ordersSubmitDTO.getAddressBookId();


        // 1. 优先用前端传的 addressBookId
        if (addressBookId != null && addressBookId > 0) {
            addressBook = addressBookMapper.getById(addressBookId);

            // 2. 查到了才校验归属
            if (addressBook != null && !addressBook.getUserId().equals(currentUserId)) {
                throw new AddressBookBusinessException("无权使用该地址");
            }
        }

        // 3. 没传或查不到 → 用默认地址兜底
        if (addressBook == null) {
            AddressBook query = new AddressBook();
            query.setUserId(currentUserId);
            query.setIsDefault(1);
            List<AddressBook> defaults = addressBookMapper.list(query);
            if (defaults != null && !defaults.isEmpty()) {
                addressBook = defaults.get(0);
                log.info("【下单】使用默认地址兜底：addressId={}", addressBook.getId());
            }
        }

        // 4. 还是没有 → 抛业务异常
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }


        //地址结束

        //先获取当前的用户id
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        //查当前用户的购物车数据
        List<ShoppingCart> shop_list = shoppingCartMapper.list(shoppingCart);

        if(shop_list == null || shop_list.size() == 0) {
            //没有数据
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //手动设置数据进去
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        //  补这一行：把兜底地址的 id 写回订单
        orders.setAddressBookId(addressBook.getId());

        //TODO 用雪花算法更好

        // 旧：毫秒时间戳，同毫秒下单会重复
//        orders.setNumber(String.valueOf(System.currentTimeMillis()));

        // 新：毫秒 + 用户ID后4位 + 6位随机
        String orderNo = System.currentTimeMillis()
                + String.format("%04d", userId % 10000)
                + String.format("%06d",
                java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
        orders.setNumber(orderNo);


        //手机号在addressBook这个表中
        orders.setPhone(addressBook.getPhone());

        orders.setUserId(userId);

        // TODO 后端自己来计算金额

        // ============ 兜底赋值区 ============
// 金额：后端重新算，不信前端
        BigDecimal totalAmount = shop_list.stream()
                .map(cart -> cart.getAmount().multiply(new BigDecimal(cart.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orders.setAmount(totalAmount);

// 配送状态：默认"立即送出"
        if (orders.getDeliveryStatus() == null) {
            orders.setDeliveryStatus(1);
        }

        if (orders.getTablewareStatus() == null) {
            orders.setTablewareStatus(1);
        }

        // ★ 餐具数量：前端没传时要给默认值，否则 Integer 字段会写 NULL
        //   （实体已从 int 改为 Integer，不再有 0 兜底；语义上 1 = 按餐量提供）
        if (orders.getTablewareNumber() == null) {
            orders.setTablewareNumber(1);
        }

        // ★ 打包费：同理，没传时默认 0（DTO 已改为 Integer，能正常拷贝进来）
        if (orders.getPackAmount() == null) {
            orders.setPackAmount(0);
        }


// 收货人 / 地址（从 addressBook 取，方便订单详情显示）
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(
                addressBook.getProvinceName() + addressBook.getCityName() +
                        addressBook.getDistrictName() + addressBook.getDetail()
        );
// ===================================


        log.info("金额：{}", totalAmount);



        log.info("订单的样子：{}", orders);

        //订单组装完毕 ----------========------------

        //遍历购物车 扣减库存
        // 记录 已经扣除菜品的id （用于 回滚）
        Map<Long,Integer> deductedMap = new HashMap<>();
        try {
            for(ShoppingCart cart : shop_list) {
                //只对菜品来进行扣除库存 ---- 套餐不需要 -> 套餐是根据有没有菜品来判断能不能卖的
                if(cart.getDishId() != null) {
                    Integer count = cart.getNumber();
                    boolean success = deductDishStockWithLock(
                            cart.getDishId(),
                            count
                    );
                    if(!success) {
                        // 库存不足 → 抛出业务异常
                        throw new OrderBusinessException("【" + cart.getName() + "】库存不足");
                    }
                    // 记录成功扣减的菜品（如果同菜品多行，累加）
                    deductedMap.merge(cart.getDishId(), count, Integer::sum);                }
            }
        } catch (Exception e) {
            // ！！！ 出错 → 回滚已扣的库存
            deductedMap.forEach((dishId, count) -> {
                dishMapper.rollbackStock(dishId, count);
                log.warn("库存回滚：dishId={}, count={}", dishId, count);
            });
            throw e;
        }



        orderMapper.insert(orders);

        List<OrderDetail> orderDetails = new ArrayList<>();

        for(ShoppingCart cart : shop_list) {
            OrderDetail orderDetail = new OrderDetail(); //订单明细
            BeanUtils.copyProperties(cart, orderDetail);

            orderDetail.setOrderId(orders.getId());//设置订单明细关联的订单id

            orderDetails.add(orderDetail);


        }

        orderDetailMapper.insertBatch(orderDetails);


        //4. 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);


        //加入 延迟队列 ==============================================================================
        //关键：投递到延迟队列（15 分钟后触发超时处理）
//        orderDelayQueueProducer.offer(orders.getId());



        // 关键：三层投递放到事务提交后

        final Long orderId = orders.getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            // 第 1 层：RabbitMQ 延迟插件（主链路）
                            orderTimeoutProducer.sendTimeOutMsg(orderId, 15 * 60 * 1000L);
                            log.info("【第 1 层】 MQ 超时消息已投递：orderId = {}", orderId);
                        } catch (Exception e) {
                            log.error("【第 1 层】 MQ 投递失败：orderId = {}", orderId, e);
                        }

                        try {
                            // 第 2 层：Redisson 延迟队列（备链路）
                            orderDelayQueueProducer.offer(orderId);
                            log.info("【第 2 层】 Redisson 延迟消息已投递：orderId = {}", orderId);
                        } catch (Exception e) {
                            log.error("【第 2 层】 Redisson 投递失败：orderId = {}", orderId, e);
                        }

                        // 第 3 层：@Scheduled 每分钟自动扫，无需投递
                    }
                }
        );

        //封装
        OrderSubmitVO orderSubmitVO = new OrderSubmitVO();
        orderSubmitVO.setId(orders.getId());
        orderSubmitVO.setOrderTime(orders.getOrderTime());
        orderSubmitVO.setOrderNumber(orders.getNumber());
        orderSubmitVO.setOrderAmount(orders.getAmount());

        // 填充地址信息
        orderSubmitVO.setAddressBookId(addressBook.getId());
        orderSubmitVO.setConsignee(addressBook.getConsignee());
        orderSubmitVO.setPhone(addressBook.getPhone());
        orderSubmitVO.setAddress(
                addressBook.getProvinceName() + addressBook.getCityName() +
                        addressBook.getDistrictName() + addressBook.getDetail()
        );

        return orderSubmitVO;
    }


    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }



    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);


        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 越权校验
        Long currentId = BaseContext.getCurrentId();
        if (!orders.getUserId().equals(currentId)) {
            log.warn("越权查看订单：orderId={}, ownerUserId={}, currentUserId={}",
                    id, orders.getUserId(), currentId);
            throw new OrderBusinessException("无权查看该订单");
        }

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }


    /**
     * 用户取消订单
     *
     * @param id
     */

    //TODO 微信退款 一定要做

    @Override
    @Transactional(rollbackFor = Exception.class)     // ★ 补事务：退款/改状态/回滚库存三步原子
    public void userCancelById(Long id) throws Exception {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Long currentId = BaseContext.getCurrentId();
        if (!orders.getUserId().equals(currentId)) {
            log.warn("越权取消订单：orderId={}, ownerUserId={}, currentUserId={}",
                    id, orders.getUserId(), currentId);
            throw new OrderBusinessException("无权取消该订单");
        }
        if (orders.getStatus() > Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        boolean needRefund = Orders.PAID.equals(orders.getPayStatus());

        // ★ CAS 抢占状态：抢不到说明已被超时任务/其他请求改过
        int affected = orderMapper.cancelWithCas(
                id, orders.getStatus(), Orders.CANCELLED, "用户取消", LocalDateTime.now());
        if (affected == 0) {
            throw new OrderBusinessException("订单状态已变更，请刷新后重试");
        }

        // 抢占成功后才退款；退款抛异常 → 事务回滚 → 订单回到原状态，用户可重试
        if (needRefund) {
            refundService.refund(orders.getNumber(), orders.getAmount(), orders.getAmount());
            orderMapper.updatePayStatus(id, Orders.REFUND);   // ★ 补：退款后置为 REFUND
        }

        // CAS 保证这里只会执行一次
        rollbackDishStock(id);
    }
//    public void userCancelById(Long id) throws Exception {
//        Orders orders = orderMapper.getById(id);
//        if (orders == null) {
//            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
//        }
//
//        // 越权校验
//        Long currentId = BaseContext.getCurrentId();
//        if (!orders.getUserId().equals(currentId)) {
//            log.warn("越权取消订单：orderId={}, ownerUserId={}, currentUserId={}",
//                    id, orders.getUserId(), currentId);
//            throw new OrderBusinessException("无权取消该订单");
//        }
//
//        // 状态校验：只有 1待付款 / 2待接单 才能取消
//        if (orders.getStatus() > 2) {
//            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
//        }
//
//        //  已支付的走退款
//        if (Orders.PAID.equals(orders.getPayStatus())
//                && Orders.TO_BE_CONFIRMED.equals(orders.getStatus())) {
//            refundService.refund(
//                    orders.getNumber(),
//                    orders.getAmount(),
//                    orders.getAmount()
//            );
//        }
//
//        Orders update = new Orders();
//        update.setId(orders.getId());
//        update.setStatus(Orders.CANCELLED);
//        update.setCancelReason("用户取消");
//        update.setCancelTime(LocalDateTime.now());
//        orderMapper.update(update);
//
//        // 回滚库存
//        rollbackDishStock(orders.getId());
//    }





//    public void userCancelById(Long id) throws Exception {
////        // 根据id查询订单
////        Orders ordersDB = orderMapper.getById(id);
////
////        // 校验订单是否存在
////        if (ordersDB == null) {
////            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
////        }
////
////        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
////        if (ordersDB.getStatus() > 2) {
////            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
////        }
////
////        Orders orders = new Orders();
////        orders.setId(ordersDB.getId());
////
////        // 订单处于待接单状态下取消，需要进行退款
////        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
////            //调用微信支付退款接口
////            weChatPayUtil.refund(
////                    ordersDB.getNumber(), //商户订单号
////                    ordersDB.getNumber(), //商户退款单号
////                    new BigDecimal(0.01),//退款金额，单位 元
////                    new BigDecimal(0.01));//原订单金额
////
////            //支付状态修改为 退款
////            orders.setPayStatus(Orders.REFUND);
////        }
////
////        // 更新订单状态、取消原因、取消时间
////        orders.setStatus(Orders.CANCELLED);
////        orders.setCancelReason("用户取消");
////        orders.setCancelTime(LocalDateTime.now());
////        orderMapper.update(orders);
//
//
//        Orders orders = orderMapper.getById(id);
//        if (orders == null) {
//            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
//        }
//
//        // 越权校验
//        Long currentId = BaseContext.getCurrentId();
//        if (!orders.getUserId().equals(currentId)) {
//            log.warn("越权取消订单：orderId={}, ownerUserId={}, currentUserId={}",
//                    id, orders.getUserId(), currentId);
//            throw new OrderBusinessException("无权取消该订单");
//        }
//
//        // 状态校验：只有 1待付款 / 2待接单 才能取消
//        if (orders.getStatus() > 2) {
//            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
//        }
//
//        Orders orders1 = new Orders();
//        orders1.setId(orders.getId());
//
//        // 已支付的需要退款（微信）
//        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)
//                && Orders.PAID.equals(orders.getPayStatus())) {
//            // TODO 真实退款：
//            // weChatPayUtil.refund(ordersDB.getNumber(), ordersDB.getNumber(),
//            //         ordersDB.getAmount(), ordersDB.getAmount());
//            // orders.setPayStatus(Orders.REFUND);
//            log.info("订单已支付，需要退款：orderId={}", id);
//        }
//
//        orders.setStatus(Orders.CANCELLED);
//        orders.setCancelReason("用户取消");
//        orders.setCancelTime(LocalDateTime.now());
//
//        // 回滚库存
//        rollbackDishStock(orders.getId());
//
//        orderMapper.update(orders);
//
//
//    }

    private void rollbackDishStock(Long orderId) {
        List<OrderDetail> details = orderDetailMapper.getByOrderId(orderId);
        if (details == null || details.isEmpty()){
            return;
        }

        for (OrderDetail orderDetail : details) {
            if(orderDetail.getDishId() != null) {
                dishMapper.rollbackStock(orderDetail.getDishId(), orderDetail.getNumber());

                log.info("库存回滚：orderId={}, dishId={}, count={}",
                        orderId, orderDetail.getDishId(), orderDetail.getNumber());
            }
        }
    }

    /**
     * 再来一单
     *
     * @param id
     */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 补：越权校验（原来可以直接传别人订单 id，把别人的订单明细复制进自己购物车）
        Orders orders = orderMapper.getById(id);
        if (orders == null || !orders.getUserId().equals(userId)) {
            throw new OrderBusinessException("无权操作该订单");
        }

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException("该订单没有菜品明细，无法再来一单");
        }


        // 空判断：订单不存在或没有明细
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException("该订单没有菜品明细，无法再来一单");
        }



        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }














    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     *
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }



    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {

        Long id = ordersConfirmDTO.getId();
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            return;
        }
        if(orders.getStatus() == Orders.TO_BE_CONFIRMED) {
             orders = Orders.builder()
                    .id(ordersConfirmDTO.getId())
                    .status(Orders.CONFIRMED)
                    .build();

            orderMapper.update(orders);
        }else{
            throw  new OrderBusinessException("订单还未到待接单");
        }


    }


    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (ordersDB == null || !Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        int affected = orderMapper.cancelWithCas(ordersRejectionDTO.getId(),
                Orders.TO_BE_CONFIRMED, Orders.CANCELLED, null, LocalDateTime.now());
        if (affected == 0) {
            throw new OrderBusinessException("订单状态已变更，请刷新后重试");
        }

        // 拒单原因单独写（cancelWithCas 写的是 cancel_reason）
        Orders upd = new Orders();
        upd.setId(ordersRejectionDTO.getId());
        upd.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orderMapper.update(upd);

        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refundService.refund(ordersDB.getNumber(), ordersDB.getAmount(), ordersDB.getAmount());
            orderMapper.updatePayStatus(ordersRejectionDTO.getId(), Orders.REFUND);
        }

        rollbackDishStock(ordersRejectionDTO.getId());   // ★ 补：拒单必须回滚库存
    }
//    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
//        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
//        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
//            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
//        }
//
//        // 统一走 RefundService（Mock 模式自动跳过）
//        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
//            refundService.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getAmount(),
//                    ordersDB.getAmount()
//            );
//        }
//
//        // 更新订单状态
//        Orders orders = new Orders();
//        orders.setId(ordersDB.getId());
//        orders.setStatus(Orders.CANCELLED);
//        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
//        orders.setCancelTime(LocalDateTime.now());
//        orderMapper.update(orders);
//    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.CANCELLED.equals(ordersDB.getStatus())
                || Orders.COMPLETED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        int affected = orderMapper.cancelWithCas(ordersCancelDTO.getId(), ordersDB.getStatus(),
                Orders.CANCELLED, ordersCancelDTO.getCancelReason(), LocalDateTime.now());
        if (affected == 0) {
            throw new OrderBusinessException("订单状态已变更，请刷新后重试");
        }

        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refundService.refund(ordersDB.getNumber(), ordersDB.getAmount(), ordersDB.getAmount());
            orderMapper.updatePayStatus(ordersCancelDTO.getId(), Orders.REFUND);
        }

        // ★ 只有「未派送」的订单才回滚库存：派送中/已完成的菜已经做掉了，回滚等于虚增可售库存
        if (ordersDB.getStatus() <= Orders.CONFIRMED) {
            rollbackDishStock(ordersCancelDTO.getId());
        }
    }



//    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
//        // 根据id查询订单
//        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
//        if (ordersDB == null) {
//            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
//        }
//
//        //  已支付走统一退款
//        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
//            refundService.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getAmount(),
//                    ordersDB.getAmount()
//            );
//        }
//
//        // 更新订单状态
//        Orders orders = new Orders();
//        orders.setId(ordersCancelDTO.getId());
//        orders.setStatus(Orders.CANCELLED);
//        orders.setCancelReason(ordersCancelDTO.getCancelReason());
//        orders.setCancelTime(LocalDateTime.now());
//        orderMapper.update(orders);
//    }
//
//

    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }



    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }




    //TODO 用户微信支付订单
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */

    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {

        // ========== Mock 模式 ==========
        if (mockPayProperties.isEnabled()) {
            log.info("[Mock] 生成模拟支付参数：outTradeNo = {}", ordersPaymentDTO.getOrderNumber());

            // 返回假的支付参数
            OrderPaymentVO vo = new OrderPaymentVO();
            vo.setNonceStr(UUID.randomUUID().toString().replace("-", ""));
            vo.setPaySign("MOCK_SIGN");
            vo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
            vo.setSignType("RSA");
            vo.setPackageStr("prepay_id=MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));

            // 关键：模拟"用户支付耗时"，异步触发回调
            // 用延迟线程池或 MQ 延迟消息都可以
            // 这里用 ScheduledExecutorService 简单模拟
            simulateUserPay(ordersPaymentDTO.getOrderNumber());

            return vo;
        }

        // ========== 真实微信支付（保留）==========
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),
                new BigDecimal(0.01),
                "外卖订单",
                user.getOpenid()
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        return vo;
    }

    /**
     * Mock 模式下：延迟 payDelayMs 后模拟微信回调
     */

    /** Mock 模式：延迟模拟支付完成。 用专用调度线程池，避免占用 ForkJoinPool.commonPool */
    private final ScheduledExecutorService mockPayScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mock-pay-simulator");
                t.setDaemon(true);
                return t;
            });

    private void simulateUserPay(String outTradeNo) {
        long delayMs = mockPayProperties.getPayDelayMs();
        log.info("[Mock] {}ms 后模拟用户支付完成：outTradeNo = {}", delayMs, outTradeNo);

        mockPayScheduler.schedule(() -> {
            try {
                payCallbackProducer.sendPayCallback(outTradeNo, "MOCK_TRANSACTION", 1);
            } catch (Exception e) {
                log.error("[Mock] 模拟支付回调投递失败：outTradeNo={}", outTradeNo, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

//    private void simulateUserPay(String outTradeNo) {
//        long delayMs = mockPayProperties.getPayDelayMs();
//        log.info("[Mock] {}ms 后模拟用户支付完成：outTradeNo = {}", delayMs, outTradeNo);
//
//        // 用线程池异步执行
//        java.util.concurrent.CompletableFuture.runAsync(() -> {
//            try {
//                Thread.sleep(delayMs);
//                // 投递回调消息到 MQ
//                payCallbackProducer.sendPayCallback(outTradeNo, "MOCK_TRANSACTION", 1);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        });
//    }




    /**
     * 支付成功回调
     * ★ 修复竞态：原实现「查 payStatus → 无条件 update」非原子，
     *   已取消订单收到迟到回调会被复活（但库存已回滚 → 账实不符）。
     *   改为 CAS：抢不到「待付款+未支付」状态就退款，绝不复活订单。
     */
    @Override
    public void paySuccess(String outTradeNo) {
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            log.warn("订单不存在：outTradeNo = {}", outTradeNo);
            return;
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            log.info("订单已支付，忽略重复回调：outTradeNo = {}", outTradeNo);
            return;
        }

        int affected = orderMapper.payWithCas(
                ordersDB.getId(),
                Orders.PENDING_PAYMENT,     // 期望状态：待付款
                Orders.TO_BE_CONFIRMED,     // 目标状态：待接单
                Orders.UN_PAID,             // 期望支付状态：未支付
                Orders.PAID,                // 目标支付状态：已支付
                LocalDateTime.now());

        if (affected == 0) {
            // 订单已被超时任务/用户取消 → 钱已收到但订单不成立 → 必须退款
            log.error("【支付】订单非待付款，触发退款：orderId={}, status={}, payStatus={}",
                    ordersDB.getId(), ordersDB.getStatus(), ordersDB.getPayStatus());
            try {
                refundService.refund(ordersDB.getNumber(), ordersDB.getAmount(), ordersDB.getAmount());
                orderMapper.updatePayStatus(ordersDB.getId(), Orders.REFUND);
            } catch (Exception e) {
                log.error("【支付】退款失败，需人工介入：orderId={}", ordersDB.getId(), e);
            }
            return;
        }

        // ===== 商家来单提醒 =====
        Map map = new HashMap();
        map.put("type", 1);
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号:" + outTradeNo);
        webSocketServer.sendToRole("EMP", JSON.toJSONString(map));

        // ===== C 端用户通知 =====
        NotificationMsg notifyMsg = NotificationMsg.of(
                "ORDER_PAID",
                ordersDB.getId(),
                ordersDB.getNumber(),
                ordersDB.getUserId(),
                ordersDB.getPhone(),
                "您的订单已支付成功，商家正在接单");
        notificationProducer.sendNotification(notifyMsg);

        log.info("支付成功处理完成：outTradeNo = {}", outTradeNo);
    }
//    public void paySuccess(String outTradeNo) {
//        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
//
//        if (ordersDB == null) {
//            log.warn("订单不存在：outTradeNo = {}", outTradeNo);
//            return;
//        }
//
//        // 幂等：已支付直接返回
//        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
//            log.info("订单已支付，忽略重复回调：outTradeNo = {}", outTradeNo);
//            return;
//        }
//
//        // 更新订单
//        Orders orders = Orders.builder()
//                .id(ordersDB.getId())
//                .status(Orders.TO_BE_CONFIRMED)
//                .payStatus(Orders.PAID)
//                .checkoutTime(LocalDateTime.now())
//                .build();
//        orderMapper.update(orders);
//


//
//        // 通过 WebSocket 推送来单提醒
//        Map map = new HashMap();
//        map.put("type", 1);
//        map.put("orderId", ordersDB.getId());
//        map.put("content", "订单号:" + outTradeNo);
//
//        String jsonString = JSON.toJSONString(map);
//        webSocketServer.sendToRole("EMP", jsonString);
//
//        log.info("支付成功处理完成：outTradeNo = {}", outTradeNo);



//        // ========== 商家推送（管理端）==========
//        Map map = new HashMap();
//        map.put("type", 1);
//        map.put("orderId", ordersDB.getId());
//        map.put("content", "订单号:" + outTradeNo);
//        webSocketServer.sendToRole("EMP", JSON.toJSONString(map));
//
//        // ========== 用户通知（C端）==========
//        NotificationMsg notifyMsg = NotificationMsg.of(
//                "ORDER_PAID",
//                ordersDB.getId(),
//                ordersDB.getNumber(),
//                ordersDB.getUserId(),
//                ordersDB.getPhone(),
//                "您的订单已支付成功，商家正在接单"
//        );
//        notificationProducer.sendNotification(notifyMsg);
//
//        log.info("支付成功处理完成：outTradeNo = {}", outTradeNo);
//    }
//

    //客户催单
    @Override
    public void reminder(Long id) {


        log.info("我是催单:{}",id);

        //1. 确认是否有这个订单
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //  补：只能催自己的单
        if (!ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException("无权操作该订单");
        }


        //2. 推送给我商家消息

        HashMap hashMap = new HashMap();
        hashMap.put("type",2); // 2 表示客户催单
        hashMap.put("orderId",id);
        hashMap.put("content","订单号:"+ordersDB.getNumber());

        log.info("我是催单的hashMap:{}",hashMap);


        String jsonString = JSON.toJSONString(hashMap);

//        webSocketServer.sendToAllClient(jsonString);

        webSocketServer.sendToRole("EMP", jsonString);

    }


}
