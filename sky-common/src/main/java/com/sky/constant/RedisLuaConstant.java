package com.sky.constant;

/**
 * Redis Lua 脚本常量
 */
public class RedisLuaConstant {

    /** Lua 脚本在 classpath 下的路径前缀 */
    public static final String LUA_PATH_PREFIX = "lua/";

    /** 限流脚本 */
    public static final String RATE_LIMIT = LUA_PATH_PREFIX + "rate_limit.lua";

    /** 菜品排行榜脚本 */
    public static final String DISH_RANK = LUA_PATH_PREFIX + "dish_rank.lua";


    /** 秒杀脚本 */
    public static final String SECKILL = LUA_PATH_PREFIX + "seckill.lua";
}