package com.sky.constant;


/**
 * RedisKey ----- 命名规范
 */
public class RedisKeyConstant {


    /** 秒杀最近一次投递 MQ 的时间：seckill:last_dispatch:{goodsId} */
    public static final String SECKILL_LAST_DISPATCH_PREFIX = "seckill:last_dispatch:";

    public static String seckillLastDispatch(Long goodsId) {
        return SECKILL_LAST_DISPATCH_PREFIX + goodsId;
    }

    /**
     * token 黑名单
     * 完整 key   ------------   black:token:{token字符串}
     */
    public final static String BLACKLIST_TOKEN_PREFIX = "black:token:";

    /**
     * 构建 黑名单 Key
     */

    public static String blackListToken(String token) {

        return BLACKLIST_TOKEN_PREFIX + token;
    }


    // ============ 限流 ============
    /** 限流 key 前缀：rate:limit:{bizKey} */
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";

    public static String rateLimit(String bizKey) {

        return RATE_LIMIT_PREFIX + bizKey;
    }

    // ============ 菜品排行榜 ============
    /** 菜品销量榜 ZSet：rank:dish:sales */
    public static final String DISH_RANK_KEY = "rank:dish:sales";

    /** 首次达成时间 Hash：rank:dish:first_time */
    public static final String DISH_RANK_FIRST_TIME_KEY = "rank:dish:first_time";


    // ============ 秒杀 ============
    /** 秒杀库存：seckill:stock:{goodsId} */
    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";

    /** 秒杀已购用户 Set：seckill:users:{goodsId} */
    public static final String SECKILL_USERS_PREFIX = "seckill:users:";

    public static String seckillStock(Long goodsId) {

        return SECKILL_STOCK_PREFIX + goodsId;
    }

    public static String seckillUsers(Long goodsId) {

        return SECKILL_USERS_PREFIX + goodsId;
    }

}
