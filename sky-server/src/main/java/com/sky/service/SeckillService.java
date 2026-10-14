package com.sky.service;

import com.sky.dto.SeckillResultDTO;

public interface SeckillService {

    /**
     * 秒杀（Redis 层预减 + 投递 MQ）
     */
    //传入 商品ID 和 用户ID 来做秒杀
    SeckillResultDTO seckill(Long goodsId, Long userId);

    /**
     * 预热库存到 Redis（启动时或手动触发）
     */
    //预热库 存到 Redis
    int warmUp(Long goodsId);

    /**
     * 查询进行中的秒杀商品列表
     */
    java.util.List<com.sky.entity.SeckillGoods> listActiveGoods();

    /**
     * 查询秒杀商品详情（含实时库存）
     */
    java.util.Map<String, Object> getGoodsDetail(Long goodsId);


    // ============ 管理端 ============

    /**
     * 分页查询秒杀商品
     */
    com.sky.result.PageResult pageQueryGoods(String name, Integer status, int page, int pageSize);

    /**
     * 新增秒杀商品
     */
    void addGoods(com.sky.entity.SeckillGoods goods);

    /**
     * 更新秒杀商品
     */
    void updateGoods(com.sky.entity.SeckillGoods goods);

    /**
     * 删除秒杀商品
     */
    void deleteGoods(Long id);

    /**
     * 修改状态（0未开始 1进行中 2已结束）
     */
    void changeStatus(Long id, Integer status);

    /**
     * 分页查询秒杀订单
     */
    com.sky.result.PageResult pageQueryOrders(Long userId
            , Long goodsId
            , Integer status
            , int page
            , int pageSize);
}