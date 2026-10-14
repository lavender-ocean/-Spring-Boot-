package com.sky.service;


import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;


public interface DishService {

    /**
     * 新增菜品和对应的口味数据
     * @param dishDTO
     */
    public void saveWithFlavor(DishDTO dishDTO);

    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    void deleteBatch(List<Long> ids);

    //根据id来查询菜品+口味
    DishVO getByIdWithFlavor(Long id);

    //根据id来修改菜品的基本信息和对应的口味信息
    void updatWithFlavor(DishDTO dishDTO);

    /**
     * 菜品起售停售
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);


    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    List<Dish> list(Long categoryId);

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);


    /**
     * 根据分类id查询起售菜品（带口味），带二级缓存
     */
    List<DishVO> listWithFlavorByCategory(Long categoryId);

    /**
     * 检查并扣减库存
     * @return true = 成功，false = 库存不足
     */
    boolean tryDeductStock(Long dishId, Integer count);

    /**
     * 回滚库存
     */
    void rollbackStock(Long dishId, Integer count);

}
