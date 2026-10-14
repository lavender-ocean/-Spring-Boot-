-- ============================================
-- 秒杀核心脚本（防超卖 + 防重复购买）
-- ============================================
-- KEYS[1] = 库存 key: seckill:stock:{goodsId} --- 去做判断库存是否为空
-- KEYS[2] = 用户 Set key: seckill:users:{goodsId} --- 去做防幂等
-- ARGV[1] = userId
-- 返回：
--    1  = 抢购成功（有资格进入下单流程）
--    0  = 已售罄
--   -1  = 重复购买
--   -2  = 活动未开始/已结束
-- ============================================

local stockKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]




-- 1. 判重（用户是否已购买）
-- 用户 Set key: seckill:users:{goodsId} 带了goodsId
-- 天然适配 因为一个用户可以参与多个秒杀-拿到多个goodsId

if redis.call('SISMEMBER', userKey , userId) == 1 then
    return -1

end


-- 2. 判库存

local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock <= 0 then
    return 0

end

-- 卖了商品之后， stock-1 并且 记录购买的用户
-- 3. 原子扣减 + 记录用户
redis.call('DECR', stockKey)
-- 防幂等记录

-- 为什么是userKey + userID呢？ --因为一个用户可以参与多个秒杀！ 就是说一个userId 可以去对应多个 stock！
redis.call('SADD', userKey, userId)


-- 首次创建已购集合时补上过期时间（与库存 key 对齐，7 天）
if redis.call('TTL', KEYS[2]) < 0 then
    redis.call('EXPIRE', KEYS[2], 604800)
end



return 1