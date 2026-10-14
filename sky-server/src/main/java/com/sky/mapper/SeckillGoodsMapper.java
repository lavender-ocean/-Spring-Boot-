package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.entity.SeckillGoods;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SeckillGoodsMapper {

    //通过 id 来查询秒杀商品
    @Select("select * from seckill_goods where id = #{id}")
    SeckillGoods getById(Long id);

    /**
     * 原子扣减库存（DB 兜底）
     */
    //自动减一直接使用 stock = stock -1
    @Update("update seckill_goods set stock = stock - 1 where id = #{goodsId} and stock >= 1")
    int deductStock(@Param("goodsId") Long goodsId);

    /**
     * 回滚库存（MySQL 层兜底）
     */
    @Update("update seckill_goods set stock = stock + 1 where id = #{goodsId}")
    int rollbackStock(@Param("goodsId") Long goodsId);

    @Select("select * from seckill_goods where status = 1 and end_time > now() order by start_time desc")
    java.util.List<SeckillGoods> listActiveGoods();


    /**
     * 分页查询（管理端，含所有状态）
     */
    Page<SeckillGoods> pageQuery(
            @Param("name") String name,
            @Param("status") Integer status
    );

    /**
     * 新增
     */
    @Insert(
            "insert into seckill_goods (name, image, original_price, seckill_price, stock, " +
                    "start_time, end_time, status, create_time, update_time) " +
                    "values (#{name}, #{image}, #{originalPrice}, #{seckillPrice}, #{stock}, " +
                    "#{startTime}, #{endTime}, #{status}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SeckillGoods goods);

    /**
     * 更新
     */
    @Update(
            "update seckill_goods set name=#{name}, image=#{image}, original_price=#{originalPrice}, " +
                    "seckill_price=#{seckillPrice}, stock=#{stock}, start_time=#{startTime}, " +
                    "end_time=#{endTime}, status=#{status}, update_time=#{updateTime} " +
                    "where id = #{id}")
    void update(SeckillGoods goods);

    /**
     * 删除
     */
    @Delete("delete from seckill_goods where id = #{id}")
    void deleteById(@org.apache.ibatis.annotations.Param("id") Long id);

    /**
     * 修改状态
     */
    @Update(
            "update seckill_goods set status = #{status}, update_time = now() where id = #{id}")
    void updateStatus(@org.apache.ibatis.annotations.Param("id") Long id,
                      @org.apache.ibatis.annotations.Param("status") Integer status);

    /**
     * 查询处于活动中的秒杀商品ID（上架+在秒杀时间窗口内）
     */
    @Select("select id from seckill_goods " +
            "where status = 1 " +
            "and start_time <= now() " +
            "and end_time >= now()")
    List<Long> listLiveSeckillGoodsIds();

}