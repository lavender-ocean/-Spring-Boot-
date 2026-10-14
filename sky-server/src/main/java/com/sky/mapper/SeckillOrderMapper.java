package com.sky.mapper;

import com.sky.entity.SeckillOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SeckillOrderMapper {

    //自增 主键
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into seckill_order (user_id, goods_id, order_no, amount, status, create_time, update_time) " +
            "values (#{userId}, #{goodsId}, #{orderNo}, #{amount}, #{status}, #{createTime}, #{updateTime})")
    void insert(SeckillOrder order);


    @Select("select * from seckill_order where user_id = #{userId} and goods_id = #{goodsId}")
    SeckillOrder getByUserAndGoods(@org.apache.ibatis.annotations.Param("userId") Long userId,
                                   @org.apache.ibatis.annotations.Param("goodsId") Long goodsId);


    /**
     * 分页查询秒杀订单
     */
    com.github.pagehelper.Page<SeckillOrder> pageQuery(
            @org.apache.ibatis.annotations.Param("userId") Long userId,
            @org.apache.ibatis.annotations.Param("goodsId") Long goodsId,
            @org.apache.ibatis.annotations.Param("status") Integer status
    );

    /**
     * 统计某商品成功订单数
     */
    @Select("select count(*) from seckill_order where goods_id = #{goodsId} and status = 1")
    Integer countSuccessByGoodsId(@org.apache.ibatis.annotations.Param("goodsId") Long goodsId);

    /**
     * 查询某商品下所有已下单用户（用于 Redis key 丢失后重建「已购用户集合」）
     */
    @Select("select user_id from seckill_order where goods_id = #{goodsId}")
    java.util.List<Long> listUserIdsByGoodsId(@org.apache.ibatis.annotations.Param("goodsId") Long goodsId);




}