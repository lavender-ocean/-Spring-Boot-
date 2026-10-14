package com.sky.controller.admin;

import com.sky.annotation.RequiresRole;
import com.sky.constant.RoleConstant;
import com.sky.entity.SeckillGoods;
import com.sky.result.Result;
import com.sky.service.SeckillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/seckill")
@Api(tags = "秒杀管理")
public class SeckillAdminController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 手动预热秒杀库存（仅超管）
     */
    @PostMapping("/warmup/{goodsId}")
    @ApiOperation("预热秒杀库存")
    //仅仅允许超级管理员来设置
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<Integer> warmUp(@PathVariable Long goodsId) {
        int stock = seckillService.warmUp(goodsId);
        return Result.success(stock);
    }

    /**
     * 分页查询秒杀商品
     */
    @GetMapping("/page")
    @ApiOperation("秒杀商品分页查询")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<com.sky.result.PageResult> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(seckillService.pageQueryGoods(name, status, page, pageSize));
    }

    /**
     * 新增秒杀商品
     */
    @PostMapping
    @ApiOperation("新增秒杀商品")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<String> add(@RequestBody SeckillGoods goods) {
        seckillService.addGoods(goods);
        return Result.success();
    }

    /**
     * 更新秒杀商品
     */
    @PutMapping
    @ApiOperation("更新秒杀商品")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<String> update(@RequestBody SeckillGoods goods) {
        seckillService.updateGoods(goods);
        return Result.success();
    }

    /**
     * 删除秒杀商品
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除秒杀商品")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<String> delete(@PathVariable Long id) {
        seckillService.deleteGoods(id);
        return Result.success();
    }

    /**
     * 修改状态（开始/结束）
     */
    @PutMapping("/status/{status}")
    @ApiOperation("修改秒杀状态")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<String> changeStatus(@PathVariable Integer status, @RequestParam Long id) {
        seckillService.changeStatus(id, status);
        return Result.success();
    }

    /**
     * 分页查询秒杀订单
     */
    @GetMapping("/order/page")
    @ApiOperation("秒杀订单分页查询")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<com.sky.result.PageResult> orderPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long goodsId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(seckillService.pageQueryOrders(userId, goodsId, status, page, pageSize));
    }
}