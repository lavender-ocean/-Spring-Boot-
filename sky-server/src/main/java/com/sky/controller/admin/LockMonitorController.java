package com.sky.controller.admin;


import com.sky.annotation.RequiresRole;
import com.sky.constant.RoleConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁监控接口（仅超级管理员）
 */
@RestController
@RequestMapping("/admin/lock")
@Api(tags = "分布式锁监控")
@Slf4j
public class LockMonitorController {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有锁 key（按前缀）
     */
    @GetMapping("/list")
    @ApiOperation("查询当前所有分布式锁")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<Map<String,Object>> listLocks(){

        Map<String, Object> result = new HashMap<>();


        //业务锁
        //幂等锁
        Set<String> idempotentLocks  = stringRedisTemplate.keys("lock:idempotent:*");
        //库存锁
        Set<String> stockLocks  = stringRedisTemplate.keys("lock:stock:*");
        //订单锁
        Set<String> orderLocks  = stringRedisTemplate.keys("lock:order:*");

        result.put("幂等锁", idempotentLocks);
        result.put("库存锁", stockLocks);
        result.put("订单锁", orderLocks);
        result.put("总数",
                (idempotentLocks == null ? 0 : idempotentLocks.size())
                        + (stockLocks == null ? 0 : stockLocks.size())
                        + (orderLocks == null ? 0 : orderLocks.size()));

        return Result.success(result);



    }


    /**
     * 查询指定锁的详细信息（持有者、TTL、重入次数）
     */
    @GetMapping("/detail/{lockKey}")
    @ApiOperation("查询指定锁的详细信息")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<Map<String,Object>> detailLock(@PathVariable String lockKey){

        Map<String,Object> result = new HashMap<>();

        //1.判断传来的key是否存在？
        boolean exists = stringRedisTemplate.hasKey(lockKey);
        if(!exists){
            result.put("exists",exists);
            return Result.error("该锁不存在");
        }

        if(Boolean.TRUE.equals(exists)){

            //TTL
            Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            result.put("ttl",ttl);

            //锁的持有者 + 重入次数
            Map<Object,Object> hash = stringRedisTemplate.opsForHash().entries(lockKey);
            result.put("holders",hash);


            //4.通过Redissson 来判断锁是否被占用
            RLock rLock = redissonClient.getLock(lockKey);
            result.put("rLock",rLock.isLocked());

        }

        return Result.success(result);


    }


    /**
     * 强制释放锁（危险操作）
     */
    @GetMapping("/forceUnlock/{lockKey}")
    @ApiOperation("强制释放锁（危险操作，仅调试用）")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<String> forceUnlock(@PathVariable String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isLocked()) {
            lock.forceUnlock();
            log.warn("【监控】强制释放锁：{}", lockKey);
            return Result.success("强制释放成功");
        }
        return Result.error("锁未被占用");
    }





}
















