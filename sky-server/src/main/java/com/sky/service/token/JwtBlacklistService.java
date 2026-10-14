package com.sky.service.token;


import com.sky.constant.RedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 *
 * JWT 黑名单 服务
 */
@Slf4j
@Service
public class JwtBlacklistService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     *   把 token 加入 黑名单
     * @param token  要拉黑的 token
     * @param expireDate token 的过期时间（从 payload 里解析出来的）
     */

    public void addBlackList(String token , Date expireDate){

        //先判断 token 是否 有值
        if(token == null || token.isEmpty()){
            return;
        }

        //再来判断 Date 过期时间 这个 参数 是否 有值
        if(expireDate == null){
            log.warn("token 没有过期时间，无法加入黑名单");
            return;
        }

        //开始计算 token 剩余 有效期
        Long ttl = expireDate.getTime() - System.currentTimeMillis();

        if(ttl < 0){
            log.info("token 已过期，无需加入黑名单");
            return;
        }

        String key = RedisKeyConstant.blackListToken(token);
        //存入 Reids
        stringRedisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.MILLISECONDS);
        log.info("token 已加入黑名单，TTL = {} 秒", ttl / 1000);


    }


    /**
     * 判断 token 是否在 黑名单
     */

    public boolean isBlackListed(String token){
        if(token == null || token.isEmpty()){
            return false;
        }

        String key = RedisKeyConstant.blackListToken(token);

        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));

    }



}
