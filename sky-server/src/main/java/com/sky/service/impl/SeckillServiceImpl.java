package com.sky.service.impl;

import com.sky.constant.RedisKeyConstant;
import com.sky.constant.RedisLuaConstant;
import com.sky.dto.SeckillResultDTO;
import com.sky.entity.SeckillGoods;
import com.sky.entity.SeckillOrder;
import com.sky.mapper.SeckillGoodsMapper;
import com.sky.mapper.SeckillOrderMapper;
import com.sky.mq.message.SeckillMsg;
import com.sky.mq.producer.SeckillProducer;
import com.sky.service.SeckillService;
import com.sky.utils.RedisLuaManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private RedisLuaManager redisLuaManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    //秒杀MQ生产者
    @Autowired
    private SeckillProducer seckillProducer;

    //开始秒杀
    @Override
    public SeckillResultDTO seckill(Long goodsId, Long userId) {

        //1. 先检查活动时间 不符合的直接禁止

        SeckillGoods goods = seckillGoodsMapper.getById(goodsId);
        if(goods == null){
            return SeckillResultDTO.error("商品不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if(
                //判断时间是否符合这个活动时间
                goods.getStartTime() == null || goods.getEndTime() == null
                || now.isBefore(goods.getStartTime())
                || now.isAfter(goods.getEndTime())
                //这个是判断是否 处于 1 -- 进行中
                || goods.getStatus() != 1
        ){
            //秒杀活动未开始或已结束
            return SeckillResultDTO.notStarted();
        }


        // 2. 生成订单号
        String orderNo = "SK" +
                System.currentTimeMillis() +
                UUID.randomUUID().toString().replace("-", "").substring(0, 6);


        Long result;
        try {
            result = redisLuaManager.executeLua(
                    RedisLuaConstant.SECKILL,
                    Arrays.asList(
                            RedisKeyConstant.seckillStock(goodsId),
                            RedisKeyConstant.seckillUsers(goodsId)   // ← 原来是 userId
                    ),
                    userId                                        // ARGV[1] 才是 userId
            );
        } catch (Exception e) {
            log.error("【秒杀】Lua 执行异常：goodsId={}, userId={}", goodsId, userId, e);
            return SeckillResultDTO.error("系统繁忙，请稍后重试");
        }

        switch (result.intValue()) {
            case 1:
                log.info("【秒杀】抢购成功：goodsId={}, userId={}, orderNo={}", goodsId, userId, orderNo);

                SeckillMsg msg = SeckillMsg.of(userId, goodsId, orderNo, goods.getSeckillPrice());
                try {
                    seckillProducer.sendSeckillMsg(msg);

                    // ★ 打点：记录最近投递时间，供对账任务判断「是否有消息在途」
                    stringRedisTemplate.opsForValue().set(
                            RedisKeyConstant.seckillLastDispatch(goodsId),
                            String.valueOf(System.currentTimeMillis()),
                            10, TimeUnit.MINUTES);

                } catch (Exception e) {
                    // MQ 投递失败 → 回滚 Redis（现在两个 key 语义一致，回滚真正生效）
                    log.error("【秒杀】MQ 投递失败，回滚 Redis：orderNo={}", orderNo, e);
                    stringRedisTemplate.opsForValue().increment(RedisKeyConstant.seckillStock(goodsId));
                    stringRedisTemplate.opsForSet().remove(
                            RedisKeyConstant.seckillUsers(goodsId), String.valueOf(userId));
                    return SeckillResultDTO.error("系统繁忙，请稍后再试");
                }
                return SeckillResultDTO.queuing(orderNo);
            case 0:
                return SeckillResultDTO.soldOut();
            case -1:
                return SeckillResultDTO.duplicate();
            default:
                return SeckillResultDTO.error("未知状态");
        }

//        //3. 带着生成的订单号 来去执行lua脚本 （原子判重+扣减）
//        Long result;
//        try{
//           result = redisLuaManager.executeLua(
//                   RedisLuaConstant.SECKILL,
//                   Arrays.asList(
//                           //传入库存量的key ---- 方便lua去拿着这个key去找value的值 --- 也就是剩余的是多少
//                           RedisKeyConstant.seckillStock(goodsId),
//                           //传入 user的key ---- lua拿着这个key去找 user的key和value --- 去判断幂等
//                           RedisKeyConstant.seckillUsers(userId)
//                   ),
//                   userId
//           );
//        }catch (Exception e){
//            log.error("【秒杀】Lua 执行异常：goodsId={}, userId={}", goodsId, userId, e);
//            return SeckillResultDTO.error("系统繁忙，请稍后重试");
//        }
//
//
//
//        // 4. 处理结果
//        //这一套下来 Result 还是 null 那就是 出问题了，就不能再执行了
//        if (result == null) {
//            return SeckillResultDTO.error("系统异常");
//        }
//
//        switch (result.intValue()) {
//            case 1:
//                log.info(" 【秒杀】抢购成功：goodsId={}, userId={}, orderNo={}", goodsId, userId, orderNo);
//
//                //开始投递MQ
//                SeckillMsg msg = SeckillMsg.of(userId, goodsId, orderNo, goods.getSeckillPrice());
//                try {
//                    seckillProducer.sendSeckillMsg(msg);
//                } catch (Exception e) {
//                    // MQ 投递失败 → 回滚 Redis（保证一致性）
//                    log.error("【秒杀】MQ 投递失败，回滚 Redis：orderNo={}", orderNo, e);
//                    stringRedisTemplate.opsForValue().increment(RedisKeyConstant.seckillStock(goodsId));
//                    stringRedisTemplate.opsForSet().remove(RedisKeyConstant.seckillUsers(goodsId),
//                            String.valueOf(userId));
//                    return SeckillResultDTO.error("系统繁忙，请稍后再试");
//                }
//
//
//                return SeckillResultDTO.queuing(orderNo);
//            case 0:
//                log.info("【秒杀】已售罄：goodsId={}", goodsId);
//                return SeckillResultDTO.soldOut();
//            case -1:
//                log.info("【秒杀】重复购买：goodsId={}, userId={}", goodsId, userId);
//                return SeckillResultDTO.duplicate();
//            default:
//                return SeckillResultDTO.error("未知状态");
//        }
    }

    /**
     * 预热库存到 Redis
     * 幂等：先删旧 key，再写新的
     */

    /**
     * 预热库存：只在 key 缺失时补写，绝不覆盖正在进行的活动
     */
    @Override
    public int warmUp(Long goodsId) {
        SeckillGoods goods = seckillGoodsMapper.getById(goodsId);
        if (goods == null) {
            log.warn("【秒杀预热】商品不存在：goodsId={}", goodsId);
            return 0;
        }
        String stockKey = RedisKeyConstant.seckillStock(goodsId);
        long ttl = 7 * 24 * 3600L;

        // ★ setIfAbsent：已在跑的活动不能被启动预热覆盖，否则会把卖掉的库存"补回来" → 超卖
        Boolean absent = stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey, String.valueOf(goods.getStock()), ttl, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(absent)) {
            log.info("【秒杀预热】写入库存：goodsId={}, stock={}, TTL={}s", goodsId, goods.getStock(), ttl);
        } else {
            log.info("【秒杀预热】库存已存在，跳过（不覆盖进行中的活动）：goodsId={}", goodsId);
        }
        return goods.getStock();
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        seckillGoodsMapper.updateStatus(id, status);

        String stockKey = RedisKeyConstant.seckillStock(id);
        String userKey = RedisKeyConstant.seckillUsers(id);

        if (status != null && status == 1) {
            // 活动开始：清掉上一轮的已购集合，再写入新库存
            stringRedisTemplate.delete(userKey);
            SeckillGoods goods = seckillGoodsMapper.getById(id);
            stringRedisTemplate.opsForValue().set(stockKey,
                    String.valueOf(goods.getStock()), 7, TimeUnit.DAYS);
            log.info("【秒杀管理】活动开始，已重置库存与已购集合：id={}", id);
        } else {
            // 活动结束/下架：清理 key
            stringRedisTemplate.delete(stockKey);
            stringRedisTemplate.delete(userKey);
            log.info("【秒杀管理】活动结束/下架，已清理 Redis：id={}", id);
        }
    }
