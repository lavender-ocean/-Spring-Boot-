package com.sky.service.token;


import com.sky.exception.TokenInvalidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


//一个用户同一时间只能有一个有效 refreshToken。新登录会覆盖旧的。


/**
 * RefreshToken 管理（存 Redis）
 */
@Slf4j
@Service
public class RefreshTokenService {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * Redis key 前缀
     * 完整 key： ------------  refresh:user:{userId}  /  refresh:emp:{empId}
     */

    private static final String REFRESH_KEY_PREFIX = "refresh:";



    /**
     * 保存 refreshToken（覆盖之前的）
     * @param scope  "user" 或 "emp"
     * @param id     userId 或 empId
     * @param token  refreshToken 字符串
     * @param ttlMillis 有效期（毫秒）
     */
    public void save(String scope , String token , Long id, long ttlMillis){

        String key = REFRESH_KEY_PREFIX + scope + ":" + id;


        // key --> key  value ---> token
        redisTemplate.opsForValue().set(key,token,ttlMillis, TimeUnit.MILLISECONDS);

        log.info("保存 refreshToken：{}", key);

    }

    /**
     * 校验 refreshToken 是否有效
     * @return true = 有效（Redis 里存的就是它）
     */
    public boolean isValid(String scope , Long id,String token){

        //先找到 key
        String key = REFRESH_KEY_PREFIX + scope + ":" + id;
        // 再 根据 key 去找 value
        String scoped = (String) redisTemplate.opsForValue().get(key);

        boolean valid;
        if(scoped == null){
            valid = false;
        }
        else{
            valid = scoped.equals(token);
        }

        return valid;

    }

    /**
     * 删除 refreshToken（登出 / 强制下线）
     */
    public void delete(String scope, Long id ){

        String key = REFRESH_KEY_PREFIX + scope + ":" + id;
        Boolean existed = redisTemplate.delete(key);
        if(Boolean.FALSE.equals(existed)){
            log.warn("删除refreshToken，key不存在：{}",key);
        }else{
            log.info("删除 refreshToken：{}", key);
        }

    }



}




























