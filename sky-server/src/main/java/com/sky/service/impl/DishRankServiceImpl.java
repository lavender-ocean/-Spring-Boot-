package com.sky.service.impl;

import com.sky.constant.RedisKeyConstant;
import com.sky.constant.RedisLuaConstant;
import com.sky.mapper.DishMapper;
import com.sky.service.DishRankService;
import com.sky.utils.RedisLuaManager;
import com.sky.vo.DishRankVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 菜品销量排行榜
 */
@Slf4j
@Service
public class DishRankServiceImpl implements DishRankService {

    @Override
    public void incrementSales(Long dishId, int count) {

    }

    @Override
    public List<DishRankVO> getTopN(int n) {
        return new ArrayList<>();
    }

    @Override
    public DishRankVO getDishRank(Long dishId) {
        return null;
    }

    @Override
    public int initFromDB() {
        return 0;
    }
}