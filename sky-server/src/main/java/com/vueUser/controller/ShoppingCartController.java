package com.vueUser.controller;


import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.vueUser.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/shoppingCart")
@Api(tags = "C端-购物车")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @ApiOperation("添加购物车")
    @PostMapping("/add")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {

        log.info("添加购物车");
        log.info(shoppingCartDTO.toString());
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();

    }


    @GetMapping("/list")
    @ApiOperation("查看购物车")
    public Result<List<ShoppingCart>> list() {

        log.info("查看购物车");

        List<ShoppingCart> shoppingCarts = shoppingCartService.showChoppingCart();

        return Result.success(shoppingCarts);
    }

    /**
     *
     * 清空购物车
     * @return
     */

    @DeleteMapping("/clean")
    @ApiOperation("清空购物车")
    public Result clean() {

        log.info("清空购物车");

        shoppingCartService.clean();

        return Result.success();
    }

    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/sub")
    @ApiOperation("删除购物车中一个商品")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("删除购物车中一个商品，商品：{}", shoppingCartDTO);
        shoppingCartService.subShoppingCart(shoppingCartDTO);
        return Result.success();
    }




}
