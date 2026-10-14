package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.entity.MqFailedMsg;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MqFailedMsgMapper {


    /**  重投后再次失败：把已存在（RETRIED）的记录放回 PENDING 并累计次数 */
    @Update("update mq_failed_msg set status = 'PENDING', retry_count = retry_count + 1, " +
            "update_time = now() where msg_id = #{msgId} and status <> 'GAVE_UP'")
    int requeueIfExists(@Param("msgId") String msgId);

    /**  发送侧 confirm 成功标记 */
    @Update("update mq_failed_msg set status = 'SUCCESS', update_time = now() where msg_id = #{msgId}")
    int markSuccess(@Param("msgId") String msgId);

    @Insert("insert ignore into mq_failed_msg " +
            "(biz_type, msg_id, payload, fail_reason, retry_count, status, create_time) " +
            "values (#{bizType}, #{msgId}, #{payload}, #{failReason}, #{retryCount}, #{status}, #{createTime})")
    int insertIgnore(MqFailedMsg msg);

    @Select("select * from mq_failed_msg where biz_type = #{bizType} and status = 'PENDING' " +
            "order by create_time asc limit #{limit}")
    List<MqFailedMsg> listPending(@Param("bizType") String bizType, @Param("limit") int limit);

    @Update("update mq_failed_msg set status = #{status}, retry_count = retry_count + 1, " +
            "update_time = now() where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("update mq_failed_msg set retry_count = retry_count + 1, update_time = now() where id = #{id}")
    int incrementRetry(@Param("id") Long id);


    /**
     * 管理端分页查询
     */
    Page<MqFailedMsg> pageQuery(@Param("bizType") String bizType,
                                @Param("status") String status,
                                @Param("msgId") String msgId);

    /**
     * 手动放弃
     */
    @Update("update mq_failed_msg set status = 'GAVE_UP', update_time = now() where id = #{id}")
    int giveUp(@Param("id") Long id);
}