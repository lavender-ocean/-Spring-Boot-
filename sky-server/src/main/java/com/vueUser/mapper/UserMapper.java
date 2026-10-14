package com.vueUser.mapper;


import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 按照手机号查找 用户
     * @param phone
     * @return
     */
    @Select("select * from user where phone = #{phone}")
    User getByPhone(String phone);


    /**
     * 根据ID查找用户 -- 返回User对象
     * @param id
     * @return
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);


    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);


    /**
     * 插入数据
     * @param user
     */
    void insert(User user);


    /**
     * 获取时间段内的 user 数量
     * @param map
     * @return
     */
    Integer getUserCount(Map map);


    /**
     * 根据动态条件统计用户数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);



}
