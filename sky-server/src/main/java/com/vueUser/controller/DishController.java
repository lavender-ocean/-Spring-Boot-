package com.vueUser.controller;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;



@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId){

        log.info("执行到了这里");

//        //构造redis中的key
//        //规则 ： dish_分类ID
//        String key = "dish" + categoryId;
////        走逻辑：
//        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
//
//        if(list != null && list.size() > 0){
//            log.info("list 是:{}", list);
//            return Result.success(list);
//        }
//
//
//        Dish dish = new Dish();
//
//        dish.setCategoryId(categoryId);
//
//        dish.setStatus(1);
//
//        list = dishService.listWithFlavor(dish);
//
//        log.info("LIst是：{}", list);
//
//        redisTemplate.opsForValue().set(key, list);

        log.info("C端查询分类 {} 的菜品", categoryId);
        // 缓存逻辑交给 Service 层
        List list = dishService.listWithFlavorByCategory(categoryId);


        return Result.success(list);
    }

}
