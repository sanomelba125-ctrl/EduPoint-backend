-- ===================================================================
-- 库存一次性反向补偿 Lua 脚本 (P0-4)
-- KEYS[1]: gift:stock:{giftId}
-- KEYS[2]: gift:stock:compensated:{messageId}
-- ARGV[1]: quantity (本次回补件数)
-- ARGV[2]: dedupe ttl seconds
-- 返回值:
--   1: 本次完成回补
--   0: 该 messageId 已回补过，幂等跳过
--  -1: 库存 key 不存在，不凭空创建库存（交由对账/预热处理）
-- ===================================================================
local stockKey = KEYS[1]
local dedupeKey = KEYS[2]
local quantity = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

if redis.call('EXISTS', dedupeKey) == 1 then
    return 0
end

local currentStock = redis.call('GET', stockKey)
if currentStock == false or currentStock == nil then
    return -1
end

redis.call('INCRBY', stockKey, quantity)
redis.call('SET', dedupeKey, '1', 'EX', ttl)
return 1
