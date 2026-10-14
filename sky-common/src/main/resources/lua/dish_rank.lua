-- ============================================
-- 菜品销量累加 + 同分排序
-- ============================================
-- KEYS[1] = ZSet 排行榜 key (rank:dish:sales)
-- KEYS[2] = Hash 首次达成时间 key (rank:dish:first_time)
-- ARGV[1] = dishId
-- ARGV[2] = 增量（通常是 1）
-- ARGV[3] = 当前时间戳（毫秒）
-- 返回：新销量
-- ============================================
local rankKey = KEYS[1]
local timeKey = KEYS[2]
local dishId = ARGV[1]
local delta = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

--获取，记录首次达成的时间
local firstTime = redis.call('HGET',timeKey,dishId)

if not firstTime then
    firstTime = now
    redis.call('HSET',timeKey,dishId,firstTime)
end
firstTime = tonumber(firstTime)

--获取当前的 score 提取整数部分作为销量
local currentScore = redis.call('ZSCORE',rankKey,dishId)
local sales = 0
if currentScore then
    sales = math.floor(tonumber(currentScore))
end

-- 3. 销量累加
sales = sales + delta

-- 4. 计算新 score：销量（整数位）+ 时间戳微调（小数位）
-- 越早首次达成，微调值越大 → 同分时越靠前
local timeAdjust = 1 - firstTime / 1e13
local newScore = sales + timeAdjust

-- 5. 更新 ZSet
redis.call('ZADD', rankKey, newScore, dishId)

return sales
