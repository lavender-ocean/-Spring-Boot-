package com.sky.controller.admin;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RequestMapping("/admin/shop")
@RestController("adminShopController")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    /**
     *
     * 设置店铺的营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺的营业状态")
    public Result setStatus(@PathVariable String status){
        /**
         * 1 -- 营业
         * 0 -- 打烊
         */
        log.info("设置店铺的营业状态{}",status);

//        redisTemplate.opsForValue().set("SHOP_STATUS",status);

        // 加 TTL  ----24小时 足够
        redisTemplate.opsForValue().set("SHOP_STATUS", status, 24, TimeUnit.HOURS);


        return Result.success();
    }

    /**
     *
     * 查询店铺的营业状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("查询店铺的营业状态")
    public Result<String> getStatus(){

        //当初放进去是什么类型，现在就可以强制转换成什么类型

        String status = (String) redisTemplate.opsForValue().get("SHOP_STATUS");

        log.info("查询店铺的营业状态{}",status);

        return Result.success(status);
    }




}
