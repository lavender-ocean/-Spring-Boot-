package com.vueUser.controller;

import com.sky.annotation.RateLimit;
import com.sky.context.BaseContext;
import com.sky.dto.SeckillResultDTO;
import com.sky.result.Result;
import com.sky.service.SeckillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user/seckill")
@Api(tags = "C端-秒杀")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 秒杀下单
     * 限流：1 秒 3 次（按用户）
     */
    @PostMapping("/{goodsId}")
    @ApiOperation("秒杀下单")
    //给个限流
    @RateLimit(
            limit = 3,
            windowMs = 1000,
            key = "T(com.sky.context.BaseContext).getCurrentId()",
            message = "秒杀请求过于频繁，请稍后再试"
    )
    public Result<SeckillResultDTO> seckill(@PathVariable Long goodsId) {
        Long userId = BaseContext.getCurrentId();
        log.info("【秒杀】收到请求：goodsId={}, userId={}", goodsId, userId);

        SeckillResultDTO result = seckillService.seckill(goodsId, userId);

        //  注意：这里用 success 包装，但 code 可能是 0/-1，前端根据 data.code 判断
        return Result.success(result);
    }


    /**
     * 查询进行中的秒杀商品列表
     */
    @GetMapping("/list")
    @ApiOperation("秒杀商品列表")
    public Result<java.util.List<com.sky.entity.SeckillGoods>> list() {
        return Result.success(seckillService.listActiveGoods());
    }

    /**
     * 查询秒杀商品详情
     */
    @GetMapping("/detail/{goodsId}")
    @ApiOperation("秒杀商品详情")
    public Result<java.util.Map<String, Object>> detail(@PathVariable Long goodsId) {
        return Result.success(seckillService.getGoodsDetail(goodsId));
    }
}