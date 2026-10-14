package com.vueUser.mapper;


import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.*;
import org.springframework.core.annotation.Order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);


    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量
     * @param status
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);


    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);



    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);


    /**
     * 根据订单状态和下单时间查询订单
     * @param status
     * @param orderTime
     * @return
     */

    @Select("select * from orders where status = #{status} and order_time < (#{orderTime})")
    List<Orders> getByStatusAndOrderTime(Integer status, LocalDateTime orderTime);


    /**
     * 根据map来统计营业额
     * @param map
     * @return
     */
    Double sumByMap(Map map);


    /**
     * 根据动态条件统计订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);


    /**
     * 统计指定时间区间内的 销量排名前10
     * @param beginDate
     * @param endDate
     * @return
     */
    List<GoodsSalesDTO> getGoodsSalesTop10(LocalDateTime beginDate, LocalDateTime endDate);





    /**
     * ★ 状态机 CAS：仅当订单仍处于 expectStatus 时才取消
     * @return 影响行数（1=抢到状态变更权，0=已被其他链路改过）
     */
    @Update("update orders set status = #{toStatus}, cancel_reason = #{reason}, cancel_time = #{cancelTime} " +
            "where id = #{id} and status = #{expectStatus}")
    int cancelWithCas(@Param("id") Long id,
                      @Param("expectStatus") Integer expectStatus,
                      @Param("toStatus") Integer toStatus,
                      @Param("reason") String reason,
                      @Param("cancelTime") LocalDateTime cancelTime);

    /**
     * ★ 状态机 CAS：仅当订单是「待付款 + 未支付」时才置为已支付
     */
    @Update("update orders set status = #{toStatus}, pay_status = #{payStatus}, checkout_time = #{checkoutTime} " +
            "where id = #{id} and status = #{expectStatus} and pay_status = #{expectPayStatus}")
    int payWithCas(@Param("id") Long id,
                   @Param("expectStatus") Integer expectStatus,
                   @Param("toStatus") Integer toStatus,
                   @Param("expectPayStatus") Integer expectPayStatus,
                   @Param("payStatus") Integer payStatus,
                   @Param("checkoutTime") LocalDateTime checkoutTime);

    /** ★ 仅更新支付状态（退款标记用） */
    @Update("update orders set pay_status = #{payStatus} where id = #{id}")
    int updatePayStatus(@Param("id") Long id, @Param("payStatus") Integer payStatus);





}
