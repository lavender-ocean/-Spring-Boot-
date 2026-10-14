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

-- 3. 判断是否超限
if count >= limit then
    return 0
end

-- 4. 记录本次请求（score = 时间戳，member = 唯一 ID）
redis.call('ZADD', key, now, requestId)

-- 5. 设置过期时间（防止冷 key 堆积）
redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)

return 1