
package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "菜品管理")
@Slf4j
@RequestMapping("/admin/dish")
@RestController
public class DishController {

    @Autowired
    private DishService dishService;

    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);
        // 清缓存：交给 DishServiceImpl 处理（用 TwoLevelCacheUtil）
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售停售")
    public Result<String> startOrStop(@PathVariable Integer status, Long id) {
        dishService.startOrStop(status, id);
        // 清缓存：交给 DishServiceImpl 处理
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> getDishPage(@ModelAttribute DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @ApiOperation("删除菜品")
    public Result deleteDish(@RequestParam List<Long> ids) {
        log.info("菜品批量删除:{}", ids);
        dishService.deleteBatch(ids);
        // 清缓存：交给 DishServiceImpl 处理
        return Result.success();
    }

    @ApiOperation("根据id来查询菜品")
    @GetMapping("/{id}")
    public Result<DishVO> getDishById(@PathVariable Long id) {
        log.info("根据id来查询菜品:{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    @ApiOperation("修改菜品")
    @PutMapping
    public Result updateDish(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品{}", dishDTO);
        dishService.updatWithFlavor(dishDTO);
        // 清缓存：交给 DishServiceImpl 处理
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("执行到了这里");
        // 缓存逻辑：交给 DishServiceImpl 处理
        return Result.success(dishService.listWithFlavorByCategory(categoryId));
    }
}





































//package com.sky.controller.admin;
//
//
//import com.sky.dto.DishDTO;
//import com.sky.dto.DishPageQueryDTO;
//import com.sky.entity.Dish;
//import com.sky.result.PageResult;
//import com.sky.result.Result;
//import com.sky.service.DishService;
//import com.sky.vo.DishVO;
//import io.swagger.annotations.Api;
//import io.swagger.annotations.ApiOperation;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Set;
//
//
//@Api(tags = "菜品管理")
//@Slf4j
//@RequestMapping("/admin/dish")
//@RestController
//public class DishController {
//
//    @Autowired
//    private DishService dishService;
//
//    @Autowired
//    private RedisTemplate redisTemplate;
//
//
//    @PostMapping
//    @ApiOperation(("新增菜品"))
//    public Result save(@RequestBody DishDTO dishDTO){
//
//        log.info("新增菜品：{}",dishDTO);
//
//        dishService.saveWithFlavor(dishDTO);
//
//
//        //清理缓存数据
//        String key = "dish_" + dishDTO.getCategoryId();
//
//        redisTemplate.delete(key);
//
//        return Result.success();
//
//    }
//
//    /**
//     * 菜品起售停售
//     * @param status
//     * @param id
//     * @return
//     */
//    @PostMapping("/status/{status}")
//    @ApiOperation("菜品起售停售")
//    public Result<String> startOrStop(@PathVariable Integer status, Long id){
//        dishService.startOrStop(status,id);
//
//        //所以菜品缓存数据都清理掉
//        Set keys = redisTemplate.keys("dish*");
//
//        redisTemplate.delete(keys);
//
//        return Result.success();
//    }
//
//    @GetMapping("/page")
//    @ApiOperation("菜品分页查询")
//
//    public Result<PageResult> getDishPage(@ModelAttribute DishPageQueryDTO dishPageQueryDTO    ){
//
//        log.info("菜品分页查询{}",dishPageQueryDTO);
//
//        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
//
//        return Result.success(pageResult);
//    }
//
//
//    @DeleteMapping
//    @ApiOperation("删除菜品")
//    public Result deleteDish(@RequestParam List<Long> ids){
//
//        log.info("菜品批量删除:{}",ids);
//
//        dishService.deleteBatch(ids);
//
//        //所以菜品缓存数据都清理掉
//        Set keys = redisTemplate.keys("dish*");
//
//        redisTemplate.delete(keys);
//
//        return Result.success();
//    }
//
//
//    @ApiOperation("根据id来查询菜品")
//    @GetMapping("/{id}")
//    public Result<DishVO> getDishById(@PathVariable Long id){
//
//
//        log.info("根据id来查询菜品:{}",id);
//        DishVO dishVO = dishService.getByIdWithFlavor(id);
//        return Result.success(dishVO);
//
//
//    }
//
//    @ApiOperation("修改菜品")
//    @PutMapping
//    public Result updateDish(@RequestBody DishDTO dishDTO){
//
//        log.info("修改菜品{}",dishDTO);
//
//        dishService.updatWithFlavor(dishDTO);
//
//        //所以菜品缓存数据都清理掉
//        Set keys = redisTemplate.keys("dish*");
//
//        redisTemplate.delete(keys);
//
//        return Result.success();
//    }
//
//
//    /**
//     * 根据分类id查询菜品
//     * @param categoryId
//     * @return
//     */
//    @GetMapping("/list")
//    @ApiOperation("根据分类id查询菜品")
//    public Result<List<DishVO>> list(Long categoryId){
//
//        log.info("执行到了这里");
//
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
//
//        return Result.success(list);
//    }
//
//
//    /**
//     * 统一清理缓存数据
//     * @param pattern
//     */
//    private void cleanCache(String pattern){
//        Set keys = redisTemplate.keys(pattern);
//        redisTemplate.delete(keys);
//    }
//
//
//
//}
