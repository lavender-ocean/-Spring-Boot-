package com.sky.mapper;

import com.sky.entity.SeckillReconcileLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillReconcileLogMapper {

    /**
     * 批量插入对账日志（提高性能）
     */
    //使用logs，防止参数名字的问题
    void insertBatch(@Param("logs") List<SeckillReconcileLog> logs);


    /**
     * 清理 30 天前的日志
     */
    void cleanOldLogs();
}