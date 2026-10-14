package com.vueUser.controller;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/user/shop")
@RestController("vueUserShopController")
@Api(tags = "C端-店铺相关接口")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;

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
