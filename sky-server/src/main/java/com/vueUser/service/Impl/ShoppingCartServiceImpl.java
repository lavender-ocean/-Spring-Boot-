package com.vueUser.service.Impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.vueUser.mapper.ShoppingCartMapper;
import com.vueUser.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    public void add(ShoppingCartDTO shoppingCartDTO) {


        log.info("Service.add 开始，currentId = {}", BaseContext.getCurrentId());   // ← 加这行


        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        Long currentId = BaseContext.getCurrentId();

        Long tempId = BaseContext.getCurrentId();

        log.info("Service.add tempId = {}", tempId);

//        // TODO: 临时兜底，等 userJwtInterceptor 注册后删除
//
//        if (currentId == null) {
//            currentId = 1L;   // ← 临时硬编码，购物车测试专用，之后删掉
//        }

        log.info("  先来看看有没有拿到ID身份  currentId = {}", currentId);

        log.info("Service.add 中间，currentId = {}", BaseContext.getCurrentId());   // ← 加这行

        shoppingCart.setUserId(currentId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);


        log.info("查询购物车结果：{}", list);   // ← 加这行

        if (list != null && list.size() > 0) {
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cart);
        }
        else {
            //一次操作，添加进来的要么是菜品，要么是套餐
            Long dishId = shoppingCartDTO.getDishId();

            if (dishId != null) {
                //添加的是菜品
                Dish d = dishMapper.getById(dishId);
                shoppingCart.setName(d.getName());
                shoppingCart.setImage(d.getImage());
                shoppingCart.setAmount(d.getPrice());

                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());


                log.info("购物车：{}", shoppingCart);


            }else{
                //添加的是套餐
                Long setmealId = shoppingCartDTO.getSetmealId();

                Setmeal sm = setmealMapper.getById(setmealId);
                shoppingCart.setName(sm.getName());
                shoppingCart.setImage(sm.getImage());
                shoppingCart.setAmount(sm.getPrice());

                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());


                log.info("购物车：{}", shoppingCart);

            }


            log.info("开始插入{}", shoppingCart);

            shoppingCartMapper.insert(shoppingCart);


        }

    }

    /**
     *
     * 查询购物车
     * @return
     */

    @Override
    public List<ShoppingCart> showChoppingCart() {

        Long id = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = ShoppingCart.builder().userId(id).build();

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        return list;

    }


    /**
     * 清空购物车
     */
    @Override
    public void clean() {

        Long id = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(id);

    }


    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     */
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        //设置查询条件，查询当前登录用户的购物车数据
        shoppingCart.setUserId(BaseContext.getCurrentId());

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        if(list != null && list.size() > 0){
            shoppingCart = list.get(0);

            Integer number = shoppingCart.getNumber();
            if(number == 1){
                //当前商品在购物车中的份数为1，直接删除当前记录
                shoppingCartMapper.deleteById(shoppingCart.getId());
            }else {
                //当前商品在购物车中的份数不为1，修改份数即可
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            }
        }
    }
}
