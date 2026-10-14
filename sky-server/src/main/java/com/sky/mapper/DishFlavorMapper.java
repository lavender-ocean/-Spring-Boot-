package com.sky.mapper;


import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper

public interface DishFlavorMapper {


    /**
     * 批量插入数据
     * @param flavors
     */
    void insertBatch(List<DishFlavor> flavors);

    //根据菜品id来删口味数据
    @Delete("delete from dish_flavor where dish_id = #{id}")
    void deleteByDishId(Long id);

    void deleteByDishIds(List<Long> ids);

    @Select("select * from dish_flavor where dish_id = #{id}")
    List<DishFlavor> getByDishId(Long id);
}
