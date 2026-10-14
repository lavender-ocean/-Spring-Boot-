package com.vueUser.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;


public interface ShoppingCartService {

    //添加购物车车
    void add(ShoppingCartDTO shoppingCartDTO);


    //查看购物车
    List<ShoppingCart> showChoppingCart();

    //清空购物车
    void clean();

    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);



}
