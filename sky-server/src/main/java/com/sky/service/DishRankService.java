package com.sky.service;

import com.sky.vo.DishRankVO;

import java.util.List;

public interface DishRankService {

    /**
     * 累加菜品销量（订单完成时调用）
     */
    void incrementSales(Long dishId, int count);

    /**
     * 获取 Top N 排行榜
     */
    List<DishRankVO> getTopN(int n);

    /**
     * 查询某菜品排名
     */
    DishRankVO getDishRank(Long dishId);

    /**
     * 从数据库初始化历史数据
     */
    int initFromDB();
}