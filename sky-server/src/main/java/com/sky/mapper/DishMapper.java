package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface DishMapper {

    /**
     * 根据分类 id 批量更新菜品状态
     */
    @Update("update dish set status = #{status}, update_time = now() " +
            "where category_id = #{categoryId}")
    void updateStatusByCategoryId(@Param("categoryId") Long categoryId,
                                  @Param("status") Integer status);

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    @AutoFill(OperationType.INSERT)
    void insert(Dish dish);


    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);


    //根据主键来查询
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    //根据主键删除菜品数据
    @Delete("delete from dish where id = #{id}")
    void deleteById(Long id);


    /**
     * 批量删除
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);


    /**
     * 根据套餐id查询菜品
     * @param setmealId
     * @return
     */
    @Select("select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = #{setmealId}")
    List<Dish> getBySetmealId(Long setmealId);


    /**
     * 动态条件查询菜品
     * @param dish
     * @return
     */
    List<Dish> list(Dish dish);


    /**
     * 根据条件统计菜品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    //扣库存的方法
    /**
     * 原子扣减库存（防超卖核心）
     * @return 影响行数（1 = 成功，0 = 库存不足）
     */
    @Update("update dish set stock = stock - #{count} " +
            "where id = #{dishId} and stock >= #{count}")
    int deductStock(@Param("dishId") Long dishId,
                    @Param("count") Integer count);


    /**
     * 回滚库存（下单失败时）
     */
    @Update("update dish set stock = stock + #{count} where id = #{dishId}")
    int rollbackStock(@Param("dishId") Long dishId,
                      @Param("count") Integer count);

}



























