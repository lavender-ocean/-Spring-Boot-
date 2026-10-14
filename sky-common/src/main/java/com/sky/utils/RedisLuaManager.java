package com.sky.utils;


import com.sky.constant.RedisLuaConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Lua 脚本管理器
 *
 * 核心设计：
 *   1. 应用启动时 SCRIPT LOAD，缓存 SHA1
 *   2. 运行时用 EVALSHA 调用，只传 SHA1（省带宽）
 *   3. NOSCRIPT 异常时自动重载（防 Redis 重启）
 *   4. 脚本内容内存缓存（避免每次读文件）
 */

@Component
@Slf4j
public class RedisLuaManager {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //脚本路径 -> SHA1
    private final Map<String,String> sha1Cache = new ConcurrentHashMap<>();

    //脚本路径 -> 脚本内容  内存缓存
    private final Map<String,String> scriptCache = new ConcurrentHashMap<>();

    /**
     * 应用启动时预加载所有脚本
     * 用 @PostConstruct 而不是 ApplicationRunner，因为要在 Bean 完全初始化前加载
     */
    @PostConstruct
    public void preLoadScript() {
        try {
            // 所有脚本都在这里预加载
            //限流脚本
            loadScript(RedisLuaConstant.RATE_LIMIT);
            //秒杀脚本
            loadScript(RedisLuaConstant.SECKILL);
            log.info(" Redis Lua 脚本预加载完成，共 {} 个", sha1Cache.size());

        } catch (Exception e) {
            log.error(" Redis Lua 脚本预加载失败", e);
            //  启动失败不要中断应用，运行时再次尝试加载
        }
    }


    //从path 路径 加载 lua脚本
    private void loadScript(String path){

        String script = scriptCache.computeIfAbsent(path, this::readScriptFromClasspath);
        String sha1 = stringRedisTemplate.execute(
                (RedisCallback<String> ) connection ->
            connection.scriptLoad(script.getBytes(StandardCharsets.UTF_8))
        );

        sha1Cache.put(path, sha1);
        log.info("Lua 脚本加载成功：{} → SHA1 = {}", path, sha1);


    }

    //从classpath处 获取 lua脚本
    private String readScriptFromClasspath(String path) {
        ClassPathResource classPathResource = new ClassPathResource(path);
        try {
            return StreamUtils.copyToString(classPathResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("加载 Lua 脚本失败：" + path, e);

        }
    }


    /**
     * 执行 Lua 脚本（通过 EVALSHA）
     *
     * @param scriptPath 脚本路径（如 RedisLuaConstant.RATE_LIMIT）
     * @param keys       Redis keys
     * @param args       ARGV 参数
     * @return 脚本返回值（Long）
     */

    public long executeLua(String scriptPath, List<String> keys, Object ... args) {

        String sha1 = sha1Cache.get(scriptPath);

        // 首次调用（理论上不会发生，因为 @PostConstruct 已加载）
        if (sha1 == null) {
            loadScript(scriptPath);
            sha1 = sha1Cache.get(scriptPath);
        }

        try{
            return doExecuteLua(sha1, keys, args);
        }catch (RedisSystemException e){
            //NOSCRIPT：Redis 重启或脚本被清空，自动重载

            if(isNoScriptException(e)){
                log.warn(" 检测到 NOSCRIPT 异常，重新加载脚本：{}", scriptPath);
                loadScript(scriptPath);
                String newSha1 = sha1Cache.get(scriptPath);
                return doExecuteLua(newSha1, keys, args);
            }
            throw e;

        }

    }


    /**
     * 实际执行 EVALSHA
     */
    private long doExecuteLua(String sha1 , List<String> keys ,Object ...args){

        return stringRedisTemplate.execute(
                (RedisCallback<Long>) connection -> {
                    // 序列化 keys
                    byte[][] keyBytes = keys.stream()
                            .map(k -> k.getBytes(StandardCharsets.UTF_8))
                            .toArray(byte[][]::new);

                    // 序列化 args
                    byte[][] argBytes = Arrays.stream(args)
                            .map(a -> String.valueOf(a).getBytes(StandardCharsets.UTF_8))
                            .toArray(byte[][]::new);

                    // 合并 keys 和 args
                    byte[][] keysAndArgs = new byte[keyBytes.length + argBytes.length][];
                    System.arraycopy(keyBytes, 0, keysAndArgs, 0, keyBytes.length);
                    System.arraycopy(argBytes, 0, keysAndArgs, keyBytes.length, argBytes.length);

                    return connection.evalSha(
                            sha1.getBytes(StandardCharsets.UTF_8),
                            ReturnType.INTEGER,
                            keys.size(),
                            keysAndArgs
                    );
                }
        );
    }

    /**
     * 判断是否是 NOSCRIPT 异常
     */
    private boolean  isNoScriptException(RedisSystemException e){
        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        return msg != null && msg.contains("NOSCRIPT");
    }



}