//    @Override
//    public int warmUp(Long goodsId) {
//
//        SeckillGoods goods = seckillGoodsMapper.getById(goodsId);
//        if(goods == null){
//            log.warn("【秒杀预热】商品不存在：goodsId={}", goodsId);
//            return 0;
//        }
//
//        String stockKey = RedisKeyConstant.seckillStock(goodsId);
//        String userKey = RedisKeyConstant.seckillUsers(goodsId);
//
//        //1.先清空旧数据----会清理掉已购客户？？？？
////        stringRedisTemplate.delete(stockKey);
////        stringRedisTemplate.delete(userKey);
//
//        //2.写入库存
//        stringRedisTemplate.opsForValue().set(stockKey,String.valueOf(goods.getStock()));
//
//        //3.设置过期时间  --- 活动结束后7天自动清理
//        long ttl = 7 * 24 * 3600L;
//        stringRedisTemplate.expire(stockKey, ttl, TimeUnit.SECONDS);
//        stringRedisTemplate.expire(userKey, ttl, TimeUnit.SECONDS);
//
//
//
//        log.info("【秒杀预热】goodsId={}, stock={}, TTL={}s", goodsId, goods.getStock(), ttl);
//        return goods.getStock();
//    }


    @Override
    public java.util.List<SeckillGoods> listActiveGoods() {
        return seckillGoodsMapper.listActiveGoods();
    }

    @Override
    public java.util.Map<String, Object> getGoodsDetail(Long goodsId) {
        SeckillGoods goods = seckillGoodsMapper.getById(goodsId);
        if (goods == null) {
            return null;
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("goods", goods);

        // 从 Redis 拿实时库存
        String stockKey = RedisKeyConstant.seckillStock(goodsId);
        String redisStock = stringRedisTemplate.opsForValue().get(stockKey);
        result.put("realTimeStock", redisStock == null ? 0 : Integer.parseInt(redisStock));

        return result;
    }





    @Override
    public com.sky.result.PageResult pageQueryGoods(String name, Integer status, int page, int pageSize) {
        com.github.pagehelper.PageHelper.startPage(page, pageSize);
        com.github.pagehelper.Page<SeckillGoods> p = seckillGoodsMapper.pageQuery(name, status);
        return new com.sky.result.PageResult(p.getTotal(), p.getResult());
    }

    @Override
    public void addGoods(SeckillGoods goods) {
        goods.setCreateTime(java.time.LocalDateTime.now());
        goods.setUpdateTime(java.time.LocalDateTime.now());
        if (goods.getStatus() == null) {
            goods.setStatus(0);
        }
        seckillGoodsMapper.insert(goods);
        log.info("【秒杀管理】新增商品：id={}, name={}", goods.getId(), goods.getName());
    }

    @Override
    public void updateGoods(SeckillGoods goods) {
        goods.setUpdateTime(java.time.LocalDateTime.now());
        seckillGoodsMapper.update(goods);
        log.info("【秒杀管理】更新商品：id={}", goods.getId());
    }

    @Override
    public void deleteGoods(Long id) {
        seckillGoodsMapper.deleteById(id);
        // 清理 Redis 库存
        stringRedisTemplate.delete(RedisKeyConstant.seckillStock(id));
        stringRedisTemplate.delete(RedisKeyConstant.seckillUsers(id));
        log.info("【秒杀管理】删除商品：id={}", id);
    }

//    @Override
//    public void changeStatus(Long id, Integer status) {
//        seckillGoodsMapper.updateStatus(id, status);
//        // 如果开始活动（status=1），预热库存
//        if (status != null && status == 1) {
//            warmUp(id);
//        }
//        log.info("【秒杀管理】修改状态：id={}, status={}", id, status);
//    }

    @Override
    public com.sky.result.PageResult pageQueryOrders(Long userId, Long goodsId, Integer status,
                                                     int page, int pageSize) {
        com.github.pagehelper.PageHelper.startPage(page, pageSize);
        com.github.pagehelper.Page<SeckillOrder> p = seckillOrderMapper.pageQuery(userId, goodsId, status);
        return new com.sky.result.PageResult(p.getTotal(), p.getResult());
    }






}