package com.vueUser.controller;


import com.sky.annotation.Idempotent;
import com.sky.annotation.RateLimit;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.vueUser.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-用户端相关接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @ApiOperation("用户下单")
    @PostMapping("/submit")

    //加入分布式锁
    @Idempotent(
            key = "#ordersSubmitDTO.addressBookId + ':' + T(com.sky.context.BaseContext).getCurrentId()",
            timeout = 5,
            message = "订单提交中，请勿重复操作"
    )
    //加入lua限流
    @RateLimit(
            limit = 3,
            windowMs = 1000,
            key = "T(com.sky.context.BaseContext).getCurrentId()",
            message = "操作过于频繁，请稍后再试"
    )
    public Result<OrderSubmitVO> submitOrder(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {

        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);

        return Result.success(orderSubmitVO);

    }



    /**
     * 历史订单查询
     *
     * @param page
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        PageResult pageResult = orderService.pageQuery4User(page, pageSize, status);
        return Result.success(pageResult);
    }


    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }


    /**
     * 用户取消订单
     *
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable("id") Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }






    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @RateLimit(limit = 5, windowMs = 1000,
            key = "T(com.sky.context.BaseContext).getCurrentId()",
            message = "支付操作过于频繁")
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);

        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     *
     * 客户催单
     * @param id
     * @return
     */
    @ApiOperation("客户催单")
    @GetMapping("/reminder/{id}")
    public Result reminder(@PathVariable("id") Long id) {

        orderService.reminder(id);

        return Result.success();

    }





}
