-- ===================================================================
-- 礼品库存原子预扣 Lua 脚本 (P1-1)
-- KEYS[1]: gift:stock:{giftId}
-- ARGV[1]: quantity (扣减数量)
-- 返回值:
--   1: 扣减成功
--   0: 库存不足，快速拦截
--  -1: 库存未预热 (Key 不存在)
-- ===================================================================
local stockKey = KEYS[1]
local quantity = tonumber(ARGV[1])

local currentStock = redis.call('GET', stockKey)
if currentStock == false or currentStock == nil then
    return -1
end

local stockNum = tonumber(currentStock)
if stockNum < quantity then
    return 0
end

redis.call('DECRBY', stockKey, quantity)
return 1
