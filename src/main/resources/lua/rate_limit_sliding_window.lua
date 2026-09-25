-- ===================================================================
-- 基于 Redis ZSET 的滑动窗口限流 Lua 脚本 (P1-6)
-- KEYS[1]: 限流键 (例如 rate_limit:seckill:exchange 或 rate_limit:user:101)
-- ARGV[1]: 窗口时间大小 (毫秒, 如 1000ms)
-- ARGV[2]: 窗口内最大允许请求数 (如 100)
-- ARGV[3]: 当前时间戳 (毫秒)
-- ARGV[4]: 唯一请求标识/随机盐 (防 ZSET 成员覆盖)
-- 返回值:
--   1: 允许通行
--   0: 达到限流阈值，触发拦截
-- ===================================================================
local limitKey = KEYS[1]
local windowMs = tonumber(ARGV[1])
local maxCount = tonumber(ARGV[2])
local nowMs = tonumber(ARGV[3])
local memberId = ARGV[4] or tostring(nowMs)

-- 1. 移除窗口外的历史请求记录
local windowStart = nowMs - windowMs
redis.call('ZREMRANGEBYSCORE', limitKey, '-inf', windowStart)

-- 2. 统计当前窗口内存活的请求数量
local currentCount = redis.call('ZCARD', limitKey)

if currentCount < maxCount then
    -- 3. 记录当前请求并刷新过期时间 (窗口大小+1秒以防内存泄漏)
    redis.call('ZADD', limitKey, nowMs, memberId)
    redis.call('PEXPIRE', limitKey, windowMs + 1000)
    return 1
else
    return 0
end
