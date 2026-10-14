-- ============================================
-- 滑动窗口限流脚本
-- ============================================
-- KEYS[1] = 限流 key
-- ARGV[1] = 窗口大小（毫秒）
-- ARGV[2] = 限流阈值
-- ARGV[3] = 当前时间戳（毫秒）
-- ARGV[4] = 唯一请求 ID
-- 返回：1 放行，0 限流
-- ============================================

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requestId = ARGV[4]

-- 1. 删除窗口外的旧请求
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 2. 统计窗口内请求数
local count = redis.call('ZCARD', key)

-- 3. 判断是否超限----
-- 先去用ZCARD去查看当前窗口的请求数量，如果不到limit --> 就放进来 用ZADD -- 并且这时候 ZCARD也会同步改变（+1）
-- 如果到了limit --- 就把这条请求给禁掉！！！
if count >= limit then
    return 0
end

-- 4. 记录本次请求（score = 时间戳，member = 唯一 ID）
-- ZSet（有序集合）结构：`member(成员) -> score(分数)`
--- `key`：ZSet 的键（你的滑动窗口限流 key）
--- `now`：**score = 当前请求的毫秒时间戳**
--- `requestId`：**member = 本次请求唯一 ID**

redis.call('ZADD', key, now, requestId)

-- 5. 设置过期时间（防止冷 key 堆积）
redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)

return 1